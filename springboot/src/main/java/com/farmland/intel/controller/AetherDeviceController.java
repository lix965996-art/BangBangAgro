package com.farmland.intel.controller;

import com.farmland.intel.config.interceptor.AuthAccess;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.SensorReading;
import com.farmland.intel.mapper.SensorReadingMapper;
import com.farmland.intel.service.IOneNetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Aether设备控制器（集成STM32硬件 + OneNET平台）
 * 提供设备状态查询、LED控制、历史数据查询等功能
 */
@RestController
@RequestMapping("/aether")
public class AetherDeviceController {

    private static final Logger log = LoggerFactory.getLogger(AetherDeviceController.class);

    @Autowired
    private SensorReadingMapper sensorReadingMapper;

    @Autowired(required = false) // 如果OneNET服务不可用，不阻塞启动
    private IOneNetService oneNetService;

    // 设备状态缓存（volatile 保证可见性，synchronized 保证复合操作原子性）
    private static final Object stateLock = new Object();
    private static volatile Double currentTemperature = 25.5;
    private static volatile Double currentHumidity = 60.0;
    private static volatile Integer currentLed = 0;
    private static volatile boolean currentFan = false;
    private static volatile boolean currentPump = false;
    private static volatile boolean deviceOnline = true;
    
    /**
     * 测试OneNET连通性（免登录）
     */
    @GetMapping("/device/test")
    @AuthAccess
    public Result testOneNet() {
        if (oneNetService == null) {
            return Result.error("500", "OneNET服务未启用");
        }
        try {
            Map<String, Object> data = oneNetService.getDeviceData();
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("500", "OneNET连接失败: " + e.getMessage());
        }
    }

    /**
     * 获取设备状态（优先从OneNET获取真实数据，降级到数据库或模拟数据）
     */
    @GetMapping("/device/status")
    public Result getDeviceStatus() {
        Map<String, Object> data = new HashMap<>();
        
        // 优先级1：尝试从OneNET获取实时数据
        if (oneNetService != null) {
            try {
                Map<String, Object> oneNetData = oneNetService.getDeviceData();
                if ((Boolean) oneNetData.getOrDefault("success", false)) {
                    data = oneNetData;
                    data.put("device_name", "STM32-001");
                    data.put("source", "OneNET"); // 标记数据来源
                    
                    // 更新缓存（同步保证多字段原子更新）
                    synchronized (stateLock) {
                        currentTemperature = (Double) data.get("temperature");
                        currentHumidity = (Double) data.get("humidity");
                        currentLed = (Integer) data.get("led");
                        deviceOnline = (Boolean) data.get("online");
                        syncPumpStateFromOneNet();
                    }
                    data.put("fan", currentFan);
                    data.put("pump", currentPump);
                    
                    // 【新增】实时存储到数据库
                    try {
                        SensorReading reading = new SensorReading();
                        reading.setTemperature(currentTemperature);
                        reading.setHumidity(currentHumidity);
                        reading.setLed(currentLed);
                        reading.setDeviceName("STM32-001");
                        reading.setCreatedAt(LocalDateTime.now());
                        sensorReadingMapper.insert(reading);
                        log.info("[API请求] 实时数据已存入数据库: T={}℃, H={}%", currentTemperature, currentHumidity);
                    } catch (Exception e) {
                        log.error("[API请求] 实时数据存库失败", e);
                    }
                    
                    return Result.success(data);
                }
            } catch (Exception e) {
                log.warn("从OneNET获取数据失败: {}", e.getMessage());
            }
        }
        
        // 优先级2：从数据库获取最新数据
        try {
            SensorReading latest = sensorReadingMapper.selectLatest();
            if (latest != null) {
                synchronized (stateLock) {
                    currentTemperature = latest.getTemperature();
                    currentHumidity = latest.getHumidity();
                    currentLed = latest.getLed();
                    deviceOnline = true;
                }
                
                data.put("success", true);
                data.put("online", true);
                data.put("temperature", currentTemperature);
                data.put("humidity", currentHumidity);
                data.put("led", currentLed);
                data.put("fan", currentFan);
                data.put("pump", currentPump);
                data.put("device_name", latest.getDeviceName());
                data.put("source", "Database"); // 标记数据来源
                
                log.debug("从数据库获取设备状态成功");
                return Result.success(data);
            }
        } catch (Exception e) {
            log.warn("从数据库获取数据失败: {}", e.getMessage());
        }
        
        // 优先级3：使用缓存的数据或模拟数据
        data.put("success", true);
        data.put("online", deviceOnline);
        data.put("temperature", currentTemperature);
        data.put("humidity", currentHumidity);
        data.put("led", currentLed);
        data.put("fan", currentFan);
        data.put("pump", currentPump);
        data.put("device_name", "STM32-001");
        data.put("source", "Cache"); // 标记数据来源
        
        return Result.success(data);
    }
    
    /**
     * 控制LED灯（同时控制OneNET硬件和数据库）
     */
    @PostMapping("/device/control/led")
    public Result controlLed(@RequestBody Map<String, Object> params) {
        // 参数验证
        if (params == null || params.isEmpty()) {
            return Result.error("400", "请求参数不能为空");
        }
        
        Integer led = (Integer) params.get("led");
        if (led == null || (led != 0 && led != 1)) {
            return Result.error("400", "LED参数必须为0（关闭）或1（开启）");
        }
        
        boolean ledState = led == 1;
        boolean oneNetSuccess = false;
        
        // 优先控制OneNET硬件
        if (oneNetService != null) {
            try {
                oneNetSuccess = oneNetService.controlLed(ledState);
                if (oneNetSuccess) {
                    log.info("OneNET LED控制成功: {}", ledState ? "开启" : "关闭");
                }
            } catch (Exception e) {
                log.error("OneNET LED控制失败", e);
            }
        }
        
        // 更新缓存
        synchronized (stateLock) {
            currentLed = led;
        }
        
        // 保存到数据库
        try {
            SensorReading reading = new SensorReading();
            reading.setTemperature(currentTemperature);
            reading.setHumidity(currentHumidity);
            reading.setLed(led);
            reading.setDeviceName("STM32-001");
            reading.setCreatedAt(LocalDateTime.now());
            sensorReadingMapper.insert(reading);
            log.debug("LED状态已保存到数据库");
        } catch (Exception e) {
            log.error("保存LED状态失败", e);
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("led", led);
        data.put("message", led == 1 ? "LED已开启" : "LED已关闭");
        data.put("oneNetControlled", oneNetSuccess); // 标记是否成功控制硬件
        
        return Result.success(data);
    }

    /**
     * 控制风扇开关（当前以本地状态为主，便于展示完整联动链路）
     */
    @PostMapping("/device/control/fan")
    public Result controlFan(@RequestBody Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Result.error("400", "请求参数不能为空");
        }

        Boolean fan = null;
        Object fanObj = params.get("fan");
        if (fanObj instanceof Boolean) {
            fan = (Boolean) fanObj;
        } else if (fanObj instanceof Integer) {
            fan = ((Integer) fanObj) == 1;
        } else if (fanObj instanceof String) {
            fan = "true".equalsIgnoreCase((String) fanObj) || "1".equals(fanObj);
        }

        if (fan == null) {
            return Result.error("400", "fan参数必须为布尔值或0/1");
        }

        currentFan = fan;

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("fan", currentFan);
        data.put("message", currentFan ? "风扇已开启" : "风扇已关闭");
        data.put("oneNetControlled", false);
        return Result.success(data);
    }
    
    /**
     * 获取历史数据（最近N天）
     */
    @GetMapping("/readings/detail")
    public Result getReadingsDetail(@RequestParam(defaultValue = "30") Integer days) {
        // 参数验证
        if (days == null || days < 1 || days > 365) {
            return Result.error("400", "天数参数必须在1-365之间");
        }
        
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusDays(days);
            
            List<SensorReading> readings = sensorReadingMapper.selectByTimeRange(startTime, endTime);
            
            List<Map<String, Object>> data = new ArrayList<>();
            for (SensorReading reading : readings) {
                Map<String, Object> item = new HashMap<>();
                item.put("date", reading.getCreatedAt().toString());
                item.put("temp", reading.getTemperature());
                item.put("humi", reading.getHumidity());
                data.add(item);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            result.put("days", days);
            result.put("count", data.size());
            
            log.info("获取{}天历史数据成功，共{}条记录", days, data.size());
            return Result.success(result);
        } catch (Exception e) {
            log.error("从数据库获取历史数据失败: {}", e.getMessage(), e);
            return Result.error("500", "数据库查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成模拟历史数据
     */
    private List<Map<String, Object>> generateMockHistoryData(int days) {
        List<Map<String, Object>> data = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        Random random = new Random();
        
        // 每天生成24个数据点（每小时一个）
        for (int i = days * 24; i >= 0; i--) {
            Map<String, Object> item = new HashMap<>();
            LocalDateTime time = now.minusHours(i);
            item.put("date", time.toString());
            item.put("temp", 20 + random.nextDouble() * 10); // 20-30度
            item.put("humi", 50 + random.nextDouble() * 30); // 50-80%
            data.add(item);
        }
        
        return data;
    }
    
    /**
     * 控制水泵开关（新设备 KK57iNOm8d）
     */
    @PostMapping("/device/control/bump")
    public Result controlBump(@RequestBody Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Result.error("400", "请求参数不能为空");
        }
        
        Boolean bump = null;
        Object bumpObj = params.get("bump");
        if (bumpObj instanceof Boolean) {
            bump = (Boolean) bumpObj;
        } else if (bumpObj instanceof Integer) {
            bump = ((Integer) bumpObj) == 1;
        } else if (bumpObj instanceof String) {
            bump = "true".equalsIgnoreCase((String) bumpObj) || "1".equals(bumpObj);
        }
        
        if (bump == null) {
            return Result.error("400", "bump参数必须为布尔值或0/1");
        }
        
        boolean oneNetSuccess = false;
        currentPump = bump;
        
        // 控制OneNET硬件
        if (oneNetService != null) {
            try {
                oneNetSuccess = oneNetService.controlBump(bump);
                if (oneNetSuccess) {
                    log.info("OneNET 水泵控制成功: {}", bump ? "开启" : "关闭");
                }
            } catch (Exception e) {
                log.error("OneNET 水泵控制失败", e);
            }
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("bump", bump);
        data.put("message", bump ? "水泵已开启" : "水泵已关闭");
        data.put("oneNetControlled", oneNetSuccess);
        
        return Result.success(data);
    }
    
    /**
     * 获取新设备状态（水泵设备）
     */
    @GetMapping("/device/new/status")
    public Result getNewDeviceStatus() {
        Map<String, Object> data = new HashMap<>();
        
        if (oneNetService != null) {
            try {
                Map<String, Object> oneNetData = oneNetService.getNewDeviceData();
                if ((Boolean) oneNetData.getOrDefault("success", false)) {
                    data = oneNetData;
                    Object bumpState = data.get("bump");
                    if (bumpState instanceof Boolean) {
                        currentPump = (Boolean) bumpState;
                    }
                    data.put("device_name", "STM32-PUMP");
                    data.put("source", "OneNET");
                    return Result.success(data);
                }
            } catch (Exception e) {
                log.warn("从OneNET获取新设备数据失败: {}", e.getMessage());
            }
        }
        
        // 返回默认数据
        data.put("success", true);
        data.put("online", false);
        data.put("temperature", 25.0);
        data.put("humidity", 50.0);
        data.put("bump", currentPump);
        data.put("device_name", "STM32-PUMP");
        data.put("source", "Default");
        
        return Result.success(data);
    }
    
    /**
     * 模拟数据更新（定时任务可以调用）
     */
    @PostMapping("/device/simulate")
    public Result simulateData() {
        Random random = new Random();
        double temp, humi;
        synchronized (stateLock) {
            currentTemperature = 20 + random.nextDouble() * 10;
            currentHumidity = 50 + random.nextDouble() * 30;
            temp = currentTemperature;
            humi = currentHumidity;
        }

        // 保存到数据库
        try {
            SensorReading reading = new SensorReading();
            reading.setTemperature(temp);
            reading.setHumidity(humi);
            reading.setLed(currentLed);
            reading.setDeviceName("STM32-001");
            reading.setCreatedAt(LocalDateTime.now());
            sensorReadingMapper.insert(reading);
        } catch (Exception e) {
            log.error("保存模拟数据失败", e);
        }
        
        log.info("模拟数据生成成功: 温度={}℃, 湿度={}%", temp, humi);
        return Result.success("模拟数据已生成");
    }

    private void syncPumpStateFromOneNet() {
        if (oneNetService == null) {
            return;
        }
        try {
            Map<String, Object> pumpData = oneNetService.getNewDeviceData();
            if ((Boolean) pumpData.getOrDefault("success", false)) {
                Object bumpState = pumpData.get("bump");
                if (bumpState instanceof Boolean) {
                    currentPump = (Boolean) bumpState;
                } else if (bumpState instanceof Integer) {
                    currentPump = ((Integer) bumpState) == 1;
                } else if (bumpState instanceof String) {
                    currentPump = "true".equalsIgnoreCase((String) bumpState) || "1".equals(bumpState);
                }
            }
        } catch (Exception e) {
            log.debug("同步水泵状态失败: {}", e.getMessage());
        }
    }
}


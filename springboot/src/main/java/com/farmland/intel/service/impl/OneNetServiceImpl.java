package com.farmland.intel.service.impl;

import com.farmland.intel.entity.SensorReading;
import com.farmland.intel.mapper.SensorReadingMapper;
import com.farmland.intel.service.IOneNetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/**
 * OneNET物联网平台服务实现
 * 提供STM32设备数据获取和控制功能
 */
@Service
@ConditionalOnProperty(prefix = "onenet", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OneNetServiceImpl implements IOneNetService {
    
    private static final Logger log = LoggerFactory.getLogger(OneNetServiceImpl.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
        new ParameterizedTypeReference<Map<String, Object>>() {};
    
    @Autowired
    private SensorReadingMapper sensorReadingMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Value("${onenet.api-url:https://iot-api.heclouds.com}")
    private String apiUrl;
    
    @Value("${onenet.author-key:}")
    private String authorKey;

    @Value("${onenet.user-id:}")
    private String userId;

    @Value("${onenet.version:2022-05-01}")
    private String version;

    @Value("${onenet.product-id:}")
    private String productId;

    @Value("${onenet.device-name:}")
    private String deviceName;

    // 新设备配置（水泵控制设备）
    @Value("${onenet.new-product-id:}")
    private String newProductId;

    @Value("${onenet.new-device-name:}")
    private String newDeviceName;
    
    /**
     * 生成OneNET认证Token（根据key.js实现）
     * 
     * 参考代码：
     * const access_key = Buffer.from(params.author_key, "base64")
     * const key = et + "\n" + method + "\n" + res + "\n" + version
     * let sign = crypto.createHmac('sha1', access_key).update(key).digest().toString('base64')
     */
    private String generateToken() {
        try {
            // 1. 计算过期时间（1年后，完全按照key.js）
            long et = (System.currentTimeMillis() + 365L * 24 * 3600 * 1000) / 1000;
            String res = "userid/" + userId;
            String method = "sha1";  // 关键：使用SHA1，不是SHA256！
            
            // 2. 构建待签名字符串（完全按照key.js的格式：et + "\n" + method + "\n" + res + "\n" + version）
            String key = et + "\n" + method + "\n" + res + "\n" + version;
            
            log.debug("待签名字符串: {}", key);
            
            // 3. 关键：将author_key从Base64解码后作为密钥（与key.js一致）
            // JavaScript: Buffer.from(params.author_key, "base64")
            byte[] accessKey = Base64.getDecoder().decode(authorKey);
            log.debug("密钥解码成功，长度: {} bytes", accessKey.length);
            
            // 4. 使用HmacSHA1签名（与key.js的crypto.createHmac('sha1', access_key)一致）
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secretKey = new SecretKeySpec(accessKey, "HmacSHA1");
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(key.getBytes(StandardCharsets.UTF_8));
            String sign = Base64.getEncoder().encodeToString(hash);
            
            log.debug("签名生成成功");
            
            // 5. URL编码（与key.js的encodeURIComponent一致）
            String encodedRes = java.net.URLEncoder.encode(res, "UTF-8");
            String encodedSign = java.net.URLEncoder.encode(sign, "UTF-8");
            
            // 6. 拼接Token（完全按照key.js的格式）
            String token = "version=" + version 
                         + "&res=" + encodedRes 
                         + "&et=" + et 
                         + "&method=" + method 
                         + "&sign=" + encodedSign;
            
            log.debug("Token生成成功");
            
            return token;
        } catch (Exception e) {
            log.error("生成Token失败", e);
            return "";
        }
    }

    /** 与 OneNET 物模型标识符对齐：temp、temperature（大小写不敏感） */
    private static boolean isTempIdentifier(String id) {
        if (id == null) return false;
        String s = id.trim().toLowerCase(Locale.ROOT);
        return "temp".equals(s) || "temperature".equals(s) || "air_temperature".equals(s);
    }

    /** humi、humidity（大小写不敏感） */
    private static boolean isHumiIdentifier(String id) {
        if (id == null) return false;
        String s = id.trim().toLowerCase(Locale.ROOT);
        return "humi".equals(s) || "humidity".equals(s) || "air_humidity".equals(s);
    }

    /** soil、soilhumidity、soil_humidity（大小写不敏感） */
    private static boolean isSoilIdentifier(String id) {
        if (id == null) return false;
        String s = id.trim().toLowerCase(Locale.ROOT);
        return "soil".equals(s) || "soilhumidity".equals(s) || "soil_humidity".equals(s);
    }

    /** 把 OneNET 返回的布尔值（可能是 Boolean / "true" / 1）统一解析为 boolean */
    private static boolean parseBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    @Override
    public Map<String, Object> getDeviceData() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        
        try {
            String url = apiUrl + "/thingmodel/query-device-property?product_id=" + productId + "&device_name=" + deviceName;
            String token = generateToken();
            
            log.debug("正在调用OneNET API: {}", url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = exchangeForMap(url, HttpMethod.GET, entity);
            
            log.debug("OneNET响应状态: {}", response.getStatusCode());
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                // 打印完整响应，方便调试
                log.warn("[OneNET调试] 完整响应: {}", body);

                // 检查是否有错误码
                Object code = body.get("code");
                if (code != null && !code.toString().equals("0")) {
                    log.error("OneNET返回错误码: {}, 错误信息: {}", code, body.get("msg"));
                    result.put("error", "OneNET返回错误: " + body.get("msg"));
                    return result;
                }

                Object dataObj = body.get("data");

                if (dataObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Map<String, Object>> dataList = (java.util.List<Map<String, Object>>) dataObj;
                    log.warn("[OneNET调试] 数据列表长度: {}", dataList.size());

                    // 按标识符匹配数据，不依赖顺序
                    Map<String, Object> tempItem = null, humiItem = null, ledItem = null,
                                        soilItem = null, fanItem = null, buzzerItem = null;
                    for (Map<String, Object> item : dataList) {
                        if (item == null) { log.warn("[OneNET 调试] 跳过 null 数据项"); continue; }
                        String id = (String) item.get("identifier");
                        if (id == null) { log.warn("[OneNET 调试] identifier 为 null，跳过"); continue; }
                        log.warn("[OneNET调试] identifier={}, value={}", id, item.get("value"));
                        if (isTempIdentifier(id)) tempItem = item;
                        else if (isHumiIdentifier(id)) humiItem = item;
                        else if (isSoilIdentifier(id)) soilItem = item;
                        else if ("led".equalsIgnoreCase(id)) ledItem = item;
                        else if ("fan".equalsIgnoreCase(id)) fanItem = item;
                        else if ("buzzer".equalsIgnoreCase(id)) buzzerItem = item;
                    }

                    Object tempVal = tempItem != null ? tempItem.get("value") : null;
                    Object humiVal = humiItem != null ? humiItem.get("value") : null;
                    Object ledVal = ledItem != null ? ledItem.get("value") : null;
                    Object soilVal = soilItem != null ? soilItem.get("value") : null;
                    Object fanVal = fanItem != null ? fanItem.get("value") : null;
                    Object buzzerVal = buzzerItem != null ? buzzerItem.get("value") : null;

                    // 通过独立API查询设备真实在线状态
                    boolean online = queryDeviceOnlineStatus(productId, deviceName);

                    if (tempVal != null && humiVal != null) {
                        Double temperature = round(Double.parseDouble(tempVal.toString()), 1);
                        Double humidity = round(Double.parseDouble(humiVal.toString()), 1);
                        Integer ledState = 0;
                        if (ledVal != null) {
                            if (ledVal instanceof Boolean) ledState = ((Boolean) ledVal) ? 1 : 0;
                            else ledState = "true".equals(ledVal.toString()) ? 1 : 0;
                        }

                        result.put("success", true);
                        result.put("temperature", temperature);
                        result.put("humidity", humidity);
                        result.put("led", ledState);
                        result.put("online", online);

                        // 土壤湿度 / 风扇 / 蜂鸣器：仅在设备实际上报时写入，避免老固件被误判为 0
                        Integer soilPercent = null;
                        if (soilVal != null) {
                            soilPercent = (int) Math.round(Double.parseDouble(soilVal.toString()));
                            result.put("soil", soilPercent);
                        }
                        Boolean fanState = fanVal != null ? parseBool(fanVal) : null;
                        if (fanState != null) result.put("fan", fanState);
                        Boolean buzzerState = buzzerVal != null ? parseBool(buzzerVal) : null;
                        if (buzzerState != null) result.put("buzzer", buzzerState);

                        log.warn("[OneNET] 真实数据 - 温度: {}℃, 湿度: {}%, 土壤: {}%, LED: {}, 风扇: {}, 报警器: {}, 在线: {}",
                                temperature, humidity, soilPercent, ledState, fanState, buzzerState, online);
                    } else {
                        // 不再用随机数冒充实时值（会导致界面与真实传感器完全不符）
                        log.warn("[OneNET] 未解析到有效温湿度（请确认物模型属性标识符为 temp/humi 或 temperature/humidity，且设备已上报）。在线: {}", online);
                        result.put("success", false);
                        result.put("online", online);
                        result.put("error", "OneNET 未返回温湿度：检查物模型标识符是否与代码一致或设备是否上报数据");
                    }
                } else {
                    log.warn("data字段不是List类型: {}", (dataObj != null ? dataObj.getClass().getName() : "null"));
                    result.put("error", "数据格式错误");
                }
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("HTTP客户端错误: {} - {}, 响应内容: {}", e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString());
            result.put("success", false);
            result.put("online", false);
            result.put("error", "HTTP错误 " + e.getStatusCode() + ": " + e.getMessage());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("网络连接失败: {}", e.getMessage());
            result.put("success", false);
            result.put("online", false);
            result.put("error", "网络连接失败，无法访问OneNET平台");
        } catch (Exception e) {
            log.error("从OneNET获取数据失败", e);
            result.put("success", false);
            result.put("online", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public boolean controlLed(boolean led) {
        try {
            String url = apiUrl + "/thingmodel/set-device-property";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authorization", generateToken());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("product_id", productId);
            requestBody.put("device_name", deviceName);
            
            Map<String, Object> params = new HashMap<>();
            params.put("led", led);
            requestBody.put("params", params);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map<String, Object>> response = exchangeForMap(url, HttpMethod.POST, entity);
            
            boolean success = response.getStatusCode() == HttpStatus.OK;
            if (success) {
                log.debug("OneNET LED控制成功: {}", led ? "开启" : "关闭");
            } else {
                log.warn("OneNET LED控制失败，状态码: {}", response.getStatusCode());
            }
            return success;
        } catch (Exception e) {
            log.error("控制LED失败", e);
            return false;
        }
    }
    
    @Override
    public boolean syncDataToDatabase() {
        try {
            Map<String, Object> data = getDeviceData();
            
            if ((Boolean) data.getOrDefault("success", false)) {
                SensorReading reading = new SensorReading();
                reading.setTemperature((Double) data.get("temperature"));
                reading.setHumidity((Double) data.get("humidity"));
                reading.setLed((Integer) data.get("led"));
                Object soil = data.get("soil");
                if (soil != null) reading.setSoilHumidity((int) Math.round(Double.parseDouble(soil.toString())));
                if (data.get("fan") != null) reading.setFan(parseBool(data.get("fan")) ? 1 : 0);
                if (data.get("buzzer") != null) reading.setBuzzer(parseBool(data.get("buzzer")) ? 1 : 0);
                reading.setDeviceName("STM32-001");
                reading.setCreatedAt(LocalDateTime.now());

                sensorReadingMapper.insert(reading);

                log.debug("[定时任务] STM32数据已同步到数据库: T={}℃, H={}%, Soil={}%", reading.getTemperature(), reading.getHumidity(), reading.getSoilHumidity());
                return true;
            } else {
                log.warn("[定时任务] 获取设备数据失败，无法同步");
            }
        } catch (Exception e) {
            log.error("[定时任务] 同步数据到数据库异常", e);
        }
        
        return false;
    }

@Override
public boolean controlPump(boolean pump) {
    try {
        String url = apiUrl + "/thingmodel/set-device-property";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", generateToken());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("product_id", newProductId);  // 使用新设备的product_id
        requestBody.put("device_name", newDeviceName);

        Map<String, Object> params = new HashMap<>();
        params.put("bump", pump);  // 水泵控制参数（OneNET硬件协议保留bump字段名）
        requestBody.put("params", params);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.debug("正在控制水泵: {} -> {}", newProductId, pump ? "开启" : "关闭");

        ResponseEntity<Map<String, Object>> response = exchangeForMap(url, HttpMethod.POST, entity);

        boolean success = response.getStatusCode() == HttpStatus.OK;
        if (success) {
            log.debug("OneNET 水泵控制成功: {}", pump ? "开启" : "关闭");
        } else {
            log.warn("OneNET 水泵控制失败，状态码: {}", response.getStatusCode());
        }
        return success;
    } catch (Exception e) {
        log.error("控制水泵失败", e);
        return false;
    }
}

@Override
public Map<String, Object> getNewDeviceData() {
    Map<String, Object> result = new HashMap<>();
    result.put("success", false);
    
    try {
        String url = apiUrl + "/thingmodel/query-device-property?product_id=" + newProductId + "&device_name=" + newDeviceName;
        String token = generateToken();
        
        log.debug("正在获取新设备数据: {}", url);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("authorization", token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map<String, Object>> response = exchangeForMap(url, HttpMethod.GET, entity);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            Object dataObj = body.get("data");
            
            // 通过独立API查询设备真实在线状态
            boolean online = queryDeviceOnlineStatus(newProductId, newDeviceName);

            if (dataObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> dataList = (java.util.List<Map<String, Object>>) dataObj;

                // 根据你提供的代码：data[0]=bump, data[1]=humi, data[2]=led, data[3]=temp
                if (dataList.size() >= 4) {
                    Double temperature = round(Double.parseDouble(dataList.get(3).get("value").toString()), 1);
                    Double humidity = round(Double.parseDouble(dataList.get(1).get("value").toString()), 1);
                    boolean pumpState = "true".equals(dataList.get(0).get("value").toString());
                    boolean ledState = "true".equals(dataList.get(2).get("value").toString());

                    log.debug("新设备数据解析成功 - 温度: {}℃, 湿度: {}%, 水泵: {}, 在线: {}", temperature, humidity, pumpState ? "开" : "关", online);

                    result.put("success", true);
                    result.put("temperature", temperature);
                    result.put("humidity", humidity);
                    result.put("pump", pumpState);
                    result.put("led", ledState);
                    result.put("online", online);
                }
            }
        }
    } catch (Exception e) {
        log.error("获取新设备数据失败", e);
        result.put("error", e.getMessage());
    }
    
    return result;
    }
    
    @Override
    public boolean syncNewDeviceDataToDatabase() {
        try {
            Map<String, Object> data = getNewDeviceData();
            
            if ((Boolean) data.getOrDefault("success", false)) {
                SensorReading reading = new SensorReading();
                reading.setTemperature((Double) data.get("temperature"));
                reading.setHumidity((Double) data.get("humidity"));
                // 使用pump状态作为led字段（复用现有字段）
                reading.setLed((Boolean) data.get("pump") ? 1 : 0);
                reading.setDeviceName("STM32-PUMP");  // 新设备名称
                reading.setCreatedAt(LocalDateTime.now());

                sensorReadingMapper.insert(reading);

                log.debug("[定时任务] 新设备数据已同步到数据库: T={}℃, H={}%, 水泵={}",
                    reading.getTemperature(), reading.getHumidity(),
                    (Boolean) data.get("pump") ? "开" : "关");
                return true;
            } else {
                log.warn("[定时任务] 获取新设备数据失败，无法同步");
            }
        } catch (Exception e) {
            log.error("[定时任务] 同步新设备数据到数据库异常", e);
        }
        
        return false;
    }

    /**
     * 通过OneNET API查询设备真实的在线状态
     * 不再依赖属性值是否为null来推测在线状态
     */
    private boolean queryDeviceOnlineStatus(String pid, String devName) {
        try {
            String url = apiUrl + "/device/detail?product_id=" + pid + "&device_name=" + devName;
            String token = generateToken();

            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = exchangeForMap(url, HttpMethod.GET, entity);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                log.warn("[OneNET] device/detail 完整响应: {}", body);

                Object code = body.get("code");
                if (code != null && !code.toString().equals("0")) {
                    log.warn("[OneNET] 查询设备状态失败: code={}, msg={}", code, body.get("msg"));
                    return true; // API失败时默认在线，避免误判
                }
                Object dataObj = body.get("data");
                if (dataObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) dataObj;
                    Object status = data.get("status");
                    log.warn("[OneNET] status字段原始值: {}, 类型: {}", status, status != null ? status.getClass().getName() : "null");
                    if (status != null) {
                        boolean online = "1".equals(status.toString()) || Integer.valueOf(1).equals(status) || Boolean.TRUE.equals(status);
                        log.warn("[OneNET] 设备 {} 真实在线状态: {}", devName, online ? "在线" : "离线");
                        return online;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[OneNET] 查询设备在线状态异常: {}", e.getMessage());
            return true; // 异常时默认在线，避免误判
        }
        return true; // 兜底：无法确定时默认在线
    }

    /**
     * 对double值进行四舍五入，保留指定小数位
     */
    private double round(double value, int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces);
        return Math.round(value * scale) / scale;
    }

    private ResponseEntity<Map<String, Object>> exchangeForMap(String url, HttpMethod method, HttpEntity<?> entity) {
        return restTemplate.exchange(url, method, entity, MAP_RESPONSE_TYPE);
    }
}




package com.farmland.intel.controller;

import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletOutputStream;
import java.net.URLEncoder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IStatisticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import org.springframework.web.multipart.MultipartFile;
import com.farmland.intel.utils.TokenUtils;

import org.springframework.web.bind.annotation.RestController;

/**
 * 农田统计数据控制器
 * 提供农田数据的增删改查、导入导出、大屏展示等功能
 */
@RestController
@RequestMapping("/statistic")
public class StatisticController {

    private static final Logger log = LoggerFactory.getLogger(StatisticController.class);

    @Resource
    private IStatisticService statisticService;

    @Resource
    private com.farmland.intel.service.IHealthIndexService healthIndexService;

    /**
     * 新增或更新农田统计数据
     */
    @PostMapping
    public Result save(@RequestBody Statistic statistic) {
        // 参数验证
        if (statistic == null) {
            return Result.error("400", "统计数据不能为空");
        }
        
        log.info("📥 接收到保存请求 - 农田: {}, 区县: {}", statistic.getFarm(), statistic.getDistrict());
        
        // 数据验证：温度范围检查（仅在明确提交温度数据时验证）
        if (statistic.getTemperature() != null) {
            double temp = statistic.getTemperature().doubleValue();
            // 检查温度是否在合理范围内
            if (temp < -20 || temp > 50) {
                // 如果温度异常，自动修正为null，不阻止保存
                log.warn("温度数据异常，已自动清除: {}°C (农田: {})", temp, statistic.getFarm());
                statistic.setTemperature(null);
            }
        }
        
        // 数据验证：湿度范围检查（自动修正异常值）
        if (statistic.getAirhumidity() != null && (statistic.getAirhumidity() < 0 || statistic.getAirhumidity() > 100)) {
            log.warn("空气湿度数据异常，已自动清除: {}% (农田: {})", statistic.getAirhumidity(), statistic.getFarm());
            statistic.setAirhumidity(null);
        }
        
        if (statistic.getSoilhumidity() != null && (statistic.getSoilhumidity() < 0 || statistic.getSoilhumidity() > 100)) {
            log.warn("土壤湿度数据异常，已自动清除: {}% (农田: {})", statistic.getSoilhumidity(), statistic.getFarm());
            statistic.setSoilhumidity(null);
        }
        
        try {
            User currentUser = TokenUtils.getCurrentUser();
            if (currentUser == null) {
                return Result.error("401", "未登录");
            }
            if (statistic.getId() == null) {
                // 自动设置农田负责人
                statistic.setKeeper(currentUser.getUsername());
            } else {
                // 更新：校验归属，防越权修改他人农田数据
                Statistic existing = statisticService.getById(statistic.getId());
                if (existing == null) {
                    return Result.error("404", "记录不存在");
                }
                if (!"ROLE_ADMIN".equals(currentUser.getRole())
                        && !currentUser.getUsername().equals(existing.getKeeper())) {
                    return Result.error("403", "无权修改该记录");
                }
            }

            statisticService.saveOrUpdate(statistic);
            log.info("农田统计数据保存成功，ID: {}", statistic.getId());
            
            // 计算健康指数并生成预警（暂时禁用，避免错误）
            try {
                if (healthIndexService != null) {
                    Integer healthIndex = healthIndexService.calculateHealthIndex(statistic);
                    healthIndexService.checkAndGenerateAlerts(statistic);
                    log.debug("健康指数计算完成: {}", healthIndex);
                }
            } catch (Exception e) {
                log.warn("健康指数计算失败（不影响保存）", e);
            }
            
            return Result.success();
        } catch (Exception e) {
            log.error("保存农田统计数据失败", e);
            return Result.error("500", "保存失败，请重试");
        }
    }

    /**
     * 删除农田统计数据（非管理员只能删除自己负责的农田）
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
            Statistic entity = statisticService.getById(id);
            if (entity == null) {
                return Result.error("404", "记录不存在");
            }
            if (!currentUser.getUsername().equals(entity.getKeeper())) {
                return Result.error(Constants.CODE_401, "无权限删除该记录");
            }
        }
        statisticService.removeById(id);
        return Result.success();
    }

    /**
     * 批量删除农田统计数据（非管理员只能删除自己负责的农田）
     */
    @PostMapping("/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("400", "删除ID列表不能为空");
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
            List<Statistic> entities = (List<Statistic>) statisticService.listByIds(ids);
            for (Statistic entity : entities) {
                if (!currentUser.getUsername().equals(entity.getKeeper())) {
                    return Result.error(Constants.CODE_401, "无权限删除记录: " + entity.getId());
                }
            }
        }
        statisticService.removeByIds(ids);
        return Result.success();
    }

    @GetMapping
    public Result findAll() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        QueryWrapper<Statistic> queryWrapper = new QueryWrapper<>();
        // 非管理员只能查看自己负责的农田
        if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
            queryWrapper.eq("keeper", currentUser.getUsername());
        }
        return Result.success(statisticService.list(queryWrapper));
    }

    @GetMapping("/{id}")
    public Result findOne(@PathVariable Integer id) {
        Statistic entity = statisticService.getById(id);
        if (entity == null) {
            return Result.error("404", "记录不存在");
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        if (!"ROLE_ADMIN".equals(currentUser.getRole())
                && !currentUser.getUsername().equals(entity.getKeeper())) {
            return Result.error("403", "无权限查看该记录");
        }
        return Result.success(entity);
    }

    @GetMapping("/page")
    public Result findPage(@RequestParam(defaultValue = "") String farm,
                           @RequestParam Integer pageNum,
                           @RequestParam Integer pageSize) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        QueryWrapper<Statistic> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        if (!"".equals(farm)) {
            queryWrapper.like("farm", farm);
        }

        // 数据权限控制：非管理员只能看自己负责的农田
        if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
            queryWrapper.eq("keeper", currentUser.getUsername());
        }

        return Result.success(statisticService.page(new Page<>(pageNum, pageSize), queryWrapper));
    }

    /**
    * 导出接口
    */
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws Exception {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            response.setStatus(401);
            return;
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("Statistic信息表", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        ExcelWriter writer = null;

        try {
            writer = ExcelUtil.getWriter(true);
            writer.setOnlyAlias(true);
            writer.addHeaderAlias("farm", "农田名称");
            writer.addHeaderAlias("area", "面积");
            writer.addHeaderAlias("address", "地址");
            writer.addHeaderAlias("district", "所属区县");
            writer.addHeaderAlias("crop", "作物名称");
            writer.addHeaderAlias("number", "数量");
            writer.addHeaderAlias("state", "生长状态");
            writer.addHeaderAlias("temperature", "温度(℃)");
            writer.addHeaderAlias("airhumidity", "空气湿度(%)");
            writer.addHeaderAlias("soilhumidity", "土壤湿度(%)");
            writer.addHeaderAlias("carbon", "CO2(ppm)");
            writer.addHeaderAlias("ph", "PH值");
            writer.addHeaderAlias("light", "光照(lux)");
            writer.addHeaderAlias("filllight", "补光灯");
            writer.addHeaderAlias("monitor", "摄像头");
            writer.addHeaderAlias("pump", "水泵");
            writer.addHeaderAlias("keeper", "负责人");

            int pageSize = 1000;
            int pageNum = 1;
            Page<Statistic> page;
            boolean isFirstPage = true;

            // 数据权限控制：非管理员只能导出自己的
            QueryWrapper<Statistic> exportQw = new QueryWrapper<>();
            if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
                exportQw.eq("keeper", currentUser.getUsername());
            }

            do {
                page = statisticService.page(new Page<>(pageNum, pageSize), exportQw);
                List<Statistic> list = page.getRecords();

                if (CollUtil.isEmpty(list)) {
                    break;
                }

                writer.write(list, isFirstPage);
                isFirstPage = false;
                pageNum++;

            } while (page.hasNext());

            writer.flush(out, true);
        } finally {
            try { if (writer != null) writer.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * excel 导入
     * @param file
     * @throws Exception
     */
    @Transactional
    @PostMapping("/import")
    public Result imp(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            return Result.error("400", "仅支持 .xlsx 或 .xls 格式的Excel文件");
        }
        try (InputStream inputStream = file.getInputStream();
             ExcelReader reader = ExcelUtil.getReader(inputStream)) {
            List<Statistic> list = reader.readAll(Statistic.class);
            statisticService.saveBatch(list);
        }
        return Result.success();
    }

    /**
     * 大屏数据接口 - 获取农田信息用于大屏展示
     * 非管理员只能查看自己负责的农田
     */
    @GetMapping("/dashboard")
    public Result getDashboardData() {
        try {
            User currentUser = TokenUtils.getCurrentUser();
            if (currentUser == null) {
                return Result.success(new java.util.ArrayList<>());
            }
            QueryWrapper<Statistic> queryWrapper = new QueryWrapper<>();
            // 非管理员只能查看自己负责的农田
            if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
                queryWrapper.eq("keeper", currentUser.getUsername());
            }
            List<Statistic> list = statisticService.list(queryWrapper);
            return Result.success(list);
        } catch (Exception e) {
            log.error("获取Dashboard数据失败", e);
            // 返回空列表而不是500错误
            return Result.success(new java.util.ArrayList<>());
        }
    }

    /**
     * 大屏数据接口 - 获取统计汇总数据
     * 非管理员只能查看自己负责的农田统计
     */
    @GetMapping("/dashboard/summary")
    public Result getDashboardSummary() {
        try {
            User currentUser = TokenUtils.getCurrentUser();
            if (currentUser == null) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("totalArea", 0);
                empty.put("totalStock", 0);
                empty.put("farmCount", 0);
                empty.put("normalCount", 0);
                return Result.success(empty);
            }
            QueryWrapper<Statistic> queryWrapper = new QueryWrapper<>();
            // 非管理员只能查看自己负责的农田
            if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
                queryWrapper.eq("keeper", currentUser.getUsername());
            }
            List<Statistic> list = statisticService.list(queryWrapper);
            
            // 计算总面积（area 是 String 且可能含小数；用 Integer.parseInt 解析"12.5"会抛异常归0，导致统计近乎恒为0）
            double totalArea = list.stream()
                    .filter(s -> s.getArea() != null && !s.getArea().trim().isEmpty())
                    .mapToDouble(s -> {
                        try {
                            return Double.parseDouble(s.getArea().trim());
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .sum();
            totalArea = Math.round(totalArea * 10) / 10.0;  // 保留1位小数
            
            // 计算总数量（存栏）
            int totalStock = list.stream()
                    .filter(s -> s.getNumber() != null)
                    .mapToInt(Statistic::getNumber)
                    .sum();
            
            // 统计数据
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalArea", totalArea);
            summary.put("totalStock", totalStock);
            summary.put("farmCount", list.size());
            summary.put("normalCount", list.stream().filter(s -> "正常".equals(s.getState())).count());
            
            return Result.success(summary);
        } catch (Exception e) {
            log.error("获取Dashboard汇总数据失败", e);
            // 返回默认值
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalArea", 0);
            summary.put("totalStock", 0);
            summary.put("farmCount", 0);
            summary.put("normalCount", 0);
            return Result.success(summary);
        }
    }

    /**
     * 数据修正接口 - 修正异常的温度数据
     * 将超出正常范围的温度值修正为合理值
     */
    @PostMapping("/fix-temperature")
    public Result fixTemperatureData() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error("403", "仅管理员可执行数据修正");
        }
        try {
            List<Statistic> list = statisticService.list();
            int fixedCount = 0;
            
            for (Statistic statistic : list) {
                if (statistic.getTemperature() != null) {
                    double temp = statistic.getTemperature().doubleValue();
                    
                    // 检测异常温度：正常农业环境温度范围 -20°C ~ 50°C
                    if (temp < -20 || temp > 50) {
                        // 如果温度异常，修正为合理的默认值
                        // 可以根据实际情况设置，这里设置为 25°C（适宜温度）
                        statistic.setTemperature(new java.math.BigDecimal("25.0"));
                        statisticService.updateById(statistic);
                        fixedCount++;
                        log.info("修正异常温度数据 - ID: {}, 农田: {}, 原温度: {}°C -> 修正为: 25.0°C", 
                                statistic.getId(), statistic.getFarm(), temp);
                    }
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", list.size());
            result.put("fixedCount", fixedCount);
            result.put("message", "温度数据修正完成，共修正 " + fixedCount + " 条异常数据");
            
            log.info("温度数据修正完成，总数: {}, 修正数: {}", list.size(), fixedCount);
            return Result.success(result);
        } catch (Exception e) {
            log.error("修正温度数据失败", e);
            return Result.error("500", "修正失败: " + e.getMessage());
        }
    }

    /**
     * 数据修正接口 - 自动填充老数据的区县字段
     * 根据地址信息自动识别并填充district字段
     */
    @PostMapping("/fix-district")
    public Result fixDistrictData() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error("403", "仅管理员可执行数据修正");
        }
        try {
            List<Statistic> list = statisticService.list();
            int fixedCount = 0;
            
            for (Statistic statistic : list) {
                // 只处理district字段为空或为"其他区域"的记录
                if ((statistic.getDistrict() == null || 
                     statistic.getDistrict().trim().isEmpty() || 
                     "其他区域".equals(statistic.getDistrict())) && 
                    statistic.getAddress() != null && 
                    !statistic.getAddress().trim().isEmpty()) {
                    
                    String address = statistic.getAddress();
                    String district = extractDistrictFromAddress(address);
                    
                    if (district != null && !"其他区域".equals(district)) {
                        statistic.setDistrict(district);
                        statisticService.updateById(statistic);
                        fixedCount++;
                        log.info("自动填充区县数据 - ID: {}, 农田: {}, 地址: {} -> 区县: {}", 
                                statistic.getId(), statistic.getFarm(), address, district);
                    }
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", list.size());
            result.put("fixedCount", fixedCount);
            result.put("message", "区县数据修正完成，共填充 " + fixedCount + " 条记录");
            
            log.info("区县数据修正完成，总数: {}, 修正数: {}", list.size(), fixedCount);
            return Result.success(result);
        } catch (Exception e) {
            log.error("修正区县数据失败", e);
            return Result.error("500", "修正失败: " + e.getMessage());
        }
    }
    
    /**
     * 从地址中提取区县信息（参考Django版本的逻辑）
     */
    private String extractDistrictFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return "未知区县";  // 参考Django版本，使用"未知区县"而不是"其他区域"
        }
        
        address = address.trim();
        
        // 1. 对于学校、景点等POI，优先使用特殊映射（参考Django POI搜索逻辑）
        java.util.Map<String, String> poiMapping = new java.util.HashMap<>();
        poiMapping.put("张家界学院", "张家界市永定区");
        poiMapping.put("吉首大学张家界", "张家界市永定区");
        poiMapping.put("湖南中医药大学", "长沙市岳麓区");
        poiMapping.put("中南大学", "长沙市岳麓区");
        poiMapping.put("湖南大学", "长沙市岳麓区");
        poiMapping.put("湖南师范大学", "长沙市岳麓区");
        
        // 检查是否包含学校、景点等关键词
        if (address.contains("学院") || address.contains("大学") || address.contains("学校") || 
            address.contains("景点") || address.contains("公园") || address.contains("广场")) {
            for (java.util.Map.Entry<String, String> entry : poiMapping.entrySet()) {
                if (address.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        
        // 2. 优先匹配完整的"市+区县"格式
        java.util.regex.Pattern fullPattern = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]{2,4}市[\\u4e00-\\u9fa5]{2,3}[区县])");
        java.util.regex.Matcher fullMatcher = fullPattern.matcher(address);
        if (fullMatcher.find()) {
            return fullMatcher.group(1);
        }
        
        // 3. 组合匹配：市+区县
        java.util.regex.Pattern cityDistrictPattern = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]{2,4}市)([\\u4e00-\\u9fa5]{2,3}[区县])");
        java.util.regex.Matcher cityDistrictMatcher = cityDistrictPattern.matcher(address);
        if (cityDistrictMatcher.find()) {
            return cityDistrictMatcher.group(1) + cityDistrictMatcher.group(2);
        }
        
        // 4. 张家界市下辖区县映射（参考Django的get_districts接口）
        java.util.Map<String, String> zjjDistrictMapping = new java.util.HashMap<>();
        zjjDistrictMapping.put("永定区", "张家界市永定区");
        zjjDistrictMapping.put("武陵源区", "张家界市武陵源区");
        zjjDistrictMapping.put("慈利县", "张家界市慈利县");
        zjjDistrictMapping.put("桑植县", "张家界市桑植县");
        
        // 5. 长沙市区县映射
        java.util.Map<String, String> csDistrictMapping = new java.util.HashMap<>();
        csDistrictMapping.put("岳麓区", "长沙市岳麓区");
        csDistrictMapping.put("芙蓉区", "长沙市芙蓉区");
        csDistrictMapping.put("天心区", "长沙市天心区");
        csDistrictMapping.put("开福区", "长沙市开福区");
        csDistrictMapping.put("雨花区", "长沙市雨花区");
        csDistrictMapping.put("望城区", "长沙市望城区");
        
        // 先检查张家界区县
        for (java.util.Map.Entry<String, String> entry : zjjDistrictMapping.entrySet()) {
            if (address.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 再检查长沙区县
        for (java.util.Map.Entry<String, String> entry : csDistrictMapping.entrySet()) {
            if (address.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 6. 包含"张家界"但没匹配到具体区县的，默认永定区（参考Django逻辑）
        if (address.contains("张家界")) {
            return "张家界市永定区";
        }
        
        // 7. 包含"长沙"但没匹配到具体区县的，默认岳麓区
        if (address.contains("长沙")) {
            return "长沙市岳麓区";
        }
        
        return "未知区县";  // 参考Django版本
    }
}


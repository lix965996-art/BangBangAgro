package com.farmland.intel.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 高德地图API代理控制器
 * 解决前端调用高德API的跨域问题
 */
@RestController
@RequestMapping("/amap")
public class AmapProxyController {
    private static final Logger log = LoggerFactory.getLogger(AmapProxyController.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<Map<String, Object>>() {};

    @Value("${amap.web-key:}")
    private String webApiKey;
    
    private final RestTemplate restTemplate;

    public AmapProxyController() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(6000);
        this.restTemplate = new RestTemplate(requestFactory);
    }
    
    /**
     * 输入提示接口代理 - 根据输入关键字返回地点提示
     * @param keywords 搜索关键词
     * @param city 城市名称
     * @return 输入提示结果
     */
    @GetMapping("/inputtips")
    @ResponseBody
    public Map<String, Object> inputTips(
            @RequestParam String keywords,
            @RequestParam(required = false, defaultValue = "张家界") String city) {
        
        Map<String, Object> result = new HashMap<>();

        if (isBlank(webApiKey)) {
            result.put("status", "0");
            result.put("info", "AMAP_WEB_KEY 未配置");
            result.put("tips", new ArrayList<>());
            return result;
        }
        
        try {
            // 检查关键词
            if (keywords == null || keywords.trim().isEmpty()) {
                result.put("status", "0");
                result.put("info", "请输入关键词");
                result.put("tips", new ArrayList<>());
                return result;
            }

            // 构建请求URL
            String url = "https://restapi.amap.com/v3/assistant/inputtips";
            String requestUrl = UriComponentsBuilder.fromUriString(url)
                    .queryParam("key", webApiKey)
                    .queryParam("keywords", keywords)
                    .queryParam("city", city)
                    .queryParam("citylimit", "false")
                    .queryParam("datatype", "all")
                    .queryParam("output", "json")
                    .build()
                    .toUriString();

            // 调用高德API
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    MAP_RESPONSE_TYPE
            );
            Map<String, Object> responseBody = response.getBody();
            log.debug("[AMAP_PROXY][inputtips] keywords={}, city={}, status={}", keywords, city,
                    responseBody != null ? responseBody.get("status") : "null");

            if (responseBody != null && "1".equals(String.valueOf(responseBody.get("status")))) {
                // 成功返回
                result.put("status", "1");
                result.put("info", "OK");
                Object tips = responseBody.get("tips");
                result.put("tips", tips != null ? tips : new ArrayList<>());
            } else {
                // 失败返回
                result.put("status", "0");
                result.put("info", responseBody != null ? responseBody.get("info") : "调用失败");
                result.put("tips", new ArrayList<>());
            }

        } catch (ResourceAccessException e) {
            log.warn("[AMAP_PROXY][inputtips] timeout or network error: {}", e.getMessage());
            result.put("status", "0");
            result.put("info", "高德服务连接超时，请稍后重试");
            result.put("tips", new ArrayList<>());
        } catch (Exception e) {
            log.error("[AMAP_PROXY][inputtips] unexpected error", e);
            result.put("status", "0");
            result.put("info", "服务器错误: " + e.getMessage());
            result.put("tips", new ArrayList<>());
        }
        
        return result;
    }
    
    /**
     * 地理编码接口代理（地址转坐标）
     * @param address 地址
     * @param city 城市
     * @return 地理编码结果
     */
    @GetMapping("/geocode")
    public Map<String, Object> geocode(
            @RequestParam String address,
            @RequestParam(required = false) String city) {

        if (isBlank(webApiKey)) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "0");
            errorResult.put("info", "AMAP_WEB_KEY 未配置");
            return errorResult;
        }
        
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("https://restapi.amap.com/v3/geocode/geo")
                    .queryParam("key", webApiKey)
                    .queryParam("address", address)
                    .queryParam("output", "json");
            
            if (city != null && !city.isEmpty()) {
                builder.queryParam("city", city);
            }
            
            String url = builder.build().encode().toUriString();
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    MAP_RESPONSE_TYPE
            );
            Map<String, Object> responseBody = response.getBody();
            log.debug("[AMAP_PROXY][geocode] address={}, city={}, status={}", address, city,
                    responseBody != null ? responseBody.get("status") : "null");
            
            if (responseBody != null && "1".equals(responseBody.get("status"))) {
                return responseBody;
            } else {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "0");
                errorResult.put("info", responseBody != null ? responseBody.get("info") : "调用失败");
                return errorResult;
            }
        } catch (ResourceAccessException e) {
            log.warn("[AMAP_PROXY][geocode] timeout or network error: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "0");
            errorResult.put("info", "高德服务连接超时，请稍后重试");
            return errorResult;
        } catch (Exception e) {
            log.error("[AMAP_PROXY][geocode] unexpected error", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "0");
            errorResult.put("info", "代理请求失败：" + e.getMessage());
            return errorResult;
        }
    }

    /**
     * 逆地理编码接口代理（坐标转地址）
     * @param location 经纬度坐标，格式：经度,纬度
     * @param extensions 返回结果控制：base（基础）或all（详细）
     * @return 逆地理编码结果
     */
    @GetMapping("/regeocode")
    public Map<String, Object> regeocode(
            @RequestParam String location,
            @RequestParam(required = false, defaultValue = "base") String extensions) {

        if (isBlank(webApiKey)) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "0");
            errorResult.put("info", "AMAP_WEB_KEY 未配置");
            return errorResult;
        }
        
        try {
            String url = UriComponentsBuilder.fromUriString("https://restapi.amap.com/v3/geocode/regeo")
                    .queryParam("key", webApiKey)
                    .queryParam("location", location)
                    .queryParam("extensions", extensions)
                    .queryParam("output", "json")
                    .build()
                    .encode()
                    .toUriString();
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    MAP_RESPONSE_TYPE
            );
            Map<String, Object> responseBody = response.getBody();
            log.debug("[AMAP_PROXY][regeocode] location={}, extensions={}, status={}", location, extensions,
                    responseBody != null ? responseBody.get("status") : "null");
            
            if (responseBody != null && "1".equals(responseBody.get("status"))) {
                return responseBody;
            } else {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "0");
                errorResult.put("info", responseBody != null ? responseBody.get("info") : "调用失败");
                return errorResult;
            }
        } catch (ResourceAccessException e) {
            log.warn("[AMAP_PROXY][regeocode] timeout or network error: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "0");
            errorResult.put("info", "高德服务连接超时，请稍后重试");
            return errorResult;
        } catch (Exception e) {
            log.error("[AMAP_PROXY][regeocode] unexpected error", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "0");
            errorResult.put("info", "代理请求失败：" + e.getMessage());
            return errorResult;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

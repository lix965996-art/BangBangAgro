package com.farmland.intel.controller;

import com.farmland.intel.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 果蔬双检分析接口
 * 集成果蔬成熟度检测和病虫害检测功能
 */
@RestController
@RequestMapping("/crop-analysis")
public class CropAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(CropAnalysisController.class);

    // Python集成检测服务地址
    @Value("${python.api-url:http://localhost:5000}")
    private String pythonApiUrl;
    
    @Resource
    private RestTemplate restTemplate;

    private String buildPythonApiUrl(String path) {
        String baseUrl = pythonApiUrl == null ? "http://localhost:5000" : pythonApiUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!baseUrl.endsWith("/api")) {
            baseUrl = baseUrl + "/api";
        }
        return baseUrl + path;
    }

    /**
     * 果蔬成熟度检测
     * @param file 上传的图片文件
     * @return 成熟度检测结果
     */
    @PostMapping("/ripeness")
    public Result analyzeRipeness(@RequestParam("file") MultipartFile file) {
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return Result.error("400", "请上传图片文件");
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("400", "只支持图片文件");
            }

            // 构建multipart请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 创建文件资源
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 调用Python成熟度检测API
            ResponseEntity<Map> response = restTemplate.exchange(
                    buildPythonApiUrl("/detect/ripeness"),
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map responseBody = response.getBody();
            if (responseBody != null && "200".equals(responseBody.get("code"))) {
                return Result.success(responseBody.get("data"));
            } else {
                String msg = responseBody != null ? (String) responseBody.get("msg") : "成熟度检测失败";
                return Result.error("500", msg);
            }

        } catch (Exception e) {
            log.error("成熟度检测失败", e);
            return Result.error("500", "成熟度检测服务异常，请确认检测服务已启动");
        }
    }

    /**
     * 病虫害检测
     * @param file 上传的图片文件
     * @param cropType 作物类型 (tomato, corn, rice, strawberry)
     * @param conf 置信度阈值 (可选，默认0.5)
     * @return 病虫害检测结果
     */
    @PostMapping("/disease")
    public Result analyzeDisease(
            @RequestParam("file") MultipartFile file,
            @RequestParam("crop_type") String cropType,
            @RequestParam(value = "conf", defaultValue = "0.5") String conf) {
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return Result.error("400", "请上传图片文件");
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("400", "只支持图片文件");
            }

            // 构建multipart请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 创建文件资源
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("crop_type", cropType);
            body.add("conf", conf);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 调用Python病虫害检测API
            ResponseEntity<Map> response = restTemplate.exchange(
                    buildPythonApiUrl("/detect/disease"),
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map responseBody = response.getBody();
            if (responseBody != null && "200".equals(responseBody.get("code"))) {
                return Result.success(responseBody.get("data"));
            } else {
                String msg = responseBody != null ? (String) responseBody.get("msg") : "病虫害检测失败";
                return Result.error("500", msg);
            }

        } catch (Exception e) {
            log.error("病虫害检测失败, cropType={}", cropType, e);
            return Result.error("500", "病虫害检测服务异常，请确认检测服务已启动");
        }
    }

    /**
     * 果蔬双检分析 - 同时进行成熟度和病虫害检测
     * @param file 上传的图片文件
     * @param cropType 作物类型 (tomato, corn, rice, strawberry)
     * @param conf 置信度阈值 (可选，默认0.5)
     * @return 综合检测结果
     */
    @PostMapping("/both")
    public Result analyzeBoth(
            @RequestParam("file") MultipartFile file,
            @RequestParam("crop_type") String cropType,
            @RequestParam(value = "conf", defaultValue = "0.5") String conf) {
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return Result.error("400", "请上传图片文件");
            }

            // 检查文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("400", "只支持图片文件");
            }

            // 构建multipart请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 创建文件资源
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("crop_type", cropType);
            body.add("conf", conf);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 调用Python双检分析API
            ResponseEntity<Map> response = restTemplate.exchange(
                    buildPythonApiUrl("/detect/both"),
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map responseBody = response.getBody();
            if (responseBody != null && "200".equals(responseBody.get("code"))) {
                return Result.success(responseBody.get("data"));
            } else {
                String msg = responseBody != null ? (String) responseBody.get("msg") : "双检分析失败";
                return Result.error("500", msg);
            }

        } catch (Exception e) {
            log.error("双检分析失败, cropType={}", cropType, e);
            return Result.error("500", "双检分析服务异常，请确认检测服务已启动");
        }
    }

    /**
     * 获取支持的模型列表
     * @return 模型信息
     */
    @GetMapping("/models")
    public Result getModels() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(buildPythonApiUrl("/models"), Map.class);
            Map responseBody = response.getBody();
            if (responseBody != null && "200".equals(responseBody.get("code"))) {
                return Result.success(responseBody.get("data"));
            }
            return Result.error("500", "获取模型列表失败");
        } catch (Exception e) {
            return Result.error("500", "模型服务未启动，请先运行 TomatoDetection/integrated_api_server.py");
        }
    }

    /**
     * 检查Python检测服务状态
     */
    @GetMapping("/health")
    public Result healthCheck() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(buildPythonApiUrl("/health"), Map.class);
            Map responseBody = response.getBody();
            if (responseBody != null && "200".equals(responseBody.get("code"))) {
                return Result.success(responseBody.get("data"));
            }
            return Result.error("500", "检测服务状态异常");
        } catch (Exception e) {
            return Result.error("500", "检测服务未启动，请先运行 TomatoDetection/integrated_api_server.py");
        }
    }
}

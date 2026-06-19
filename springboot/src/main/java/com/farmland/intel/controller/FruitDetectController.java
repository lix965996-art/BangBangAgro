package com.farmland.intel.controller;

import com.farmland.intel.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

import java.util.Map;

/**
 * 成熟度检测接口
 * 兼容旧的 /fruit-detect 路由，将请求转发到 TomatoDetection 集成服务。
 */
@RestController
@RequestMapping("/fruit-detect")
public class FruitDetectController {

    private static final Logger log = LoggerFactory.getLogger(FruitDetectController.class);

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

    @PostMapping("/analyze")
    public Result analyze(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.error("400", "请上传图片文件");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("400", "仅支持图片文件");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    buildPythonApiUrl("/detect/ripeness"),
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && "200".equals(String.valueOf(responseBody.get("code")))) {
                return Result.success(responseBody.get("data"));
            }

            String msg = responseBody != null ? String.valueOf(responseBody.get("msg")) : "成熟度检测失败";
            return Result.error("500", msg);
        } catch (Exception e) {
            log.error("成熟度检测失败", e);
            return Result.error("500", "检测服务异常，请先运行 TomatoDetection/integrated_api_server.py");
        }
    }

    @GetMapping("/health")
    public Result healthCheck() {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    buildPythonApiUrl("/health"),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && "200".equals(String.valueOf(responseBody.get("code")))) {
                return Result.success(responseBody.get("data"));
            }
            return Result.error("500", "检测服务状态异常");
        } catch (Exception e) {
            return Result.error("500", "检测服务未启动，请先运行 TomatoDetection/integrated_api_server.py");
        }
    }
}

package com.farmland.intel.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.Files;
import com.farmland.intel.mapper.FileMapper;
import org.springframework.beans.factory.annotation.Value;
import com.farmland.intel.config.interceptor.AuthAccess;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 * 文件上传相关接口
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${files.upload.path}")
    private String fileUploadPath;

    @Value("${server.domain:http://localhost:9090}")
    private String serverDomain;

    @Resource
    private FileMapper fileMapper;

    /**
     * 文件上传接口
     * @param file 前端传递过来的文件
     * @return
     * @throws IOException
     */
    // 允许上传的文件类型白名单
    private static final java.util.Set<String> ALLOWED_TYPES = new java.util.HashSet<>(java.util.Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp",
            "pdf", "doc", "docx", "xls", "xlsx",
            "txt", "csv"
    ));
    // 最大文件大小 20MB
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    @PostMapping("/upload")
    public Result upload(@RequestParam MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String type = FileUtil.extName(originalFilename);
        long size = file.getSize();

        // 校验文件类型
        if (!ALLOWED_TYPES.contains(type.toLowerCase())) {
            return Result.error("400", "不支持的文件类型: ." + type + "，允许的类型: " + ALLOWED_TYPES);
        }

        // 校验文件大小
        if (size > MAX_FILE_SIZE) {
            return Result.error("400", "文件大小超过限制，最大允许 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        // 确保上传目录存在
        File uploadDir = new File(fileUploadPath);
        if (!uploadDir.exists()) {
            boolean created = uploadDir.mkdirs();
            if (!created) {
                throw new RuntimeException("无法创建上传目录: " + uploadDir.getAbsolutePath());
            }
        }

        String fileUUID = IdUtil.fastSimpleUUID() + StrUtil.DOT + type;
        File uploadFile = new File(fileUploadPath + fileUUID);

        String url;
        String md5 = SecureUtil.md5(file.getInputStream());

        // 检查文件是否已存在（通过MD5去重），使用同步块防止并发竞态
        synchronized (md5.intern()) {
            Files dbFiles = getFileByMd5(md5);
            if (dbFiles != null) {
                url = dbFiles.getUrl();
            } else {
                // 保存文件
                file.transferTo(uploadFile);
                // 使用配置的域名生成URL
                url = serverDomain + "/file/" + fileUUID;

                // 在同步块内保存数据库记录，防止并发插入重复记录
                Files saveFile = new Files();
                saveFile.setName(originalFilename);
                saveFile.setType(type);
                saveFile.setSize(size / 1024);
                saveFile.setUrl(url);
                saveFile.setMd5(md5);
                fileMapper.insert(saveFile);
            }
        }

        return Result.success(url);
    }

    /**
     * 文件下载接口   http://localhost:9090/file/{fileUUID}
     * @param fileUUID
     * @param response
     * @throws IOException
     */
    @AuthAccess
    @GetMapping("/{fileUUID}")
    public void download(@PathVariable String fileUUID, HttpServletResponse response) throws IOException {
        // 防止路径遍历攻击
        if (fileUUID == null || fileUUID.contains("..") || fileUUID.contains("/") || fileUUID.contains("\\")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"400\",\"msg\":\"非法文件参数\"}");
            return;
        }

        // 根据文件的唯一标识码获取文件
        File uploadFile = new File(fileUploadPath + fileUUID);

        // 检查文件是否存在
        if (!uploadFile.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"404\",\"msg\":\"文件不存在\"}");
            return;
        }

        // 设置输出流的格式
        response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileUUID, "UTF-8"));
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(uploadFile.length());

        // 流式传输文件，避免大文件OOM
        try (java.io.InputStream is = FileUtil.getInputStream(uploadFile);
             ServletOutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
    }


    /**
     * 通过文件的md5查询文件
     * @param md5
     * @return
     */
    private Files getFileByMd5(String md5) {
        // 查询文件的md5是否存在
        QueryWrapper<Files> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("md5", md5);
        List<Files> filesList = fileMapper.selectList(queryWrapper);
        return filesList.size() == 0 ? null : filesList.get(0);
    }

    @PostMapping("/update")
    public Result update(@RequestBody Files files) {
        // 仅管理员可修改文件记录
        com.farmland.intel.entity.User currentUser = com.farmland.intel.utils.TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error("403", "无权限修改文件");
        }
        return Result.success(fileMapper.updateById(files));
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        // 安全审计修复: 原代码无权限校验,任何登录用户可删除任意文件
        com.farmland.intel.entity.User currentUser = com.farmland.intel.utils.TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error("403", "仅管理员可删除文件");
        }
        Files files = fileMapper.selectById(id);
        if (files == null) {
            return Result.error("404", "文件不存在");
        }
        // 删除磁盘文件
        deleteDiskFile(files.getUrl());
        files.setIsDelete(true);
        fileMapper.updateById(files);
        return Result.success();
    }

    @PostMapping("/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
        // 安全审计修复: 原代码无任何权限校验,任意登录用户可批量删除所有人的文件
        com.farmland.intel.entity.User currentUser = com.farmland.intel.utils.TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error("403", "仅管理员可批量删除文件");
        }
        if (ids == null || ids.isEmpty()) {
            return Result.error("400", "删除ID列表不能为空");
        }
        // 防止恶意大批量删除拖垮系统
        if (ids.size() > 200) {
            return Result.error("400", "单次批量删除不能超过 200 条");
        }
        QueryWrapper<Files> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", ids);
        List<Files> files = fileMapper.selectList(queryWrapper);
        for (Files file : files) {
            deleteDiskFile(file.getUrl());
            file.setIsDelete(true);
            fileMapper.updateById(file);
        }
        return Result.success();
    }

    private void deleteDiskFile(String url) {
        if (url == null) return;
        // 从 URL 中提取文件名（最后的 fileUUID 部分）
        String fileUUID = url.substring(url.lastIndexOf('/') + 1);
        if (fileUUID.isEmpty()) return;
        // 安全审计修复: 防路径穿越(如数据库 url 字段被注入 "xx\..\..\config")
        if (fileUUID.contains("..") || fileUUID.contains("/") || fileUUID.contains("\\")) {
            return;
        }
        File diskFile = new File(fileUploadPath + fileUUID);
        // 二重检查: 真实路径必须在 fileUploadPath 内
        try {
            String canonical = diskFile.getCanonicalPath();
            String base = new File(fileUploadPath).getCanonicalPath();
            if (!canonical.startsWith(base)) {
                return;
            }
        } catch (java.io.IOException e) {
            return;
        }
        if (diskFile.exists()) {
            diskFile.delete();
        }
    }

    /**
     * 分页查询接口
     * @param pageNum
     * @param pageSize
     * @param name
     * @return
     */
    @GetMapping("/page")
    public Result findPage(@RequestParam Integer pageNum,
                           @RequestParam Integer pageSize,
                           @RequestParam(defaultValue = "") String name) {

        QueryWrapper<Files> queryWrapper = new QueryWrapper<>();
        // 查询未删除的记录
        queryWrapper.eq("is_delete", false);
        queryWrapper.orderByDesc("id");
        if (!"".equals(name)) {
            queryWrapper.like("name", name);
        }
        return Result.success(fileMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper));
    }


}

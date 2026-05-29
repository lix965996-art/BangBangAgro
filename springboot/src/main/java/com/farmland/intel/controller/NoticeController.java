package com.farmland.intel.controller;

import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletOutputStream;
import java.net.URLEncoder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.entity.Notice;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.INoticeService;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import java.io.InputStream;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import org.springframework.web.multipart.MultipartFile;
import com.farmland.intel.utils.TokenUtils;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Resource
    private INoticeService noticeService;

    // 新增或者更新
    @PostMapping
    public Result save(@RequestBody Notice notice) {
        if (notice.getId() == null) {
            notice.setTime(DateUtil.now());
            User currentUser = TokenUtils.getCurrentUser();
            if (currentUser != null) {
                notice.setUser(currentUser.getUsername());
            }
        }
        noticeService.saveOrUpdate(notice);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            Notice entity = noticeService.getById(id);
            if (entity == null) {
                return Result.error("404", "记录不存在");
            }
            if (!currentUser.getUsername().equals(entity.getUser())) {
                return Result.error(Constants.CODE_401, "无权限删除该记录");
            }
        }
        noticeService.removeById(id);
        return Result.success();
    }

    @PostMapping("/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("400", "删除ID列表不能为空");
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            List<Notice> entities = noticeService.listByIds(ids);
            for (Notice entity : entities) {
                if (!currentUser.getUsername().equals(entity.getUser())) {
                    return Result.error(Constants.CODE_401, "无权限删除记录: " + entity.getId());
                }
            }
        }
        noticeService.removeByIds(ids);
        return Result.success();
    }

    @GetMapping
    public Result findAll() {
        return Result.success(noticeService.list());
    }

    @GetMapping("/{id}")
    public Result findOne(@PathVariable Integer id) {
        return Result.success(noticeService.getById(id));
    }

    @GetMapping("/page")
    public Result findPage(@RequestParam(defaultValue = "") String name,
                           @RequestParam Integer pageNum,
                           @RequestParam Integer pageSize) {
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        if (!"".equals(name)) {
            queryWrapper.like("name", name);
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && "ROLE_USER".equals(currentUser.getRole())) {
            queryWrapper.eq("user", currentUser.getUsername());
        }
        return Result.success(noticeService.page(new Page<>(pageNum, pageSize), queryWrapper));
    }

    /**
    * 导出接口
    */
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("Notice信息表", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        ExcelWriter writer = null;

        try {
            writer = ExcelUtil.getWriter(true);

            // 数据权限控制：普通用户只能导出自己的
            QueryWrapper<Notice> exportQw = new QueryWrapper<>();
            User currentUser = TokenUtils.getCurrentUser();
            if (currentUser != null && "ROLE_USER".equals(currentUser.getRole())) {
                exportQw.eq("user", currentUser.getUsername());
            }

            int pageSize = 1000;
            int pageNum = 1;
            Page<Notice> page;
            boolean isFirstPage = true;

            do {
                page = noticeService.page(new Page<>(pageNum, pageSize), exportQw);
                List<Notice> list = page.getRecords();

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
            List<Notice> list = reader.readAll(Notice.class);
            noticeService.saveBatch(list);
        }
        return Result.success();
    }

}


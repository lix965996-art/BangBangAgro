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
import com.farmland.intel.entity.Purchase;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IPurchaseService;
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
@RequestMapping("/purchase")
public class PurchaseController {

    @Resource
    private IPurchaseService purchaseService;

    // 新增或者更新
    @PostMapping
    public Result save(@RequestBody Purchase purchase) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        if (purchase.getId() == null) {
            // 自动填充采购员
            purchase.setPurchaser(currentUser.getUsername());
        } else {
            // 更新：校验归属，防越权修改他人记录
            Purchase existing = purchaseService.getById(purchase.getId());
            if (existing == null) {
                return Result.error("404", "记录不存在");
            }
            if (!"ROLE_ADMIN".equals(currentUser.getRole())
                    && !currentUser.getUsername().equals(existing.getPurchaser())) {
                return Result.error("403", "无权修改该记录");
            }
        }
        purchaseService.saveOrUpdate(purchase);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
            Purchase entity = purchaseService.getById(id);
            if (entity == null) {
                return Result.error("404", "记录不存在");
            }
            if (!currentUser.getUsername().equals(entity.getPurchaser())) {
                return Result.error(Constants.CODE_401, "无权限删除该记录");
            }
        }
        purchaseService.removeById(id);
        return Result.success();
    }

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
            List<Purchase> entities = purchaseService.listByIds(ids);
            for (Purchase entity : entities) {
                if (!currentUser.getUsername().equals(entity.getPurchaser())) {
                    return Result.error(Constants.CODE_401, "无权限删除记录: " + entity.getId());
                }
            }
        }
        purchaseService.removeByIds(ids);
        return Result.success();
    }

    @GetMapping
    public Result findAll() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        QueryWrapper<Purchase> queryWrapper = new QueryWrapper<>();
        // 非管理员只能查看自己的采购记录
        if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
            queryWrapper.eq("purchaser", currentUser.getUsername());
        }
        return Result.success(purchaseService.list(queryWrapper));
    }

    @GetMapping("/{id}")
    public Result findOne(@PathVariable Integer id) {
        Purchase entity = purchaseService.getById(id);
        if (entity == null) {
            return Result.error("404", "记录不存在");
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        if (!"ROLE_ADMIN".equals(currentUser.getRole())
                && !currentUser.getUsername().equals(entity.getPurchaser())) {
            return Result.error("403", "无权限查看该记录");
        }
        return Result.success(entity);
    }

    @GetMapping("/page")
    public Result findPage(@RequestParam(defaultValue = "") String product,
                           @RequestParam Integer pageNum,
                           @RequestParam Integer pageSize){
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        QueryWrapper<Purchase> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        if (!"".equals(product)) {
            queryWrapper.like("product", product);
        }

        // 数据权限控制：非管理员只能看自己的
        if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
            queryWrapper.eq("purchaser", currentUser.getUsername());
        }

        return Result.success(purchaseService.page(new Page<>(pageNum, pageSize), queryWrapper));
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
        String fileName = URLEncoder.encode("Purchase信息表", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        ExcelWriter writer = null;

        try {
            writer = ExcelUtil.getWriter(true);

            // 数据权限控制：非管理员只能导出自己的
            QueryWrapper<Purchase> exportQw = new QueryWrapper<>();
            if (!"ROLE_ADMIN".equals(currentUser.getRole())) {
                exportQw.eq("purchaser", currentUser.getUsername());
            }

            int pageSize = 1000;
            int pageNum = 1;
            Page<Purchase> page;
            boolean isFirstPage = true;

            do {
                page = purchaseService.page(new Page<>(pageNum, pageSize), exportQw);
                List<Purchase> list = page.getRecords();

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
            List<Purchase> list = reader.readAll(Purchase.class);
            purchaseService.saveBatch(list);
        }
        return Result.success();
    }

}


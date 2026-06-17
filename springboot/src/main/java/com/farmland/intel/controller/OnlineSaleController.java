package com.farmland.intel.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.Inventory;
import com.farmland.intel.entity.OnlineSale;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IInventoryService;
import com.farmland.intel.service.IOnlineSaleService;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.List;

/**
 * 农作物在线销售Controller
 */
@RestController
@RequestMapping("/onlineSale")
public class OnlineSaleController {

    @Resource
    private IOnlineSaleService onlineSaleService;

    @Resource
    private IInventoryService inventoryService;

    // 新增或更新
    @PostMapping
    public Result save(@RequestBody OnlineSale onlineSale) {
        // 校验库存数量
        if (onlineSale.getInventoryId() != null) {
            Inventory inventory = inventoryService.getById(onlineSale.getInventoryId());
            if (inventory == null) {
                return Result.error("400", "库存商品不存在");
            }
            int stock = inventory.getNumber() != null ? inventory.getNumber() : 0;
            int qty = onlineSale.getQuantity() != null ? onlineSale.getQuantity() : 0;
            if (qty < 1) {
                return Result.error("400", "上架数量至少为 1");
            }
            if (qty > stock) {
                return Result.error("400", "出售数量不能大于库存数量(" + stock + ")");
            }
            // 自动填充商品信息
            if (onlineSale.getId() == null) {
                onlineSale.setProduce(inventory.getProduce());
                onlineSale.setWarehouse(inventory.getWarehouse());
            }
        }

        // 自动填充销售员 (仅新增时)
        if (onlineSale.getId() == null) {
            try {
                User currentUser = TokenUtils.getCurrentUser();
                if (currentUser != null) {
                    onlineSale.setSeller(currentUser.getUsername());
                }
            } catch (Exception e) {
                // 忽略获取用户失败的情况，避免影响主流程
            }
        }

        // 计算总价
        if (onlineSale.getPrice() != null && onlineSale.getQuantity() != null) {
            onlineSale.setTotalPrice(onlineSale.getPrice().multiply(new BigDecimal(onlineSale.getQuantity())));
        }
        onlineSaleService.saveOrUpdate(onlineSale);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            OnlineSale entity = onlineSaleService.getById(id);
            if (entity == null) {
                return Result.error("404", "记录不存在");
            }
            if (!currentUser.getUsername().equals(entity.getSeller())) {
                return Result.error(Constants.CODE_401, "无权限删除该记录");
            }
        }
        onlineSaleService.removeById(id);
        return Result.success();
    }

    @PostMapping("/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("400", "删除ID列表不能为空");
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            List<OnlineSale> entities = onlineSaleService.listByIds(ids);
            for (OnlineSale entity : entities) {
                if (!currentUser.getUsername().equals(entity.getSeller())) {
                    return Result.error(Constants.CODE_401, "无权限删除记录: " + entity.getId());
                }
            }
        }
        onlineSaleService.removeByIds(ids);
        return Result.success();
    }

    // 查询全部
    @GetMapping
    public Result findAll() {
        QueryWrapper<OnlineSale> queryWrapper = new QueryWrapper<>();
        // 非管理员只能查看自己的在线销售记录
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            queryWrapper.eq("seller", currentUser.getUsername());
        }
        return Result.success(onlineSaleService.list(queryWrapper));
    }

    // 根据ID查询
    @GetMapping("/{id}")
    public Result findOne(@PathVariable Integer id) {
        OnlineSale sale = onlineSaleService.getById(id);
        if (sale == null) return Result.error("404", "记录不存在");
        // 数据权限控制：非管理员只能查看自己的
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            if (!currentUser.getUsername().equals(sale.getSeller())) {
                return Result.error("403", "无权限查看");
            }
        }
        return Result.success(sale);
    }

    // 分页查询
    @GetMapping("/page")
    public Result findPage(@RequestParam(defaultValue = "") String produce,
                           @RequestParam Integer pageNum,
                           @RequestParam Integer pageSize) {
        QueryWrapper<OnlineSale> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(!"".equals(produce), "produce", produce);
        queryWrapper.orderByDesc("id");

        // 数据权限控制：非管理员只能看自己的
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
            queryWrapper.eq("seller", currentUser.getUsername());
        }

        Page<OnlineSale> page = onlineSaleService.page(new Page<>(pageNum, pageSize), queryWrapper);
        return Result.success(page);
    }

    // 修改状态
    private static final java.util.Set<String> ALLOWED_STATUS = java.util.Set.of("上架中", "已下架", "已售罄");

    @PutMapping("/status/{id}")
    public Result updateStatus(@PathVariable Integer id, @RequestParam String status) {
        if (!ALLOWED_STATUS.contains(status)) {
            return Result.error("400", "无效的状态值，允许: " + ALLOWED_STATUS);
        }
        OnlineSale sale = onlineSaleService.getById(id);
        if (sale == null) {
            return Result.error("404", "商品不存在");
        }
        // 数据权限：非管理员只能修改自己上架的商品，防越权(IDOR)改他人商品状态
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        if (!"ROLE_ADMIN".equals(currentUser.getRole())
                && !currentUser.getUsername().equals(sale.getSeller())) {
            return Result.error("403", "无权操作他人商品");
        }
        sale.setStatus(status);
        onlineSaleService.updateById(sale);
        return Result.success();
    }

    // 导出Excel
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("农作物在线销售", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        ExcelWriter writer = null;

        try {
            writer = ExcelUtil.getWriter(true);
            writer.addHeaderAlias("id", "ID");
            writer.addHeaderAlias("produce", "商品名称");
            writer.addHeaderAlias("warehouse", "所属仓库");
            writer.addHeaderAlias("quantity", "出售数量");
            writer.addHeaderAlias("price", "单价(元)");
            writer.addHeaderAlias("totalPrice", "总价(元)");
            writer.addHeaderAlias("status", "状态");
            writer.addHeaderAlias("seller", "销售员");
            writer.addHeaderAlias("createTime", "创建时间");
            writer.addHeaderAlias("remark", "备注");

            // 数据权限控制：非管理员只能导出自己的
            QueryWrapper<OnlineSale> exportQw = new QueryWrapper<>();
            User currentUser = TokenUtils.getCurrentUser();
            if (currentUser != null && !"ROLE_ADMIN".equals(currentUser.getRole())) {
                exportQw.eq("seller", currentUser.getUsername());
            }

            int pageSize = 1000;
            int pageNum = 1;
            Page<OnlineSale> page;
            boolean isFirstPage = true;

            do {
                page = onlineSaleService.page(new Page<>(pageNum, pageSize), exportQw);
                List<OnlineSale> list = page.getRecords();

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
}

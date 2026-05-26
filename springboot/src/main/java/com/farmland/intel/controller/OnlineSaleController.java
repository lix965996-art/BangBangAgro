package com.farmland.intel.controller;

import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.Inventory;
import com.farmland.intel.entity.OnlineSale;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IInventoryService;
import com.farmland.intel.service.IOnlineSaleService;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
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

    // 删除
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        onlineSaleService.removeById(id);
        return Result.success();
    }

    // 批量删除
    @PostMapping("/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
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
        return Result.success(onlineSaleService.getById(id));
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
    @PutMapping("/status/{id}")
    public Result updateStatus(@PathVariable Integer id, @RequestParam String status) {
        OnlineSale sale = onlineSaleService.getById(id);
        if (sale != null) {
            sale.setStatus(status);
            onlineSaleService.updateById(sale);
        }
        return Result.success();
    }

    // 导出Excel
    @GetMapping("/export")
    public void export(HttpServletResponse response) throws Exception {
        List<OnlineSale> list = onlineSaleService.list();
        ExcelWriter writer = ExcelUtil.getWriter(true);
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
        writer.write(list, true);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("农作物在线销售", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
        ServletOutputStream out = response.getOutputStream();
        writer.flush(out, true);
        out.close();
        writer.close();
    }
}

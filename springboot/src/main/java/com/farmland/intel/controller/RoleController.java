package com.farmland.intel.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.entity.Role;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IRoleService;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/role")
public class RoleController {

    @Resource
    private IRoleService roleService;

    // 新增或者更新
    @PostMapping
    public Result save(@RequestBody Role role) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        roleService.saveOrUpdate(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        roleService.removeById(id);
        return Result.success();
    }

    @PostMapping("/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        roleService.removeByIds(ids);
        return Result.success();
    }

    @GetMapping
    public Result findAll() {
        return Result.success(roleService.list());
    }

    @GetMapping("/{id}")
    public Result findOne(@PathVariable Integer id) {
        return Result.success(roleService.getById(id));
    }

    @GetMapping("/page")
    public Result findPage(@RequestParam String name,
                           @RequestParam Integer pageNum,
                           @RequestParam Integer pageSize) {
        QueryWrapper<Role> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("name", name);
        queryWrapper.orderByDesc("id");
        return Result.success(roleService.page(new Page<>(pageNum, pageSize), queryWrapper));
    }

    /**
     * 绑定角色和菜单的关系
     * @param roleId 角色id
     * @param menuIds 菜单id数组
     * @return
     */
    @PostMapping("/roleMenu/{roleId}")
    public Result roleMenu(@PathVariable Integer roleId, @RequestBody List<Integer> menuIds) {
        // 仅管理员可修改角色权限
        com.farmland.intel.entity.User currentUser = com.farmland.intel.utils.TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error("403", "无权限修改角色菜单");
        }
        roleService.setRoleMenu(roleId, menuIds);
        return Result.success();
    }

    @GetMapping("/roleMenu/{roleId}")
    public Result getRoleMenu(@PathVariable Integer roleId) {
        return Result.success( roleService.getRoleMenu(roleId));
    }

}


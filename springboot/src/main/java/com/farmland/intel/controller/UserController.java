package com.farmland.intel.controller;

import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.config.interceptor.AuthAccess;
import com.farmland.intel.controller.dto.UserDTO;
import com.farmland.intel.controller.dto.UserPasswordDTO;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IUserService;
import com.farmland.intel.utils.PasswordUtils;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import cn.hutool.core.util.RandomUtil;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Value("${files.upload.path}")
    private String filesUploadPath;

    @Resource
    private IUserService userService;

    @PostMapping("/login")
    public Result login(@RequestBody UserDTO userDTO) {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Result.error(Constants.CODE_400, "参数错误");
        }
        return Result.success(userService.login(userDTO));
    }

    @PostMapping("/register")
    public Result register(@RequestBody UserDTO userDTO) {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Result.error(Constants.CODE_400, "参数错误");
        }
        userDTO.setNickname(userDTO.getUsername());
        return Result.success(sanitizeUser(userService.register(userDTO)));
    }

    @PostMapping
    public Result save(@RequestBody User user) {
        // 校验当前用户是否有管理员权限
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }

        String username = user.getUsername();
        if (StrUtil.isBlank(username)) {
            return Result.error(Constants.CODE_400, "参数错误");
        }
        if (StrUtil.isBlank(user.getNickname())) {
            user.setNickname(username);
        }

        if (user.getId() != null) {
            user.setPassword(null);
        } else {
            if (StrUtil.isBlank(user.getPassword())) {
                user.setPassword(RandomUtil.randomString(12));
            }
            user.setPassword(PasswordUtils.encode(user.getPassword()));
        }

        userService.saveOrUpdate(user);
        return Result.success(sanitizeUser(user));
    }

    @PostMapping("/password")
    public Result password(@RequestBody UserPasswordDTO userPasswordDTO) {
        // 校验当前用户只能修改自己的密码
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error(Constants.CODE_401, "未登录");
        }
        if (!currentUser.getUsername().equals(userPasswordDTO.getUsername())) {
            return Result.error(Constants.CODE_401, "无权限修改其他用户密码");
        }
        userService.updatePassword(userPasswordDTO);
        return Result.success();
    }

    @AuthAccess
    @PutMapping("/reset")
    public Result reset(@RequestBody UserPasswordDTO userPasswordDTO) {
        if (StrUtil.isBlank(userPasswordDTO.getUsername()) || StrUtil.isBlank(userPasswordDTO.getPhone())) {
            return Result.error("-1", "用户名或手机号不正确");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userPasswordDTO.getUsername());
        queryWrapper.eq("phone", userPasswordDTO.getPhone());
        List<User> list = userService.list(queryWrapper);
        if (CollUtil.isEmpty(list)) {
            // 统一错误消息，防止用户枚举
            return Result.error("-1", "用户名或手机号不正确");
        }
        User user = list.get(0);
        user.setPassword(PasswordUtils.encode(RandomUtil.randomString(12)));
        userService.updateById(user);
        return Result.success();
    }

    /**
     * 封禁 / 解封账号（仅管理员）
     * POST /user/{id}/ban  → 封禁
     * POST /user/{id}/unban → 解封
     */
    @PostMapping("/{id}/ban")
    public Result banUser(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        if (currentUser.getId().equals(id)) {
            return Result.error(Constants.CODE_400, "不能封禁自己的账号");
        }
        User target = userService.getById(id);
        if (target == null) return Result.error("404", "用户不存在");
        target.setStatus(1);
        target.setPassword(null); // 不更新密码
        userService.updateById(target);
        return Result.success("已封禁该账号");
    }

    @PostMapping("/{id}/unban")
    public Result unbanUser(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        User target = userService.getById(id);
        if (target == null) return Result.error("404", "用户不存在");
        target.setStatus(0);
        target.setPassword(null);
        userService.updateById(target);
        return Result.success("已解除封禁");
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        return Result.success(userService.removeById(id));
    }

    @PostMapping("/del/batch")
    public Result deleteBatch(@RequestBody List<Integer> ids) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        return Result.success(userService.removeByIds(ids));
    }

    @GetMapping
    public Result findAll() {
        // 仅管理员可获取全量用户列表
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !"ROLE_ADMIN".equals(currentUser.getRole())) {
            return Result.error("403", "无权限");
        }
        return Result.success(sanitizeUsers(userService.list()));
    }

    @GetMapping("/{id}")
    public Result findOne(@PathVariable Integer id) {
        return Result.success(sanitizeUser(userService.getById(id)));
    }

    @GetMapping("/username/{username}")
    public Result findByUsername(@PathVariable String username) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        return Result.success(sanitizeUser(userService.getOne(queryWrapper)));
    }

    @GetMapping("/page")
    public Result findPage(@RequestParam Integer pageNum,
                           @RequestParam Integer pageSize,
                           @RequestParam(defaultValue = "") String username,
                           @RequestParam(defaultValue = "") String email,
                           @RequestParam(defaultValue = "") String address) {

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        if (!"".equals(username)) {
            queryWrapper.like("username", username);
        }
        if (!"".equals(email)) {
            queryWrapper.like("email", email);
        }
        if (!"".equals(address)) {
            queryWrapper.like("address", address);
        }

        Page<User> page = userService.page(new Page<>(pageNum, pageSize), queryWrapper);
        sanitizeUsers(page.getRecords());
        return Result.success(page);
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("用户信息", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        ExcelWriter writer = null;

        try {
            writer = ExcelUtil.getWriter(true);
            writer.setOnlyAlias(true);
            writer.addHeaderAlias("username", "用户名");
            writer.addHeaderAlias("nickname", "昵称");
            writer.addHeaderAlias("email", "邮箱");
            writer.addHeaderAlias("phone", "电话");
            writer.addHeaderAlias("address", "地址");
            writer.addHeaderAlias("createTime", "创建时间");
            writer.addHeaderAlias("avatarUrl", "头像");

            int pageSize = 1000;
            int pageNum = 1;
            Page<User> page;
            boolean isFirstPage = true;

            do {
                page = userService.page(new Page<>(pageNum, pageSize));
                List<User> list = page.getRecords();
                if (CollUtil.isEmpty(list)) {
                    break;
                }

                sanitizeUsers(list);
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

    @Transactional
    @PostMapping("/import")
    public Result imp(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".xlsx") && !originalFilename.toLowerCase().endsWith(".xls"))) {
            return Result.error("400", "仅支持 .xlsx 或 .xls 格式的Excel文件");
        }
        List<User> users;
        try (InputStream inputStream = file.getInputStream();
             ExcelReader reader = ExcelUtil.getReader(inputStream)) {
            List<List<Object>> list = reader.read(1);
            users = CollUtil.newArrayList();
            java.util.Set<String> existingUsernames = new java.util.HashSet<>();
            // 查询已存在的用户名
            userService.list().forEach(u -> existingUsernames.add(u.getUsername()));

            for (List<Object> row : list) {
                if (CollUtil.isEmpty(row) || row.get(0) == null) {
                    continue;
                }
                String username = row.get(0).toString().trim();
                if (StrUtil.isBlank(username) || existingUsernames.contains(username)) {
                    continue; // 跳过空用户名和已存在的用户名
                }
                existingUsernames.add(username); // 防止 Excel 内重复
                User user = new User();
                user.setUsername(username);
                String rawPassword = row.size() > 1 && row.get(1) != null ? row.get(1).toString() : "";
                if (StrUtil.isBlank(rawPassword)) {
                    rawPassword = RandomUtil.randomString(12);
                }
                user.setPassword(PasswordUtils.encode(rawPassword));
                user.setNickname(row.size() > 2 && row.get(2) != null ? row.get(2).toString() : user.getUsername());
                user.setEmail(row.size() > 3 && row.get(3) != null ? row.get(3).toString() : null);
                user.setPhone(row.size() > 4 && row.get(4) != null ? row.get(4).toString() : null);
                user.setAddress(row.size() > 5 && row.get(5) != null ? row.get(5).toString() : null);
                user.setAvatarUrl(row.size() > 6 && row.get(6) != null ? row.get(6).toString() : null);
                users.add(user);
            }
        }

        if (!users.isEmpty()) {
            userService.saveBatch(users);
        }
        return Result.success(true);
    }

    private User sanitizeUser(User user) {
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    private List<User> sanitizeUsers(List<User> users) {
        if (users != null) {
            users.forEach(this::sanitizeUser);
        }
        return users;
    }
}

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
import com.farmland.intel.entity.ScoreAdjustment;
import com.farmland.intel.entity.User;
import com.farmland.intel.entity.UserScoreSnapshot;
import com.farmland.intel.service.IScoreAdjustmentService;
import com.farmland.intel.service.IUserScoreSnapshotService;
import com.farmland.intel.service.IUserService;
import com.farmland.intel.service.UserScoreService;
import com.farmland.intel.utils.PasswordUtils;
import com.farmland.intel.utils.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import cn.hutool.core.util.RandomUtil;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Value("${files.upload.path}")
    private String filesUploadPath;

    @Resource
    private IUserService userService;

    @Resource
    private IUserScoreSnapshotService snapshotService;

    @Resource
    private IScoreAdjustmentService adjustmentService;

    @Resource
    private UserScoreService userScoreService;

    @PostMapping("/login")
    public Result login(@RequestBody UserDTO userDTO, HttpServletRequest request) {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Result.error(Constants.CODE_400, "参数错误");
        }
        return Result.success(userService.login(userDTO, getClientIp(request)));
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For / X-Real-IP（经 Nginx 等代理时），兜底 RemoteAddr */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String h : headers) {
            String ip = request.getHeader(h);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                int comma = ip.indexOf(',');          // X-Forwarded-For 可能是 "真实IP, 代理IP"
                return (comma > 0 ? ip.substring(0, comma) : ip).trim();
            }
        }
        return request.getRemoteAddr();
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

        boolean isNewUser = user.getId() == null;
        String initialPasswordPlain = null;

        if (!isNewUser) {
            user.setPassword(null);
        } else {
            // BUG-01 修复:新建用户时若没传密码,生成可读的默认密码并通过 msg 返回给管理员
            // 旧逻辑用 RandomUtil 生成 12 位随机字符串,sanitize 又把密码置 null,
            // 导致管理员/新用户都不知道密码,新用户永远无法登录。
            if (StrUtil.isBlank(user.getPassword())) {
                initialPasswordPlain = "Aa" + username + "@2026";
                user.setPassword(initialPasswordPlain);
                log.info("[新建用户] {} 未指定密码,使用默认初始密码 {} (请通知用户首次登录后修改)",
                        username, initialPasswordPlain);
            }
            user.setPassword(PasswordUtils.encode(user.getPassword()));
        }

        userService.saveOrUpdate(user);

        if (initialPasswordPlain != null) {
            return Result.success(sanitizeUser(user),
                    "用户 " + username + " 已创建,初始密码:" + initialPasswordPlain + " (请告知用户首次登录后修改)");
        }
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
        String newPasswordPlain = RandomUtil.randomString(12);
        user.setPassword(PasswordUtils.encode(newPasswordPlain));
        userService.updateById(user);
        return Result.success(newPasswordPlain, "密码重置成功，请妥善保管新密码");
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
        enrichScores(page.getRecords());
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

    /**
     * 用户统计：今日出勤率、总人数
     */
    @GetMapping("/stats")
    public Result getUserStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        long total = userService.count();
        stats.put("total", total);

        // 统计今日登录人数（lastLoginTime 在今天）
        java.util.Calendar today = java.util.Calendar.getInstance();
        today.set(java.util.Calendar.HOUR_OF_DAY, 0);
        today.set(java.util.Calendar.MINUTE, 0);
        today.set(java.util.Calendar.SECOND, 0);
        today.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Date todayStart = today.getTime();

        long activeToday = userService.count(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .ge("last_login_time", todayStart)
        );
        stats.put("activeToday", activeToday);
        stats.put("attendanceRate", total > 0 ? Math.round(activeToday * 1000.0 / total) / 10.0 : 0);

        return Result.success(stats);
    }

    /**
     * 保存安全问题（登录后调用）
     */
    @PostMapping("/security-question")
    public Result saveSecurityQuestion(@RequestBody java.util.Map<String, String> params) {
        String question = params.get("question");
        String answer = params.get("answer");
        if (StrUtil.isBlank(question) || StrUtil.isBlank(answer)) {
            return Result.error(Constants.CODE_400, "问题和答案不能为空");
        }
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error(Constants.CODE_401, "请先登录");
        }
        User user = userService.getById(currentUser.getId());
        user.setSecurityQuestion(question);
        user.setSecurityAnswer(answer.trim());
        userService.updateById(user);
        return Result.success("安全问题设置成功");
    }

    /**
     * 获取指定用户的安全问题（找回密码用，无需登录）
     */
    @AuthAccess
    @GetMapping("/security-question")
    public Result getSecurityQuestion(@RequestParam String username) {
        if (StrUtil.isBlank(username)) {
            return Result.error(Constants.CODE_400, "用户名不能为空");
        }
        User user = userService.getOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("username", username)
        );
        if (user == null) {
            return Result.error(Constants.CODE_404, "用户不存在");
        }
        if (StrUtil.isBlank(user.getSecurityQuestion())) {
            return Result.error(Constants.CODE_400, "该用户未设置安全问题");
        }
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("question", user.getSecurityQuestion());
        return Result.success(data);
    }

    /**
     * 通过安全问题验证后重置密码（无需登录）
     */
    @AuthAccess
    @PostMapping("/reset-by-question")
    public Result resetByQuestion(@RequestBody java.util.Map<String, String> params) {
        String username = params.get("username");
        String answer = params.get("answer");
        String newPassword = params.get("newPassword");
        if (StrUtil.isBlank(username) || StrUtil.isBlank(answer) || StrUtil.isBlank(newPassword)) {
            return Result.error(Constants.CODE_400, "参数不完整");
        }
        if (newPassword.length() < 6) {
            return Result.error(Constants.CODE_400, "密码长度至少6位");
        }
        User user = userService.getOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                .eq("username", username)
        );
        if (user == null) {
            return Result.error(Constants.CODE_404, "用户不存在");
        }
        if (StrUtil.isBlank(user.getSecurityQuestion())) {
            return Result.error(Constants.CODE_400, "该用户未设置安全问题");
        }
        if (!answer.trim().equals(user.getSecurityAnswer())) {
            return Result.error(Constants.CODE_400, "答案错误");
        }
        user.setPassword(PasswordUtils.encode(newPassword));
        userService.updateById(user);
        return Result.success("密码重置成功");
    }

    // ==================== 周滚动评分 ====================

    /** 绩效明细 (绩效弹窗用): 管理员可查任意人, 普通用户只能查自己 */
    @GetMapping("/score")
    public Result getScore(@RequestParam Integer userId) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error(Constants.CODE_401, "未登录");
        if (!Constants.ROLE_ADMIN.equals(currentUser.getRole()) && !currentUser.getId().equals(userId)) {
            return Result.error(Constants.CODE_401, "无权限");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        UserScoreSnapshot s = snapshotService.getLatestByUser(userId);
        if (s == null) {
            data.put("available", false);
            return Result.success(data);
        }
        data.put("available", true);
        data.put("total", s.getTotal());
        data.put("grade", s.getGrade());
        data.put("commentary", s.getCommentary());
        data.put("dataThin", s.getDataThin() != null && s.getDataThin() == 1);
        data.put("isOverride", s.getIsOverride() != null && s.getIsOverride() == 1);
        data.put("windowStart", s.getWindowStart());
        data.put("windowEnd", s.getWindowEnd());
        Map<String, Object> subs = new LinkedHashMap<>();
        subs.put("alert", s.getAlertSub());
        subs.put("ai", s.getAiSub());
        subs.put("approval", s.getApprovalSub());
        subs.put("attendance", s.getAttendanceSub());
        subs.put("knowledge", s.getKnowledgeSub());
        data.put("subs", subs);
        return Result.success(data);
    }

    /** 管理员人工覆写总分 (写审计表 + 标记 is_override), 仅管理员 */
    @PostMapping("/score/override")
    public Result overrideScore(@RequestBody Map<String, Object> body) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !Constants.ROLE_ADMIN.equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        Object uidObj = body.get("userId");
        Object totalObj = body.get("newTotal");
        String reason = body.get("reason") == null ? "" : body.get("reason").toString();
        if (uidObj == null || totalObj == null) {
            return Result.error(Constants.CODE_400, "userId 和 newTotal 不能为空");
        }
        Integer userId;
        BigDecimal newTotal;
        try {
            userId = Integer.valueOf(uidObj.toString());
            newTotal = new BigDecimal(totalObj.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return Result.error(Constants.CODE_400, "参数格式错误");
        }
        UserScoreSnapshot s = snapshotService.getLatestByUser(userId);
        if (s == null) return Result.error(Constants.CODE_404, "该用户暂无评分快照, 请先回填");
        ScoreAdjustment adj = new ScoreAdjustment();
        adj.setUserId(userId);
        adj.setWindowStart(s.getWindowStart());
        adj.setAdminId(currentUser.getId());
        adj.setOldTotal(s.getTotal());
        adj.setNewTotal(newTotal);
        adj.setReason(reason);
        adj.setCreatedAt(new java.util.Date());
        adjustmentService.save(adj);

        s.setTotal(newTotal);
        s.setGrade(gradeOf(newTotal.doubleValue()));
        s.setIsOverride(1);
        snapshotService.updateById(s);
        return Result.success("已覆写");
    }

    /** 手动回填当前窗口评分 (部署后立即出数), 仅管理员 */
    @PostMapping("/score/backfill")
    public Result backfillScore() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null || !Constants.ROLE_ADMIN.equals(currentUser.getRole())) {
            return Result.error(Constants.CODE_401, "无权限，仅管理员可操作");
        }
        int n = userScoreService.computeAndPersistWindow();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scored", n);
        return Result.success(data, "回填完成, 共评分 " + n + " 名用户");
    }

    /** 把每个用户的最新评分总分/评级挂到 transient 字段 (供卡片展示) */
    private void enrichScores(List<User> users) {
        if (users == null || users.isEmpty()) return;
        List<Integer> ids = users.stream().map(User::getId).collect(Collectors.toList());
        Map<Integer, UserScoreSnapshot> latest = snapshotService.getLatestByUserIds(ids);
        for (User u : users) {
            UserScoreSnapshot s = latest.get(u.getId());
            if (s != null) {
                u.setScoreTotal(s.getTotal() != null ? s.getTotal().doubleValue() : null);
                u.setScoreGrade(s.getGrade());
            }
        }
    }

    private String gradeOf(double total) {
        if (total >= 90) return "S";
        if (total >= 80) return "A";
        if (total >= 70) return "B";
        if (total >= 60) return "C";
        return "D";
    }

    private User sanitizeUser(User user) {
        if (user != null) {
            user.setPassword(null);
            user.setSecurityAnswer(null);
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

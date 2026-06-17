package com.farmland.intel.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.log.Log;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.common.Constants;
import com.farmland.intel.common.RoleEnum;
import com.farmland.intel.controller.dto.UserDTO;
import com.farmland.intel.controller.dto.UserPasswordDTO;
import com.farmland.intel.entity.Menu;
import com.farmland.intel.entity.User;
import com.farmland.intel.exception.ServiceException;
import com.farmland.intel.mapper.RoleMapper;
import com.farmland.intel.mapper.RoleMenuMapper;
import com.farmland.intel.mapper.UserMapper;
import com.farmland.intel.service.IMenuService;
import com.farmland.intel.service.IUserService;
import com.farmland.intel.utils.PasswordUtils;
import com.farmland.intel.utils.TokenUtils;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final Log LOG = Log.get();

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private RoleMenuMapper roleMenuMapper;

    @Resource
    private IMenuService menuService;

    @Resource
    private RestTemplate restTemplate;

    /** 高德 Web 服务 Key，用于 IP 归属地解析（复用项目已有高德配置） */
    @Value("${amap.web-key:}")
    private String amapWebKey;

    @Override
    public UserDTO login(UserDTO userDTO) {
        return login(userDTO, null);
    }

    @Override
    public UserDTO login(UserDTO userDTO, String clientIp) {
        User one = getUserInfo(userDTO);
        if (one == null) {
            throw new ServiceException(Constants.CODE_600, "用户名或密码错误");
        }

        // 更新最后登录时间 + IP + 大致归属地
        one.setLastLoginTime(new java.util.Date());
        if (clientIp != null && !clientIp.isEmpty()) {
            one.setLastLoginIp(clientIp);
            one.setLastLoginRegion(resolveIpRegion(clientIp));  // 失败/内网返回 null，不影响登录
        }
        updateById(one);

        BeanUtil.copyProperties(one, userDTO, true);
        userDTO.setToken(TokenUtils.genToken(one.getId().toString()));
        userDTO.setPassword(null);
        userDTO.setMenus(getRoleMenus(one.getRole()));
        return userDTO;
    }

    /**
     * 用高德 IP 定位把 IP 解析成大致归属地（省+市，城市级，可能不准）。
     * 内网/本机 IP、未配高德 Key、或解析失败 → 返回 null，绝不抛异常影响登录。
     */
    private String resolveIpRegion(String ip) {
        try {
            if (ip == null) return null;
            // 内网 / 本机 IP 高德无法定位，直接标注
            if (ip.startsWith("127.") || ip.startsWith("192.168.") || ip.startsWith("10.")
                    || ip.startsWith("172.") || "localhost".equalsIgnoreCase(ip) || ip.contains(":")) {
                return "内网/本机";
            }
            if (amapWebKey == null || amapWebKey.isEmpty()) return null;

            String url = "https://restapi.amap.com/v3/ip?ip=" + ip + "&key=" + amapWebKey;
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) return null;
            JSONObject json = JSONUtil.parseObj(body);
            if (!"1".equals(json.getStr("status"))) return null;
            // province/city 在内网或国外可能返回空数组 []，getStr 会得到 "[]"
            String province = json.getStr("province");
            String city = json.getStr("city");
            if (province == null || province.isEmpty() || "[]".equals(province)) return null;
            if (city == null || "[]".equals(city)) city = "";
            String region = (province + city).trim();
            return region.isEmpty() ? null : region;
        } catch (Exception e) {
            LOG.warn("IP 归属地解析失败: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public User register(UserDTO userDTO) {
        User one = getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, userDTO.getUsername()));
        if (one != null) {
            throw new ServiceException(Constants.CODE_600, "用户已存在");
        }

        one = new User();
        BeanUtil.copyProperties(userDTO, one, true);
        one.setPassword(PasswordUtils.encode(userDTO.getPassword()));
        one.setRole(RoleEnum.ROLE_USER.toString());
        // 生成唯一6位帮帮农ID（UID）
        one.setUid(generateUniqueUid());
        save(one);
        one.setPassword(null);
        return one;
    }

    /**
     * 生成唯一6位数字UID（100000-999999），冲突时重试
     */
    private String generateUniqueUid() {
        java.util.Random random = new java.util.Random();
        String uid;
        int maxRetries = 20;
        do {
            uid = String.valueOf(100000 + random.nextInt(900000));
            maxRetries--;
        } while (maxRetries > 0 &&
                count(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>().eq("uid", uid)) > 0);
        return uid;
    }

    @Override
    public void updatePassword(UserPasswordDTO userPasswordDTO) {
        User user = getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, userPasswordDTO.getUsername()));
        if (user == null) {
            PasswordUtils.matches(userPasswordDTO.getPassword(),
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            throw new ServiceException(Constants.CODE_600, "密码错误");
        }
        if (!PasswordUtils.matches(userPasswordDTO.getPassword(), user.getPassword())) {
            throw new ServiceException(Constants.CODE_600, "密码错误");
        }
        user.setPassword(PasswordUtils.encode(userPasswordDTO.getNewPassword()));
        updateById(user);
    }

    private User getUserInfo(UserDTO userDTO) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userDTO.getUsername());
        User one;
        try {
            one = getOne(queryWrapper);
        } catch (Exception e) {
            LOG.error(e);
            throw new ServiceException(Constants.CODE_500, "系统错误");
        }

        if (one == null) {
            // 防止用户枚举时序攻击：用户名不存在时也执行等时bcrypt
            PasswordUtils.matches(userDTO.getPassword(),
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
            return null;
        }
        if (!PasswordUtils.matches(userDTO.getPassword(), one.getPassword())) {
            return null;
        }

        // 检查账号是否被封禁
        if (one.getStatus() != null && one.getStatus() == 1) {
            throw new ServiceException(Constants.CODE_600, "您已被管理员拉黑，请稍后再试！");
        }

        if (PasswordUtils.needsEncoding(one.getPassword())) {
            one.setPassword(PasswordUtils.encode(userDTO.getPassword()));
            updateById(one);
        }
        return one;
    }

    private List<Menu> getRoleMenus(String roleFlag) {
        if (roleFlag == null) {
            return new ArrayList<>();
        }
        Integer roleId = roleMapper.selectByFlag(roleFlag);
        if (roleId == null) {
            return new ArrayList<>();
        }
        List<Integer> menuIds = roleMenuMapper.selectByRoleId(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Menu> menus = menuService.findMenus("");
        List<Menu> roleMenus = new ArrayList<>();
        for (Menu menu : menus) {
            if (menuIds.contains(menu.getId())) {
                roleMenus.add(menu);
            }
            List<Menu> children = menu.getChildren();
            if (children != null) {
                children.removeIf(child -> !menuIds.contains(child.getId()));
            }
        }
        return roleMenus;
    }
}

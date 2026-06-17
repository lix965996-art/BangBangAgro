package com.farmland.intel.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.farmland.intel.common.Result;
import com.farmland.intel.config.interceptor.AuthAccess;
import com.farmland.intel.entity.*;
import com.farmland.intel.service.*;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mini")
public class MiniProgramController {

    @Resource
    private IMiniUserService miniUserService;

    @Resource
    private IReservationService reservationService;

    @Resource
    private IGroupBuyService groupBuyService;

    @Resource
    private IGroupBuyOrderService groupBuyOrderService;

    @Resource
    private IBadgeService badgeService;

    // ==================== 用户 ====================

    @AuthAccess
    @PostMapping("/user/login")
    public Result login(@RequestBody MiniUser user) {
        if (StrUtil.isBlank(user.getOpenid())) {
            return Result.error("400", "openid不能为空");
        }
        QueryWrapper<MiniUser> wrapper = new QueryWrapper<>();
        wrapper.eq("openid", user.getOpenid());
        MiniUser existUser = miniUserService.getOne(wrapper);
        if (existUser != null) {
            if (StrUtil.isNotBlank(user.getNickname())) {
                existUser.setNickname(user.getNickname());
            }
            if (StrUtil.isNotBlank(user.getAvatarUrl())) {
                existUser.setAvatarUrl(user.getAvatarUrl());
            }
            miniUserService.updateById(existUser);
            return Result.success(existUser);
        }
        user.setCreatedAt(LocalDateTime.now());
        miniUserService.save(user);
        return Result.success(user);
    }

    @AuthAccess
    @GetMapping("/user/{id}")
    public Result getUser(@PathVariable Integer id) {
        MiniUser user = miniUserService.getById(id);
        if (user == null) {
            return Result.error("404", "用户不存在");
        }
        return Result.success(user);
    }

    @AuthAccess
    @PutMapping("/user/{id}")
    public Result updateUser(@PathVariable Integer id, @RequestBody MiniUser user) {
        user.setId(id);
        miniUserService.updateById(user);
        return Result.success();
    }

    // ==================== 预约 ====================

    @AuthAccess
    @PostMapping("/reservation")
    public Result createReservation(@RequestBody Reservation reservation) {
        reservation.setVerifyCode(IdUtil.randomUUID().replace("-", "").substring(0, 16).toUpperCase());
        reservation.setStatus("pending");
        reservation.setCreatedAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationService.save(reservation);
        return Result.success(reservation);
    }

    @AuthAccess
    @GetMapping("/reservation/user/{userId}")
    public Result getUserReservations(@PathVariable Integer userId) {
        QueryWrapper<Reservation> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        List<Reservation> list = reservationService.list(wrapper);
        return Result.success(list);
    }

    @AuthAccess
    @GetMapping("/reservation/{id}")
    public Result getReservation(@PathVariable Integer id) {
        return Result.success(reservationService.getById(id));
    }

    @AuthAccess
    @PutMapping("/reservation/{id}/cancel")
    public Result cancelReservation(@PathVariable Integer id) {
        UpdateWrapper<Reservation> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).set("status", "cancelled");
        reservationService.update(wrapper);
        return Result.success();
    }

    @AuthAccess
    @PutMapping("/reservation/{id}/verify")
    public Result verifyReservation(@PathVariable Integer id) {
        UpdateWrapper<Reservation> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).eq("status", "pending").set("status", "completed");
        boolean updated = reservationService.update(wrapper);
        if (!updated) {
            return Result.error("400", "核销失败，预约状态不正确");
        }
        return Result.success();
    }

    // ==================== 团购 ====================

    @AuthAccess
    @PostMapping("/group-buy")
    public Result createGroupBuy(@RequestBody GroupBuy groupBuy) {
        groupBuy.setStatus("active");
        groupBuy.setCurrentWeight(BigDecimal.ZERO);
        groupBuy.setCreatedAt(LocalDateTime.now());
        groupBuyService.save(groupBuy);
        return Result.success(groupBuy);
    }

    @AuthAccess
    @GetMapping("/group-buy/active")
    public Result getActiveGroups() {
        QueryWrapper<GroupBuy> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "active").orderByDesc("created_at");
        List<GroupBuy> list = groupBuyService.list(wrapper);
        return Result.success(list);
    }

    @AuthAccess
    @GetMapping("/group-buy/{id}")
    public Result getGroupBuy(@PathVariable Integer id) {
        GroupBuy groupBuy = groupBuyService.getById(id);
        if (groupBuy == null) {
            return Result.error("404", "团购不存在");
        }
        QueryWrapper<GroupBuyOrder> orderWrapper = new QueryWrapper<>();
        orderWrapper.eq("group_buy_id", id).orderByDesc("created_at");
        List<GroupBuyOrder> orders = groupBuyOrderService.list(orderWrapper);
        return Result.success(Map.of("groupBuy", groupBuy, "orders", orders));
    }

    @AuthAccess
    @PostMapping("/group-buy/{id}/join")
    public Result joinGroupBuy(@PathVariable Integer id, @RequestBody GroupBuyOrder order) {
        GroupBuy groupBuy = groupBuyService.getById(id);
        if (groupBuy == null) {
            return Result.error("404", "团购不存在");
        }
        if (!"active".equals(groupBuy.getStatus())) {
            return Result.error("400", "团购已结束");
        }
        order.setGroupBuyId(id);
        order.setStatus("paid");
        order.setCreatedAt(LocalDateTime.now());
        order.setAmount(order.getWeight().multiply(groupBuy.getPrice()));
        groupBuyOrderService.save(order);

        UpdateWrapper<GroupBuy> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id).setSql("current_weight = current_weight + " + order.getWeight());
        BigDecimal newWeight = groupBuy.getCurrentWeight().add(order.getWeight());
        if (newWeight.compareTo(groupBuy.getTargetWeight()) >= 0) {
            wrapper.set("status", "completed");
        }
        groupBuyService.update(wrapper);
        return Result.success(order);
    }

    @AuthAccess
    @GetMapping("/group-buy/user/{userId}")
    public Result getUserGroupBuys(@PathVariable Integer userId) {
        QueryWrapper<GroupBuyOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at");
        List<GroupBuyOrder> orders = groupBuyOrderService.list(wrapper);
        return Result.success(orders);
    }

    // ==================== 成就 ====================

    @AuthAccess
    @GetMapping("/badge/user/{userId}")
    public Result getUserBadges(@PathVariable Integer userId) {
        QueryWrapper<Badge> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        List<Badge> list = badgeService.list(wrapper);
        return Result.success(list);
    }

    @AuthAccess
    @PostMapping("/badge")
    public Result updateBadge(@RequestBody Badge badge) {
        QueryWrapper<Badge> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", badge.getUserId()).eq("badge_type", badge.getBadgeType());
        Badge existing = badgeService.getOne(wrapper);
        if (existing != null) {
            existing.setProgress(badge.getProgress());
            if (badge.getProgress() >= existing.getTarget()) {
                existing.setUnlocked(1);
                existing.setUnlockedAt(LocalDateTime.now());
            }
            badgeService.updateById(existing);
            return Result.success(existing);
        }
        if (badge.getProgress() >= badge.getTarget()) {
            badge.setUnlocked(1);
            badge.setUnlockedAt(LocalDateTime.now());
        }
        badgeService.save(badge);
        return Result.success(badge);
    }
}

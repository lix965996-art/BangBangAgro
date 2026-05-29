package com.farmland.intel.controller;

import com.farmland.intel.common.Result;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IFriendshipService;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 好友关系接口
 * GET  /friendship/search?uid=123456   通过UID搜索用户
 * POST /friendship/add                 { "friendId": 2 } 添加好友
 * GET  /friendship/list                获取好友列表
 * DELETE /friendship/{friendId}        删除好友
 * GET  /friendship/status/{friendId}   是否已是好友
 */
@RestController
@RequestMapping("/friendship")
public class FriendshipController {

    @Resource
    private IFriendshipService friendshipService;

    /** 通过帮帮农ID（UID）搜索用户 */
    @GetMapping("/search")
    public Result searchByUid(@RequestParam String uid) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        User found = friendshipService.searchByUid(uid, currentUser.getId());
        if (found == null) return Result.error("404", "未找到该用户，请确认 UID 是否正确");
        // 附带是否已是好友的状态
        boolean already = friendshipService.isFriend(currentUser.getId(), found.getId());
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("user", found);
        resp.put("isFriend", already);
        return Result.success(resp);
    }

    /** 添加好友（双向） */
    @PostMapping("/add")
    public Result addFriend(@RequestBody Map<String, Object> body) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        Integer friendId = body == null ? null : (Integer) body.get("friendId");
        if (friendId == null) return Result.error("400", "参数错误");
        if (friendId.equals(currentUser.getId())) return Result.error("400", "不能添加自己为好友");
        String result = friendshipService.addFriend(currentUser.getId(), friendId);
        if ("already".equals(result)) return Result.error("400", "已经是好友了");
        return Result.success("添加成功");
    }

    /** 获取好友列表 */
    @GetMapping("/list")
    public Result getFriendList() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        List<Map<String, Object>> list = friendshipService.getFriendList(currentUser.getId());
        return Result.success(list);
    }

    /** 删除好友 */
    @DeleteMapping("/{friendId}")
    public Result removeFriend(@PathVariable Integer friendId) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        friendshipService.removeFriend(currentUser.getId(), friendId);
        return Result.success("已删除");
    }

    /** 查询是否已是好友 */
    @GetMapping("/status/{friendId}")
    public Result isFriend(@PathVariable Integer friendId) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        boolean r = friendshipService.isFriend(currentUser.getId(), friendId);
        return Result.success(r);
    }

    /** 发送好友申请 */
    @PostMapping("/request")
    public Result sendRequest(@RequestBody Map<String, Object> body) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        Integer toUserId = body == null ? null : (Integer) body.get("toUserId");
        if (toUserId == null) return Result.error("400", "参数错误");
        String result = friendshipService.sendFriendRequest(currentUser.getId(), toUserId);
        if ("sent".equals(result))    return Result.success("申请已发送，等待对方同意");
        if ("already".equals(result)) return Result.error("400", "已经是好友了");
        if ("pending".equals(result)) return Result.error("400", "已发送过申请，请等待对方处理");
        if ("self".equals(result))    return Result.error("400", "不能添加自己");
        return Result.error("500", "发送失败");
    }

    /** 获取我收到的待处理申请 */
    @GetMapping("/requests")
    public Result getIncomingRequests() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        return Result.success(friendshipService.getIncomingRequests(currentUser.getId()));
    }

    /** 同意好友申请 */
    @PostMapping("/requests/{id}/accept")
    public Result acceptRequest(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        boolean ok = friendshipService.acceptRequest(id, currentUser.getId());
        return ok ? Result.success("已同意，对方已成为你的好友") : Result.error("400", "操作失败");
    }

    /** 拒绝好友申请 */
    @PostMapping("/requests/{id}/reject")
    public Result rejectRequest(@PathVariable Integer id) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        boolean ok = friendshipService.rejectRequest(id, currentUser.getId());
        return ok ? Result.success("已拒绝") : Result.error("400", "操作失败");
    }
}

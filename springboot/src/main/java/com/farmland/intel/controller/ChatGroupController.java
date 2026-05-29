package com.farmland.intel.controller;

import com.farmland.intel.common.Result;
import com.farmland.intel.entity.ChatGroup;
import com.farmland.intel.entity.ChatGroupMember;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IChatGroupService;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat-group")
public class ChatGroupController {

    @Resource
    private IChatGroupService chatGroupService;

    @PostMapping
    public Result createGroup(@RequestBody Map<String, Object> body) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        List<Integer> memberIds = (List<Integer>) body.get("memberIds");
        if (name == null || name.trim().isEmpty()) {
            return Result.error("400", "群组名称不能为空");
        }
        ChatGroup group = chatGroupService.createGroup(name.trim(), currentUser.getId(), memberIds);
        return Result.success(group);
    }

    @GetMapping
    public Result getUserGroups() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        List<Map<String, Object>> groups = chatGroupService.getUserGroups(currentUser.getId());
        return Result.success(groups);
    }

    @GetMapping("/{id}/members")
    public Result getGroupMembers(@PathVariable Integer id) {
        List<ChatGroupMember> members = chatGroupService.getGroupMembers(id);
        return Result.success(members);
    }

    @PostMapping("/{id}/members")
    public Result addMembers(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        // 仅群主或管理员可添加成员
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        ChatGroup group = chatGroupService.getById(id);
        if (group == null) return Result.error("404", "群组不存在");
        boolean isOwner = currentUser.getId().equals(group.getOwnerId());
        boolean isAdmin = "ROLE_ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            return Result.error("403", "仅群主或管理员可添加成员");
        }

        @SuppressWarnings("unchecked")
        List<Integer> userIds = (List<Integer>) body.get("userIds");
        if (userIds == null || userIds.isEmpty()) {
            return Result.error("400", "请选择要添加的成员");
        }
        chatGroupService.addMembers(id, userIds);
        return Result.success();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result removeMember(@PathVariable Integer id, @PathVariable Integer userId) {
        // 仅群主或管理员可移除成员
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        ChatGroup group = chatGroupService.getById(id);
        if (group == null) return Result.error("404", "群组不存在");
        boolean isOwner = currentUser.getId().equals(group.getOwnerId());
        boolean isAdmin = "ROLE_ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            return Result.error("403", "仅群主或管理员可移除成员");
        }

        chatGroupService.removeMember(id, userId);
        return Result.success();
    }

    @GetMapping("/{id}/non-members")
    public Result getNonMembers(@PathVariable Integer id) {
        List<Map<String, Object>> users = chatGroupService.getNonMembers(id);
        return Result.success(users);
    }

    /** 通过群号搜索群组 */
    @GetMapping("/search")
    public Result searchByGroupNumber(@RequestParam String groupNumber) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        ChatGroup group = chatGroupService.searchByGroupNumber(groupNumber);
        if (group == null) return Result.error("404", "未找到该群聊，请确认群号是否正确");
        // 是否已在群里
        List<ChatGroupMember> members = chatGroupService.getGroupMembers(group.getId());
        boolean joined = members.stream().anyMatch(m -> m.getUserId().equals(currentUser.getId()));
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("group", group);
        resp.put("memberCount", members.size());
        resp.put("joined", joined);
        return Result.success(resp);
    }

    /** 通过群号加群 */
    @PostMapping("/join")
    public Result joinGroup(@RequestBody Map<String, Object> body) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) return Result.error("401", "未登录");
        String groupNumber = body == null ? null : (String) body.get("groupNumber");
        if (groupNumber == null || groupNumber.trim().isEmpty()) return Result.error("400", "群号不能为空");
        boolean joined = chatGroupService.joinGroupByNumber(groupNumber.trim(), currentUser.getId());
        if (!joined) {
            // 可能是已在群里，也可能是群不存在
            ChatGroup group = chatGroupService.searchByGroupNumber(groupNumber.trim());
            if (group == null) return Result.error("404", "群聊不存在");
            return Result.error("400", "您已在该群聊中");
        }
        return Result.success("加群成功");
    }
}

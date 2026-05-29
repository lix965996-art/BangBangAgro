package com.farmland.intel.controller;

import com.farmland.intel.common.Result;
import com.farmland.intel.entity.ChatGroupMember;
import com.farmland.intel.entity.ChatMessage;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IChatGroupService;
import com.farmland.intel.service.IChatMessageService;
import com.farmland.intel.utils.TokenUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat-message")
public class ChatMessageController {

    @Resource
    private IChatMessageService chatMessageService;

    @Resource
    private IChatGroupService chatGroupService;

    @GetMapping("/private/{userId}")
    public Result getPrivateMessages(@PathVariable Integer userId,
                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                     @RequestParam(defaultValue = "50") Integer pageSize) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        List<ChatMessage> messages = chatMessageService.getPrivateMessages(currentUser.getId(), userId, pageNum, pageSize);
        return Result.success(messages);
    }

    @GetMapping("/group/{groupId}")
    public Result getGroupMessages(@PathVariable Integer groupId,
                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                   @RequestParam(defaultValue = "50") Integer pageSize) {
        // 校验当前用户是否为群成员
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        List<ChatGroupMember> members = chatGroupService.getGroupMembers(groupId);
        boolean isMember = members.stream().anyMatch(m -> m.getUserId().equals(currentUser.getId()));
        if (!isMember) {
            return Result.error("403", "无权限查看该群消息");
        }
        List<ChatMessage> messages = chatMessageService.getGroupMessages(groupId, pageNum, pageSize);
        return Result.success(messages);
    }

    @GetMapping("/contacts")
    public Result getContacts() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        List<Map<String, Object>> contacts = chatMessageService.getContacts(currentUser.getId());
        return Result.success(contacts);
    }

    @GetMapping("/unread")
    public Result getUnreadCount() {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        int count = chatMessageService.getUnreadCount(currentUser.getId());
        return Result.success(count);
    }

    @PutMapping("/read/{userId}")
    public Result markPrivateRead(@PathVariable Integer userId) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        chatMessageService.markPrivateRead(userId, currentUser.getId());
        return Result.success();
    }

    @PutMapping("/read-group/{groupId}")
    public Result markGroupRead(@PathVariable Integer groupId) {
        User currentUser = TokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("401", "未登录");
        }
        chatMessageService.markGroupRead(groupId, currentUser.getId());
        return Result.success();
    }

    @GetMapping("/users")
    public Result getAllUsers() {
        List<User> users = chatMessageService.getAllUsersForChat();
        return Result.success(users);
    }
}

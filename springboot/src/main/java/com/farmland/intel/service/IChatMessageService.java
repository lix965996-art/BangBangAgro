package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.ChatMessage;
import com.farmland.intel.entity.User;

import java.util.List;
import java.util.Map;

public interface IChatMessageService extends IService<ChatMessage> {

    /**
     * 获取与指定用户的私聊历史（分页）
     */
    List<ChatMessage> getPrivateMessages(Integer userId1, Integer userId2, int pageNum, int pageSize);

    /**
     * 获取群聊历史（分页）
     */
    List<ChatMessage> getGroupMessages(Integer groupId, int pageNum, int pageSize);

    /**
     * 获取最近联系人列表（包含最后一条消息和未读数）
     */
    List<Map<String, Object>> getContacts(Integer userId);

    /**
     * 获取未读消息总数
     */
    int getUnreadCount(Integer userId);

    /**
     * 标记私聊消息为已读
     */
    void markPrivateRead(Integer fromUserId, Integer toUserId);

    /**
     * 标记群聊消息为已读
     */
    void markGroupRead(Integer groupId, Integer userId);

    /**
     * 发送并保存消息
     */
    ChatMessage sendMessage(ChatMessage message);

    /**
     * 获取所有用户列表（用于联系人选择）
     */
    List<User> getAllUsersForChat();
}

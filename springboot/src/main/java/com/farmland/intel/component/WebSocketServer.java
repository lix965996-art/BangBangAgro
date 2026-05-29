package com.farmland.intel.component;

import org.springframework.stereotype.Component;

import jakarta.websocket.server.ServerEndpoint;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.farmland.intel.entity.ChatGroupMember;
import com.farmland.intel.entity.ChatMessage;
import com.farmland.intel.entity.User;
import com.farmland.intel.service.IChatGroupService;
import com.farmland.intel.service.IChatMessageService;
import com.farmland.intel.service.IUserService;
import com.farmland.intel.utils.SpringContextHolder;
import com.farmland.intel.utils.TokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint(value = "/imserver/{username}")
@Component
public class WebSocketServer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    // 最大 WebSocket 连接数
    private static final int MAX_CONNECTIONS = 200;

    public static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    // 用户ID到用户名的映射，方便群聊时查找
    public static final Map<String, Integer> userOnlineIds = new ConcurrentHashMap<>();

    // 已认证的会话集合（key = username）
    private static final Set<String> authenticatedSessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(Session session, @PathParam("username") String username) {
        // 连接数限制，防止 DoS 攻击
        if (sessionMap.size() >= MAX_CONNECTIONS) {
            log.warn("WebSocket连接数已达上限({})，拒绝连接: {}", MAX_CONNECTIONS, username);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.TRY_AGAIN_LATER, "服务器连接数已满"));
            } catch (IOException e) {
                log.error("关闭超限连接失败", e);
            }
            return;
        }
        // 等待客户端发送 auth 消息进行认证
        log.info("WebSocket连接建立，等待认证，username={}", username);
    }

    @OnMessage
    public void onMessage(String message, Session session, @PathParam("username") String username) {
        JSONObject obj = JSONUtil.parseObj(message);

        // 处理认证消息
        if ("auth".equals(obj.getStr("type"))) {
            String token = obj.getStr("token");
            if (token == null || !TokenUtils.validateWebSocketToken(token, username)) {
                log.warn("WebSocket认证失败，username={}", username);
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "认证失败"));
                } catch (IOException e) {
                    log.error("关闭未认证WebSocket连接失败", e);
                }
                return;
            }

            // 认证通过，注册会话
            authenticatedSessions.add(username);

            // 关闭同一用户名的旧连接
            Session oldSession = sessionMap.get(username);
            if (oldSession != null && oldSession.isOpen()) {
                try {
                    oldSession.close();
                } catch (Exception e) {
                    log.warn("关闭旧WebSocket连接失败: {}", e.getMessage());
                }
            }
            sessionMap.put(username, session);

            // 记录在线用户ID
            try {
                IUserService userService = SpringContextHolder.getBean(IUserService.class);
                User user = userService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>().eq("username", username));
                if (user != null) {
                    userOnlineIds.put(username, user.getId());
                }
            } catch (Exception e) {
                log.warn("获取用户ID失败: {}", e.getMessage());
            }

            log.info("用户认证成功，username={}, 当前在线人数为：{}", username, sessionMap.size());

            // 广播在线用户列表
            broadcastOnlineUsers();
            return;
        }

        // 未认证的会话不允许发送业务消息
        if (!authenticatedSessions.contains(username)) {
            log.warn("未认证的WebSocket消息，username={}", username);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "未认证"));
            } catch (IOException e) {
                log.error("关闭未认证连接失败", e);
            }
            return;
        }

        log.info("收到用户username={}的消息:{}", username, message);

        try {
            // 获取发送用户信息
            IUserService userService = SpringContextHolder.getBean(IUserService.class);
            User sender = userService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>().eq("username", username));

            if (sender == null) {
                log.warn("未找到发送用户: {}", username);
                return;
            }

            ChatMessage chatMsg = new ChatMessage();
            chatMsg.setFromUserId(sender.getId());
            chatMsg.setFromUsername(sender.getUsername());
            chatMsg.setFromAvatar(sender.getAvatarUrl());
            chatMsg.setContent(obj.getStr("text"));

            String msgType = obj.getStr("type", "private");
            if ("group".equals(msgType)) {
                handleGroupMessage(obj, chatMsg, sender);
            } else {
                handlePrivateMessage(obj, chatMsg, username);
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
            JSONObject errorResp = new JSONObject();
            errorResp.set("type", "error");
            errorResp.set("message", "消息发送失败");
            sendMessage(JSONUtil.toJsonStr(errorResp), session);
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("username") String username) {
        sessionMap.remove(username);
        userOnlineIds.remove(username);
        authenticatedSessions.remove(username);
        log.info("连接关闭，移除username={}, 当前在线人数为：{}", username, sessionMap.size());

        broadcastOnlineUsers();
    }

    @OnError
    public void onError(Session session, @PathParam("username") String username, Throwable error) {
        log.error("WebSocket发生错误，username={}", username, error);
        if (username != null && sessionMap.containsKey(username)) {
            sessionMap.remove(username);
            userOnlineIds.remove(username);
            authenticatedSessions.remove(username);
            log.info("错误处理：已移除username={}的session，当前在线人数：{}", username, sessionMap.size());
        }
    }

    private void broadcastOnlineUsers() {
        JSONObject result = new JSONObject();
        JSONArray array = new JSONArray();
        result.set("type", "online_users");
        result.set("users", array);
        for (Object key : sessionMap.keySet()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("username", key);
            array.add(jsonObject);
        }
        sendAllMessage(JSONUtil.toJsonStr(result));
    }

    private void handlePrivateMessage(JSONObject obj, ChatMessage chatMsg, String fromUsername) {
        String toUsername = obj.getStr("to");
        if (toUsername == null || toUsername.isEmpty()) {
            log.warn("私聊消息缺少接收人");
            return;
        }

        // 查找目标用户ID
        IUserService userService = SpringContextHolder.getBean(IUserService.class);
        User targetUser = userService.getOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>().eq("username", toUsername));
        if (targetUser != null) {
            chatMsg.setToUserId(targetUser.getId());
        }

        // 消息入库
        IChatMessageService msgService = SpringContextHolder.getBean(IChatMessageService.class);
        chatMsg = msgService.sendMessage(chatMsg);

        // 构建响应
        JSONObject resp = buildMessageResponse(chatMsg, "private");
        String respStr = JSONUtil.toJsonStr(resp);

        // 转发给接收人
        Session toSession = sessionMap.get(toUsername);
        if (toSession != null) {
            sendMessage(respStr, toSession);
        }

        // 也发回给发送人（确认消息已送达）
        sendMessage(respStr, sessionMap.get(fromUsername));
    }

    private void handleGroupMessage(JSONObject obj, ChatMessage chatMsg, User sender) {
        Integer groupId = obj.getInt("toGroup");
        if (groupId == null) {
            log.warn("群聊消息缺少群组ID");
            return;
        }
        chatMsg.setToGroupId(groupId);

        // 消息入库
        IChatMessageService msgService = SpringContextHolder.getBean(IChatMessageService.class);
        chatMsg = msgService.sendMessage(chatMsg);

        // 获取群成员
        IChatGroupService groupService = SpringContextHolder.getBean(IChatGroupService.class);
        List<ChatGroupMember> members = groupService.getGroupMembers(groupId);

        // 构建响应
        JSONObject resp = buildMessageResponse(chatMsg, "group");
        resp.set("groupId", groupId);
        String respStr = JSONUtil.toJsonStr(resp);

        // 推送给所有在线群成员（除了自己）
        IUserService userService = SpringContextHolder.getBean(IUserService.class);
        for (ChatGroupMember member : members) {
            User memberUser = userService.getById(member.getUserId());
            if (memberUser != null && !memberUser.getUsername().equals(sender.getUsername())) {
                Session memberSession = sessionMap.get(memberUser.getUsername());
                if (memberSession != null) {
                    sendMessage(respStr, memberSession);
                }
            }
        }

        // 发回给发送人确认
        Session senderSession = sessionMap.get(sender.getUsername());
        if (senderSession != null) {
            sendMessage(respStr, senderSession);
        }
    }

    private JSONObject buildMessageResponse(ChatMessage msg, String type) {
        JSONObject resp = new JSONObject();
        resp.set("type", type);
        resp.set("id", msg.getId());
        resp.set("fromUserId", msg.getFromUserId());
        resp.set("fromUsername", msg.getFromUsername());
        resp.set("fromAvatar", msg.getFromAvatar() != null ? msg.getFromAvatar() : "");
        resp.set("text", msg.getContent());
        resp.set("toUserId", msg.getToUserId());
        resp.set("toGroupId", msg.getToGroupId());
        resp.set("time", msg.getCreateTime() != null
            ? msg.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return resp;
    }

    private void sendMessage(String message, Session toSession) {
        try {
            if (toSession != null && toSession.isOpen()) {
                toSession.getBasicRemote().sendText(message);
            }
        } catch (Exception e) {
            log.error("服务端发送消息给客户端失败", e);
        }
    }

    private void sendAllMessage(String message) {
        for (Session session : sessionMap.values()) {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (Exception e) {
                log.error("服务端广播消息失败", e);
            }
        }
    }
}

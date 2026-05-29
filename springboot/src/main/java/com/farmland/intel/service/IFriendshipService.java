package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.Friendship;
import com.farmland.intel.entity.User;

import java.util.List;
import java.util.Map;

public interface IFriendshipService extends IService<Friendship> {

    /** 通过 UID 搜索用户（不含当前用户自己） */
    User searchByUid(String uid, Integer currentUserId);

    /**
     * 添加好友（双向写入）
     * @return "added" 新增成功 | "already" 已是好友
     */
    String addFriend(Integer userId, Integer friendId);

    /** 删除好友（双向删除） */
    void removeFriend(Integer userId, Integer friendId);

    /** 判断两人是否已是好友 */
    boolean isFriend(Integer userId, Integer friendId);

    /** 获取好友列表（带最后消息预览） */
    List<Map<String, Object>> getFriendList(Integer userId);

    /** 发送好友申请：sent/already/pending/self */
    String sendFriendRequest(Integer fromUserId, Integer toUserId);

    /** 获取我收到的待处理申请列表（含申请人信息） */
    List<Map<String, Object>> getIncomingRequests(Integer userId);

    /** 同意申请 */
    boolean acceptRequest(Integer requestId, Integer currentUserId);

    /** 拒绝申请 */
    boolean rejectRequest(Integer requestId, Integer currentUserId);
}

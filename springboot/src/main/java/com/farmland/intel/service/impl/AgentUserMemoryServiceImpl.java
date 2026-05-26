package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.AgentUserMemory;
import com.farmland.intel.mapper.AgentUserMemoryMapper;
import com.farmland.intel.service.IAgentUserMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class AgentUserMemoryServiceImpl extends ServiceImpl<AgentUserMemoryMapper, AgentUserMemory>
        implements IAgentUserMemoryService {

    private static final int MAX_PREFERENCES = 4000;
    private static final int MAX_SUMMARY = 8000;
    private static final int SNIPPET_PREF_MAX = 1500;
    private static final int SNIPPET_SUM_MAX = 1500;
    private static final int PROMPT_SNIPPET_TOTAL = 3200;

    @Override
    public AgentUserMemory getOrCreate(Integer userId) {
        if (userId == null) {
            return null;
        }
        AgentUserMemory row = getOne(Wrappers.<AgentUserMemory>lambdaQuery()
                .eq(AgentUserMemory::getUserId, userId)
                .last("LIMIT 1"));
        if (row != null) {
            return row;
        }
        AgentUserMemory created = new AgentUserMemory();
        created.setUserId(userId);
        created.setPreferences("");
        created.setConversationSummary("");
        save(created);
        return created;
    }

    @Override
    public String buildPromptSnippet(Integer userId) {
        AgentUserMemory m = getOrCreate(userId);
        if (m == null) {
            return null;
        }
        String pref = m.getPreferences();
        String sum = m.getConversationSummary();
        if (!StringUtils.hasText(pref) && !StringUtils.hasText(sum)) {
            return null;
        }
        if (pref != null && pref.length() > SNIPPET_PREF_MAX) {
            pref = pref.substring(pref.length() - SNIPPET_PREF_MAX);
        }
        if (sum != null && sum.length() > SNIPPET_SUM_MAX) {
            sum = sum.substring(sum.length() - SNIPPET_SUM_MAX);
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(pref)) {
            sb.append("【用户偏好 / 显式记录】\n").append(pref.trim()).append("\n\n");
        }
        if (StringUtils.hasText(sum)) {
            sb.append("【近期对话滚动记录】\n").append(sum.trim());
        }
        String out = sb.toString().trim();
        if (out.length() > PROMPT_SNIPPET_TOTAL) {
            out = out.substring(out.length() - PROMPT_SNIPPET_TOTAL);
        }
        return out;
    }

    @Override
    public void appendConversationTurn(Integer userId, String question, String advice) {
        if (userId == null) {
            return;
        }
        String q = truncate(safe(question), 500);
        String a = truncate(safe(advice), 800);
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "] 问：" + q + " | 答：" + a + "\n";

        AgentUserMemory m = getOrCreate(userId);
        String cur = m.getConversationSummary() == null ? "" : m.getConversationSummary();
        String merged = cur + line;
        if (merged.length() > MAX_SUMMARY) {
            merged = merged.substring(merged.length() - MAX_SUMMARY);
        }
        m.setConversationSummary(merged);
        updateById(m);
    }

    @Override
    public void appendPreferenceNote(Integer userId, String note) {
        if (userId == null || !StringUtils.hasText(note)) {
            return;
        }
        String n = note.trim();
        AgentUserMemory m = getOrCreate(userId);
        String cur = m.getPreferences() == null ? "" : m.getPreferences();
        String merged = cur.isEmpty() ? n : (cur + "\n" + n);
        if (merged.length() > MAX_PREFERENCES) {
            merged = merged.substring(merged.length() - MAX_PREFERENCES);
        }
        m.setPreferences(merged);
        updateById(m);
    }

    @Override
    public void clearMemory(Integer userId, String scope) {
        if (userId == null) {
            return;
        }
        AgentUserMemory m = getOrCreate(userId);
        if (m == null) {
            return;
        }
        String s = scope == null ? "all" : scope.trim().toLowerCase(Locale.ROOT);
        if ("preferences".equals(s) || "preference".equals(s)) {
            m.setPreferences("");
        } else if ("summary".equals(s) || "conversation".equals(s) || "dialog".equals(s)) {
            m.setConversationSummary("");
        } else {
            m.setPreferences("");
            m.setConversationSummary("");
        }
        updateById(m);
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }
}

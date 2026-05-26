package com.bangbangagro.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.ChatRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val content: String)

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun sendMessage(question: String) {
        val text = question.trim()
        if (text.isBlank() || _loading.value) return
        _messages.value = _messages.value + ChatMessage("user", text)
        _loading.value = true

        viewModelScope.launch {
            try {
                val resp = RetrofitClient.api.chat(ChatRequest(text))
                val answer = if (resp.code == 200) resp.answer else "请求失败，请稍后再试。"
                _messages.value = _messages.value + ChatMessage("assistant", answer)
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage("assistant", "网络错误：${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }
}

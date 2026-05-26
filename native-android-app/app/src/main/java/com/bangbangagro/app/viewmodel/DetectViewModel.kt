package com.bangbangagro.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.FruitDetectResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class DetectViewModel : ViewModel() {

    private val _result = MutableStateFlow<FruitDetectResult?>(null)
    val result: StateFlow<FruitDetectResult?> = _result

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun detect(context: Context, uri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取图片")
                val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "image.jpg", requestBody)
                val resp = RetrofitClient.api.detectFruit(part)
                if (resp.isSuccess) {
                    _result.value = resp.data
                } else {
                    _error.value = resp.msg ?: "检测失败"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "网络错误"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clear() {
        _result.value = null
        _error.value = null
    }
}

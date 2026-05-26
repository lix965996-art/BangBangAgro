package com.bangbangagro.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.LoginRequest
import com.bangbangagro.app.data.TokenManager
import com.bangbangagro.app.data.UserDTO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenManager = TokenManager(app)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    init {
        RetrofitClient.init(tokenManager)
        viewModelScope.launch {
            tokenManager.token.collect { token ->
                _isLoggedIn.value = !token.isNullOrBlank()
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("请输入用户名和密码")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val response = RetrofitClient.api.login(LoginRequest(username.trim(), password))
                val user = response.data
                if (response.isSuccess && user != null && !user.token.isNullOrBlank()) {
                    tokenManager.saveAuth(
                        token = user.token,
                        username = user.username.ifBlank { username.trim() },
                        nickname = user.nickname.ifBlank { user.username.ifBlank { username.trim() } },
                        role = user.role.orEmpty()
                    )
                    _loginState.value = LoginState.Success(user)
                } else {
                    _loginState.value = LoginState.Error(response.msg ?: "登录失败")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "网络错误")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            tokenManager.clear()
            _loginState.value = LoginState.Idle
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
}

sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class Success(val user: UserDTO) : LoginState()
    data class Error(val message: String) : LoginState()
}

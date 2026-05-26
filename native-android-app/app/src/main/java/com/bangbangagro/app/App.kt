package com.bangbangagro.app

import android.app.Application
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.TokenManager

class App : Application() {
    lateinit var tokenManager: TokenManager

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        RetrofitClient.init(tokenManager)
    }
}

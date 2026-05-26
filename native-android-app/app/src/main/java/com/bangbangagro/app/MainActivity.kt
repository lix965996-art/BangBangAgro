package com.bangbangagro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.bangbangagro.app.navigation.NavGraph
import com.bangbangagro.app.navigation.Routes
import com.bangbangagro.app.ui.theme.BangBangAgroTheme
import com.bangbangagro.app.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as App
        val hasToken = runBlocking { app.tokenManager.token.firstOrNull()?.isNotBlank() == true }

        setContent {
            BangBangAgroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = viewModel()

                    LaunchedEffect(hasToken) {
                        if (hasToken) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    }

                    NavGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        startDestination = if (hasToken) Routes.HOME else Routes.LOGIN
                    )
                }
            }
        }
    }
}

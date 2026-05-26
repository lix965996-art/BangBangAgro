package com.bangbangagro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bangbangagro.app.App
import com.bangbangagro.app.BuildConfig
import com.bangbangagro.app.navigation.Routes
import com.bangbangagro.app.ui.components.AgroBottomBar
import com.bangbangagro.app.ui.theme.Green40
import com.bangbangagro.app.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = (context.applicationContext as App).tokenManager
    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        tokenManager.username.collectLatest { username = it.orEmpty() }
    }
    LaunchedEffect(Unit) {
        tokenManager.nickname.collectLatest { nickname = it.orEmpty() }
    }
    LaunchedEffect(Unit) {
        tokenManager.role.collectLatest { role = it.orEmpty() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green40,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            AgroBottomBar(currentRoute = Routes.PROFILE, onNavigate = onNavigate)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F8F5))
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(Green40),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(50.dp), tint = Color.White)
            }

            Spacer(Modifier.height(16.dp))
            Text(nickname.ifBlank { username.ifBlank { "用户" } }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(role.ifBlank { "普通用户" }, color = Color.Gray)

            Spacer(Modifier.height(28.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column {
                    ProfileMenuItem(Icons.Default.Person, "用户名", username)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(Icons.Default.Badge, "昵称", nickname)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(Icons.Default.Security, "角色", role)
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column {
                    ProfileMenuItem(Icons.Default.Security, "后端服务", BuildConfig.BASE_URL)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(Icons.Default.Badge, "网页地址", BuildConfig.WEB_URL)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(Icons.Default.Badge, "应用版本", BuildConfig.VERSION_NAME)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileMenuItem(Icons.Default.Person, "服务器", "47.98.173.125")
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    authViewModel.logout()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("退出登录", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Green40, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Text(value.ifBlank { "-" }, fontSize = 15.sp, color = Color.Gray)
    }
}

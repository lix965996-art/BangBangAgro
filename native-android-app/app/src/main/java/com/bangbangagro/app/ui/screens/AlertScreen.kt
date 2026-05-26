package com.bangbangagro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bangbangagro.app.data.FarmlandAlert
import com.bangbangagro.app.navigation.Routes
import com.bangbangagro.app.ui.components.AgroBottomBar
import com.bangbangagro.app.ui.theme.Green40
import com.bangbangagro.app.viewmodel.AlertViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: AlertViewModel = viewModel()
) {
    val alerts by viewModel.alerts.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预警中心") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { viewModel.loadAlerts() }) { Icon(Icons.Default.Refresh, "刷新") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green40,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            AgroBottomBar(currentRoute = Routes.ALERT, onNavigate = onNavigate)
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green40)
            }
            alerts.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF6F8F5))
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                    Text("暂无待处理预警", color = Color.Gray, modifier = Modifier.padding(top = 14.dp))
                }
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF6F8F5))
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("待处理预警", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("及时处理异常环境和设备消息", color = Color.Gray, fontSize = 13.sp)
                }
                items(alerts) { alert ->
                    AlertCard(alert = alert, onProcess = { viewModel.processAlert(alert.id) })
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: FarmlandAlert, onProcess: () -> Unit) {
    val levelColor = alertColor(alert.alertLevel)
    val typeIcon = when (alert.alertType) {
        "temperature" -> Icons.Default.Thermostat
        "soil_humidity" -> Icons.Default.WaterDrop
        "air_humidity" -> Icons.Default.Air
        "visual" -> Icons.Default.Visibility
        else -> Icons.Default.Warning
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(typeIcon, levelColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(alert.farmlandName.ifBlank { "农田预警" }, fontWeight = FontWeight.SemiBold)
                    Text(alertTypeLabel(alert.alertType), color = Color.Gray, fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = levelColor.copy(alpha = 0.12f)) {
                    Text(levelLabel(alert.alertLevel), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = levelColor, fontSize = 12.sp)
                }
            }
            Text(alert.message.ifBlank { "系统检测到一条预警，请及时查看。" }, modifier = Modifier.padding(top = 12.dp), fontSize = 14.sp)
            if (alert.suggestion.isNotBlank()) {
                Text("建议：${alert.suggestion}", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(alert.createTime.take(16), color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (alert.status == "pending") {
                    Button(
                        onClick = onProcess,
                        colors = ButtonDefaults.buttonColors(containerColor = Green40),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("处理", fontSize = 13.sp) }
                } else {
                    Text("已处理", color = Color(0xFF4CAF50), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
    }
}

private fun alertTypeLabel(type: String): String = when (type) {
    "temperature" -> "温度异常"
    "soil_humidity" -> "土壤湿度异常"
    "air_humidity" -> "空气湿度异常"
    "visual" -> "图像识别异常"
    else -> "环境异常"
}

private fun levelLabel(level: String): String = when (level.lowercase()) {
    "high", "danger" -> "紧急"
    "medium", "warning" -> "中等"
    else -> "低"
}

private fun alertColor(level: String): Color = when (level.lowercase()) {
    "high", "danger" -> Color(0xFFE53935)
    "medium", "warning" -> Color(0xFFFF9800)
    else -> Color(0xFF4CAF50)
}

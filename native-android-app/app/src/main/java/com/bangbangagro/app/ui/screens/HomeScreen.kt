package com.bangbangagro.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bangbangagro.app.data.FarmlandAlert
import com.bangbangagro.app.data.Statistic
import com.bangbangagro.app.navigation.Routes
import com.bangbangagro.app.ui.components.AgroBottomBar
import com.bangbangagro.app.ui.theme.Green40
import com.bangbangagro.app.viewmodel.HomeViewModel

private data class QuickAction(
    val icon: ImageVector,
    val label: String,
    val description: String,
    val route: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val summary by viewModel.summary.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    val todayAlerts by viewModel.todayAlerts.collectAsState()
    val loading by viewModel.loading.collectAsState()

    val quickActions = listOf(
        QuickAction(Icons.Default.DeviceThermostat, "环境监测", "实时温湿度", Routes.MONITOR, Color(0xFF0F766E)),
        QuickAction(Icons.Default.Grass, "农田管理", "地块与作物", Routes.FARMLAND, Color(0xFF2E7D32)),
        QuickAction(Icons.Default.CameraAlt, "AI 检测", "图像识别", Routes.DETECT, Color(0xFFB45309)),
        QuickAction(Icons.Default.SmartToy, "AI 助手", "农业问答", Routes.CHAT, Color(0xFF2563EB)),
        QuickAction(Icons.Default.Notifications, "预警中心", "异常处理", Routes.ALERT, Color(0xFFDC2626)),
        QuickAction(Icons.Default.Person, "个人中心", "账号与设置", Routes.PROFILE, Color(0xFF7C3AED))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("帮帮农", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("智慧农业管理平台", fontSize = 12.sp, color = Color.White.copy(alpha = 0.82f))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDashboard() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green40,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            AgroBottomBar(currentRoute = Routes.HOME, onNavigate = onNavigate)
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green40)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF6F8F5))
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroCard(
                    farmCount = summary?.farmCount ?: stats.size,
                    alertCount = todayAlerts.size,
                    online = deviceStatus?.online == true
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard(Modifier.weight(1f), "农田", "${summary?.farmCount ?: stats.size}", "块", Color(0xFF2E7D32))
                    SummaryCard(Modifier.weight(1f), "面积", formatNumber(summary?.totalArea ?: 0.0), "亩", Color(0xFF1976D2))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryCard(Modifier.weight(1f), "正常", "${summary?.normalCount ?: normalCount(stats)}", "块", Color(0xFFFF9800))
                    SummaryCard(Modifier.weight(1f), "库存", formatNumber(summary?.totalStock ?: 0.0), "件", Color(0xFF8E24AA))
                }
            }

            item {
                EnvironmentCard(stats = stats, online = deviceStatus?.online == true)
            }

            item {
                DeviceSnapshot(
                    temperature = deviceStatus?.temperature ?: stats.firstOrNull()?.temperature ?: 0.0,
                    humidity = deviceStatus?.humidity ?: stats.firstOrNull()?.airhumidity ?: 0.0,
                    led = deviceStatus?.led == 1,
                    fan = deviceStatus?.fan == true,
                    pump = deviceStatus?.pump == true
                )
            }

            item { SectionTitle("快捷入口") }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(quickActions) { action ->
                        QuickActionCard(action = action, onClick = { onNavigate(action.route) })
                    }
                }
            }

            item { SectionTitle("今日预警") }
            if (todayAlerts.isEmpty()) {
                item { EmptyCard("今日无待处理预警，系统运行平稳") }
            } else {
                items(todayAlerts.take(5)) { alert ->
                    AlertRow(alert = alert, onClick = { onNavigate(Routes.ALERT) })
                }
            }

            item { SectionTitle("农田概览") }
            if (stats.isEmpty()) {
                item { EmptyCard("暂无农田数据") }
            } else {
                items(stats.take(8)) { stat ->
                    FarmRow(stat = stat, onClick = { onNavigate(Routes.FARMLAND) })
                }
            }
        }
    }
}

@Composable
private fun HeroCard(farmCount: Int, alertCount: Int, online: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0B7A43), Color(0xFF37A169), Color(0xFF94C973))
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("智慧农业驾驶舱", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("云端数据已连接，农田、设备、预警统一管理", color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusChip("农田 $farmCount 块")
                    StatusChip(if (online) "设备在线" else "设备离线")
                    StatusChip("预警 $alertCount 条")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(18.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, title: String, value: String, unit: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = color, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun EnvironmentCard(stats: List<Statistic>, online: Boolean) {
    val temperature = stats.map { it.temperature }.filter { it > 0 }
    val humidity = stats.map { it.airhumidity }.filter { it > 0 }
    val light = stats.map { it.light }.filter { it > 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("环境趋势", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("根据最新农田传感数据生成", color = Color.Gray, fontSize = 12.sp)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (online) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Text(
                        if (online) "设备在线" else "等待设备",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (online) Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontSize = 12.sp
                    )
                }
            }

            TemperatureChart(values = temperature.ifEmpty { listOf(0.0, 0.0, 0.0) })

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                DeviceInfo("平均温度", "${formatNumber(temperature.averageOrZero())}℃")
                DeviceInfo("平均湿度", "${formatNumber(humidity.averageOrZero())}%")
                DeviceInfo("平均光照", formatNumber(light.averageOrZero()))
            }
        }
    }
}

@Composable
private fun TemperatureChart(values: List<Double>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 1.0
        val range = (max - min).takeIf { it > 0.01 } ?: 1.0
        val step = if (values.size > 1) size.width / (values.size - 1) else size.width

        for (i in 0..3) {
            val y = size.height / 3 * i
            drawLine(
                color = Color(0xFFE8EDE5),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) size.width / 2 else step * index
            val y = size.height - (((value - min) / range).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Green40,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun DeviceSnapshot(temperature: Double, humidity: Double, led: Boolean, fan: Boolean, pump: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("设备快照", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DeviceInfo("温度", "${formatNumber(temperature)}℃")
                DeviceInfo("湿度", "${formatNumber(humidity)}%")
                DeviceInfo("补光灯", if (led) "开启" else "关闭")
                DeviceInfo("风扇", if (fan) "开启" else "关闭")
                DeviceInfo("水泵", if (pump) "开启" else "关闭")
            }
        }
    }
}

@Composable
private fun DeviceInfo(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Green40)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color(0xFF1F2933))
}

@Composable
private fun QuickActionCard(action: QuickAction, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(action.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(action.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(action.description, color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AlertRow(alert: FarmlandAlert, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(alertColor(alert.alertLevel))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(alert.farmlandName.ifBlank { "农田预警" }, fontWeight = FontWeight.Medium)
                Text(alert.message.ifBlank { "请及时查看预警详情" }, color = Color.Gray, fontSize = 13.sp)
            }
            Text(alert.alertLevel.ifBlank { "info" }, color = alertColor(alert.alertLevel), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FarmRow(stat: Statistic, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Grass, contentDescription = null, tint = Green40, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stat.farm.ifBlank { "未命名农田" }, fontWeight = FontWeight.SemiBold)
                Text("${stat.area.ifBlank { "未知面积" }} | ${stat.crop.ifBlank { "未设置作物" }}", color = Color.Gray, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatNumber(stat.temperature)}℃", fontWeight = FontWeight.Medium)
                Text("湿度 ${formatNumber(stat.airhumidity)}%", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.Gray)
        }
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

private fun normalCount(stats: List<Statistic>): Int =
    stats.count { it.state.contains("正常") || it.state.equals("normal", ignoreCase = true) }

private fun formatNumber(value: Double): String =
    if (kotlin.math.abs(value - value.toInt()) < 0.01) value.toInt().toString() else String.format("%.1f", value)

private fun alertColor(level: String): Color = when (level.lowercase()) {
    "high", "danger", "严重" -> Color(0xFFE53935)
    "medium", "warning", "中" -> Color(0xFFFF9800)
    else -> Color(0xFF4CAF50)
}

package com.bangbangagro.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bangbangagro.app.data.Statistic
import com.bangbangagro.app.navigation.Routes
import com.bangbangagro.app.ui.components.AgroBottomBar
import com.bangbangagro.app.ui.theme.Green40
import com.bangbangagro.app.viewmodel.FarmlandViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmlandScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: FarmlandViewModel = viewModel()
) {
    val farmlands by viewModel.farmlands.collectAsState()
    val selected by viewModel.selectedFarmland.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "农田管理" else "农田详情") },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) viewModel.clearSelection() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = { IconButton(onClick = { viewModel.loadFarmlands() }) { Icon(Icons.Default.Refresh, "刷新") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green40,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            AgroBottomBar(currentRoute = Routes.FARMLAND, onNavigate = onNavigate)
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Green40)
            }
            selected != null -> FarmlandDetail(stat = selected!!, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF6F8F5))
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("农田列表", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("点击卡片查看环境数据、设备和负责人", color = Color.Gray, fontSize = 13.sp)
                }
                if (farmlands.isEmpty()) {
                    item {
                        EmptyState("暂无农田数据")
                    }
                }
                items(farmlands) { stat ->
                    FarmlandCard(stat = stat, onClick = { viewModel.selectFarmland(stat) })
                }
            }
        }
    }
}

@Composable
private fun FarmlandCard(stat: Statistic, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Grass, null, tint = Green40, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stat.farm.ifBlank { "未命名农田" }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("${stat.area.ifBlank { "未知面积" }} | ${stat.crop.ifBlank { "未设置作物" }}", color = Color.Gray, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Text(stat.address.ifBlank { "暂无地址" }, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(stat.state)
                Spacer(Modifier.height(6.dp))
                Text("${formatSensor(stat.temperature)}℃", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun FarmlandDetail(stat: Statistic, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8F5)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF0B7A43), Color(0xFF66BB6A))))
                        .padding(20.dp)
                ) {
                    Column {
                        Text(stat.farm.ifBlank { "未命名农田" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${stat.area.ifBlank { "未知面积" }} | ${stat.district.ifBlank { "未知区域" }}", color = Color.White.copy(alpha = 0.86f))
                        Text("作物：${stat.crop.ifBlank { "未设置" }}", color = Color.White.copy(alpha = 0.86f))
                    }
                }
            }
        }

        item { SectionTitle("环境数据") }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SensorItem("温度", "${formatSensor(stat.temperature)}℃", Color(0xFFE53935))
                        SensorItem("空气湿度", "${formatSensor(stat.airhumidity)}%", Color(0xFF1976D2))
                        SensorItem("土壤湿度", "${formatSensor(stat.soilhumidity)}%", Color(0xFF4CAF50))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SensorItem("CO2", "${formatSensor(stat.carbon)}ppm", Color(0xFF7B1FA2))
                        SensorItem("pH", formatSensor(stat.ph), Color(0xFFFF9800))
                        SensorItem("光照", "${formatSensor(stat.light)}lux", Color(0xFFFFC107))
                    }
                }
            }
        }

        item { SectionTitle("设备与负责人") }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    DeviceRow("监控", stat.monitor)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DeviceRow("补光灯", stat.filllight)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DeviceRow("水泵", stat.pump)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    DeviceRow("负责人", stat.keeper)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color(0xFF1F2933))
}

@Composable
private fun StatusBadge(state: String) {
    val normalized = state.ifBlank { "未知" }
    val ok = normalized == "正常" || normalized.equals("normal", ignoreCase = true)
    val color = if (ok) Color(0xFF2E7D32) else Color(0xFFE65100)
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
        Text(normalized, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 12.sp)
    }
}

@Composable
private fun SensorItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun DeviceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = Color.Gray)
        Text(value.ifBlank { "未配置" }, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(text: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.Gray)
        }
    }
}

private fun formatSensor(value: Double): String =
    if (kotlin.math.abs(value - value.toInt()) < 0.01) value.toInt().toString() else String.format("%.1f", value)

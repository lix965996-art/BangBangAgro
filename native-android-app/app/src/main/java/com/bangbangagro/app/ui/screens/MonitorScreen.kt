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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bangbangagro.app.navigation.Routes
import com.bangbangagro.app.ui.components.AgroBottomBar
import com.bangbangagro.app.ui.theme.Green40
import com.bangbangagro.app.viewmodel.MonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: MonitorViewModel = viewModel()
) {
    val deviceStatus by viewModel.deviceStatus.collectAsState()
    val weatherNow by viewModel.weatherNow.collectAsState()
    val dailyForecast by viewModel.dailyForecast.collectAsState()
    val hourlyForecast by viewModel.hourlyForecast.collectAsState()
    val controlMessage by viewModel.controlMessage.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(controlMessage) {
        controlMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("环境监测") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { viewModel.loadAll() }) { Icon(Icons.Default.Refresh, "刷新") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green40,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AgroBottomBar(currentRoute = Routes.MONITOR, onNavigate = onNavigate)
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF1565C0), Color(0xFF26A69A))))
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(weatherNow?.temp?.let { "$it℃" } ?: "--℃", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(weatherNow?.text ?: "实时天气加载中", fontSize = 18.sp, color = Color.White.copy(alpha = 0.88f))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                                WeatherChip("湿度", weatherNow?.humidity?.let { "$it%" } ?: "--")
                                WeatherChip("风向", weatherNow?.windDir ?: "--")
                                WeatherChip("风力", weatherNow?.windScale?.let { "${it}级" } ?: "--")
                            }
                        }
                    }
                }
            }

            if (hourlyForecast.isNotEmpty()) {
                item { SectionTitle("24 小时预报") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(hourlyForecast.take(12)) { hour ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(hour.fxTime.takeLast(5), fontSize = 12.sp, color = Color.Gray)
                                    Icon(Icons.Default.WbSunny, null, tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
                                    Text("${hour.temp}℃", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("设备控制") }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        ControlRow(Icons.Default.Lightbulb, "补光灯", deviceStatus?.led == 1) { viewModel.controlLed(it) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ControlRow(Icons.Default.Air, "风扇", deviceStatus?.fan == true) { viewModel.controlFan(it) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ControlRow(Icons.Default.WaterDrop, "水泵", deviceStatus?.pump == true) { viewModel.controlPump(it) }
                    }
                }
            }

            deviceStatus?.let { device ->
                item { SectionTitle("设备详情") }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.DeviceThermostat, null, tint = Green40)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(device.deviceName.ifBlank { "农业监测设备" }, fontWeight = FontWeight.SemiBold)
                                    Text(device.source.ifBlank { "云端数据" }, color = Color.Gray, fontSize = 12.sp)
                                }
                                StatusPill(if (device.online) "在线" else "离线", device.online)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                DetailItem("温度", "${device.temperature}℃")
                                DetailItem("湿度", "${device.humidity}%")
                                DetailItem("补光灯", if (device.led == 1) "开启" else "关闭")
                                DetailItem("风扇", if (device.fan) "开启" else "关闭")
                            }
                        }
                    }
                }
            }

            if (dailyForecast.isNotEmpty()) {
                item { SectionTitle("未来天气") }
                items(dailyForecast) { day ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(day.fxDate.takeLast(5), modifier = Modifier.width(58.dp), fontWeight = FontWeight.Medium)
                            Text(day.textDay, modifier = Modifier.weight(1f), color = Color.Gray)
                            Text("${day.tempMin}℃ / ${day.tempMax}℃", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherChip(label: String, value: String) {
    Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp)) {
        Text("$label $value", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color(0xFF1F2933))
}

@Composable
private fun ControlRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Green40)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Green40))
    }
}

@Composable
private fun StatusPill(text: String, online: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (online) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (online) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, color = Green40, fontSize = 14.sp)
        Text(label, color = Color.Gray, fontSize = 11.sp)
    }
}

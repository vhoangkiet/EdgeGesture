package com.edgegesture.evilgodxu

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgegesture.evilgodxu.data.LitePreferences
import com.edgegesture.evilgodxu.data.LiteSettings
import com.edgegesture.evilgodxu.service.EdgeGestureAccessibilityService
import com.edgegesture.evilgodxu.ui.theme.AppSwitch
import com.edgegesture.evilgodxu.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LitePreferences.init(this)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen(
                    onOpenAccessibilitySettings = {
                        try {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {}
                    }
                )
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (EdgeGestureAccessibilityService.isServiceRunning()) return true
    val expectedComponentName = "${context.packageName}/${EdgeGestureAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentName = colonSplitter.next()
        if (componentName.equals(expectedComponentName, ignoreCase = true)) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by LitePreferences.settingsFlow.collectAsStateWithLifecycle()

    var isServiceActive by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceActive = isAccessibilityServiceEnabled(context)
                EdgeGestureAccessibilityService.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "EdgeGesture Lite",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Android 10 • MIUI 12 • Không Root",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Trạng thái dịch vụ Accessibility
            item {
                ServiceStatusCard(
                    isActive = isServiceActive,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
            }

            // 2. Lưu ý MIUI 12
            item {
                MiuiNoticeCard()
            }

            // 3. Cạnh trái (Back)
            item {
                SectionCard(title = "Cạnh trái (Vuốt -> Back)") {
                    SwitchRow(
                        title = "Kích hoạt cử chỉ cạnh trái",
                        subtitle = "Vuốt từ cạnh trái vào trong để Back",
                        checked = settings.isLeftEnabled,
                        onCheckedChange = { checked ->
                            LitePreferences.update { it.copy(isLeftEnabled = checked) }
                        }
                    )
                    if (settings.isLeftEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SliderRow(
                            label = "Chiều cao vùng vuốt: ${settings.leftHeightPercent}%",
                            value = settings.leftHeightPercent.toFloat(),
                            valueRange = 20f..100f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(leftHeightPercent = it.toInt()) }
                            }
                        )
                        SliderRow(
                            label = "Vị trí vùng vuốt: ${settings.leftPositionPercent}%",
                            value = settings.leftPositionPercent.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(leftPositionPercent = it.toInt()) }
                            }
                        )
                        SliderRow(
                            label = "Độ rộng vùng vuốt: ${settings.leftWidthDp} dp",
                            value = settings.leftWidthDp.toFloat(),
                            valueRange = 12f..48f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(leftWidthDp = it.toInt()) }
                            }
                        )
                    }
                }
            }

            // 4. Cạnh phải (Back)
            item {
                SectionCard(title = "Cạnh phải (Vuốt -> Back)") {
                    SwitchRow(
                        title = "Kích hoạt cử chỉ cạnh phải",
                        subtitle = "Vuốt từ cạnh phải vào trong để Back",
                        checked = settings.isRightEnabled,
                        onCheckedChange = { checked ->
                            LitePreferences.update { it.copy(isRightEnabled = checked) }
                        }
                    )
                    if (settings.isRightEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SliderRow(
                            label = "Chiều cao vùng vuốt: ${settings.rightHeightPercent}%",
                            value = settings.rightHeightPercent.toFloat(),
                            valueRange = 20f..100f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(rightHeightPercent = it.toInt()) }
                            }
                        )
                        SliderRow(
                            label = "Vị trí vùng vuốt: ${settings.rightPositionPercent}%",
                            value = settings.rightPositionPercent.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(rightPositionPercent = it.toInt()) }
                            }
                        )
                        SliderRow(
                            label = "Độ rộng vùng vuốt: ${settings.rightWidthDp} dp",
                            value = settings.rightWidthDp.toFloat(),
                            valueRange = 12f..48f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(rightWidthDp = it.toInt()) }
                            }
                        )
                    }
                }
            }

            // 5. Cạnh dưới (Home & Recent Apps)
            item {
                SectionCard(title = "Cạnh dưới (Home & Recent Apps)") {
                    SwitchRow(
                        title = "Kích hoạt cử chỉ cạnh dưới",
                        subtitle = "Vuốt lên -> Home | Vuốt lên giữ -> Recent Apps",
                        checked = settings.isBottomEnabled,
                        onCheckedChange = { checked ->
                            LitePreferences.update { it.copy(isBottomEnabled = checked) }
                        }
                    )
                    if (settings.isBottomEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SliderRow(
                            label = "Độ rộng vùng vuốt đáy: ${settings.bottomWidthPercent}%",
                            value = settings.bottomWidthPercent.toFloat(),
                            valueRange = 30f..100f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(bottomWidthPercent = it.toInt()) }
                            }
                        )
                        SliderRow(
                            label = "Chiều cao vùng vuốt đáy: ${settings.bottomHeightDp} dp",
                            value = settings.bottomHeightDp.toFloat(),
                            valueRange = 14f..48f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(bottomHeightDp = it.toInt()) }
                            }
                        )
                    }
                }
            }

            // 6. Thanh Pixel Navigation Pill
            item {
                SectionCard(title = "Thanh Pill Pixel (Google Pixel Style)") {
                    SwitchRow(
                        title = "Hiển thị thanh Pill",
                        subtitle = "Thanh điều hướng bo góc ở đáy màn hình phong cách Pixel",
                        checked = settings.showPill,
                        onCheckedChange = { checked ->
                            LitePreferences.update { it.copy(showPill = checked) }
                        }
                    )
                    if (settings.showPill) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SliderRow(
                            label = "Chiều dài Pill: ${settings.pillWidthDp} dp",
                            value = settings.pillWidthDp.toFloat(),
                            valueRange = 40f..120f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(pillWidthDp = it.toInt()) }
                            }
                        )
                        SliderRow(
                            label = "Độ dày Pill: ${settings.pillHeightDp} dp",
                            value = settings.pillHeightDp.toFloat(),
                            valueRange = 3f..8f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(pillHeightDp = it.toInt()) }
                            }
                        )
                        SliderRow(
                            label = "Khoảng cách đáy: ${settings.pillBottomMarginDp} dp",
                            value = settings.pillBottomMarginDp.toFloat(),
                            valueRange = 2f..16f,
                            onValueChange = {
                                LitePreferences.update { s -> s.copy(pillBottomMarginDp = it.toInt()) }
                            }
                        )
                    }
                }
            }

            // 7. Tương thích Laban Key & Rung
            item {
                SectionCard(title = "Tương thích & Phản hồi") {
                    SwitchRow(
                        title = "Hiệu ứng vuốt (Animation)",
                        subtitle = "Hiển thị mũi tên giọt nước cạnh viền & độ nảy đàn hồi thanh Pill",
                        checked = settings.showSwipeAnimation,
                        onCheckedChange = { checked ->
                            LitePreferences.update { it.copy(showSwipeAnimation = checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SwitchRow(
                        title = "Tránh xung đột Laban Key",
                        subtitle = "Tự động tắt vùng vuốt đáy khi bàn phím mở để gõ phím Space mượt mà",
                        checked = settings.avoidKeyboard,
                        onCheckedChange = { checked ->
                            LitePreferences.update { it.copy(avoidKeyboard = checked) }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SwitchRow(
                        title = "Rung khi nhận cử chỉ",
                        subtitle = "Rung nhẹ khi kích hoạt Back, Home hoặc Recents",
                        checked = settings.vibrateOnTrigger,
                        onCheckedChange = { checked ->
                            LitePreferences.update { it.copy(vibrateOnTrigger = checked) }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ServiceStatusCard(
    isActive: Boolean,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isActive) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isActive) "Dịch vụ đang hoạt động" else "Dịch vụ hỗ trợ tiếp cận chưa bật",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color(0xFF1B5E20) else Color(0xFFBF360C)
                    )
                    Text(
                        text = if (isActive)
                            "Cử chỉ Back, Home, Recents và Pill đang chạy"
                        else
                            "Cần bật Dịch vụ Hỗ trợ Tiếp cận để cử chỉ hoạt động",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) Color(0xFF2E7D32) else Color(0xFFD84315)
                    )
                }
            }

            if (!isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Bật Dịch Vụ Tiếp Cận")
                }
            }
        }
    }
}

@Composable
fun MiuiNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Mẹo giữ chạy nền ổn định trên MIUI 12 (Redmi Note 8):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. Vào Cài đặt > Ứng dụng > Quản lý ứng dụng > EdgeGesture Lite > Bật 'Tự khởi chạy' (Autostart).\n2. Vào Tiết kiệm pin > Chọn 'Không giới hạn' (No restrictions).\n3. Trong Đa nhiệm, vuốt khóa ứng dụng để MIUI không dọn bộ nhớ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

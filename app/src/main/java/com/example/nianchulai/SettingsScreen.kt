package com.example.nianchulai

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun ChoiceChip(selected: Boolean, label: String, onClick: () -> Unit,
                        modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterChip(selected = selected, onClick = onClick, enabled = enabled, modifier = modifier,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, contentDescription = "已選取") }) else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary))
}

@Composable
internal fun SettingsScreen(settings: ThemeSettings, active: Boolean, remaining: Long,
                            setTimer: (Int?) -> Unit, sentenceFontSize: Int,
                            selectFontSize: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("顯示模式", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayMode.entries.forEach { mode ->
                ChoiceChip(settings.mode == mode, mode.label, { settings.selectMode(mode) })
            }
        }
        Text("主題色彩", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            themePresets.chunked(3).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (label, hue) ->
                        val isSelected = settings.hue == hue
                        Surface(onClick = { settings.selectHue(hue) },
                            modifier = Modifier.weight(1f).semantics { selected = isSelected },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.heightIn(min = 64.dp).padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(18.dp).clip(CircleShape).background(hueColor(hue)))
                                    if (isSelected) Icon(Icons.Default.Check, "已選取", Modifier.size(18.dp))
                                }
                                Text(label, style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
        Text("自訂色彩 · Hue ${settings.hue.toInt()}°", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(hueColor(settings.hue))
                .semantics { contentDescription = "目前自訂色彩，色相 ${settings.hue.toInt()} 度" })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(
                    Brush.horizontalGradient((0..6).map { hueColor(it * 60f) })))
                Slider(value = settings.hue, onValueChange = settings::selectHue, valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "自訂主題色相" },
                    colors = SliderDefaults.colors(activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent))
            }
        }
        Text("逐句朗讀字型大小", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(18 to "小字", 22 to "中字", 28 to "大字").forEach { (size, label) ->
                ChoiceChip(sentenceFontSize == size, label, { selectFontSize(size) })
            }
        }
        Text("睡眠計時器", style = MaterialTheme.typography.titleMedium)
        if (ReadingSession.timerEndsAt > 0 || ReadingSession.timerFinishing) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ReadingSession.timerFinishing) "時間已到，唸完目前句子後停止。"
                    else "剩餘 ${remaining / 60}:${String.format(Locale.US, "%02d", remaining % 60)}",
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { setTimer(null) }) { Text("取消") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60).forEach { minutes ->
                ChoiceChip(ReadingSession.timerEndsAt > 0 && ReadingSession.timerMinutes == minutes,
                    "$minutes 分", { setTimer(minutes) }, enabled = active)
            }
        }
        if (!active) Text("開始朗讀後即可設定睡眠計時。")
    }
}

package com.example.nianchulai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun CompactReadingSettings(mode: ReadingMode, speed: Float, select: (Float, ReadingMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip(mode == ReadingMode.CHINESE, "中文", { select(speed, ReadingMode.CHINESE) },
            modifier = Modifier.weight(1f))
        ChoiceChip(mode == ReadingMode.MIXED, "中英文", { select(speed, ReadingMode.MIXED) },
            modifier = Modifier.weight(1.15f))
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "朗讀語速 ${String.format(Locale.US, "%.1f", speed)} 倍，點擊選擇倍速"
                }) {
                Text("${String.format(Locale.US, "%.1f", speed)}×", style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                (5..15).forEach { step ->
                    val value = step / 10f
                    val selected = kotlin.math.abs(speed - value) < 0.01f
                    DropdownMenuItem(
                        text = { Text("${String.format(Locale.US, "%.1f", value)}×",
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) },
                        onClick = { expanded = false; select(value, mode) },
                        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, "已選取",
                            tint = MaterialTheme.colorScheme.onPrimary) }) else null,
                        modifier = if (selected) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                }
            }
        }
    }
}

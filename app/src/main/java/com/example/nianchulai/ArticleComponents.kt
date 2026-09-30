package com.example.nianchulai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow

@Composable
internal fun PageHeading(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}

@Composable
internal fun ArticleActionButton(onClick: () -> Unit, enabled: Boolean = true, primary: Boolean = false,
                                 content: @Composable () -> Unit) {
    // The visible circle is compact; its slot and minimum touch target remain 48 dp.
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        val icon: @Composable () -> Unit = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { content() }
        }
        if (primary) {
            FilledIconButton(onClick, modifier = Modifier.size(40.dp), enabled = enabled,
                shape = CircleShape, content = icon)
        } else {
            OutlinedIconButton(onClick, modifier = Modifier.size(40.dp), enabled = enabled,
                shape = CircleShape,
                border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)), content = icon)
        }
    }
}

@Composable
internal fun ArticlePlaybackIcon(title: String, action: ArticlePlaybackAction) {
    if (action == ArticlePlaybackAction.PAUSE) {
        Icon(painterResource(R.drawable.ic_paused), "暫停：$title")
    } else {
        Icon(Icons.Default.PlayArrow, if (action == ArticlePlaybackAction.RESUME) "繼續：$title" else "朗讀：$title")
    }
}

@Composable
internal fun ArticleHeading(title: String, showArticleIcon: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showArticleIcon) {
            Icon(painterResource(R.drawable.ic_article), "文章", modifier = Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontSize = 20.sp, lineHeight = 28.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun ArticleFormDialog(title: String, text: String, importedName: String?,
                               changeTitle: (String) -> Unit, changeText: (String) -> Unit,
                               save: () -> Unit, dismiss: () -> Unit, editing: Boolean = false) {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(if (editing) "編輯文章" else if (importedName == null) "新增文章" else "匯入 TXT 文章") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (importedName != null) Text("來源：$importedName")
                OutlinedTextField(title, changeTitle, label = { Text("標題（必填）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(text, changeText, label = { Text("內容（必填）") },
                    minLines = 5, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp))
                Text("標題與內容皆須填寫。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = title.isNotBlank() && text.isNotBlank(), onClick = save) { Text("儲存") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

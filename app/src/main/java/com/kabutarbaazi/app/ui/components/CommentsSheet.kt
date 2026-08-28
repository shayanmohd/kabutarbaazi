package com.kabutarbaazi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.domain.model.ReportReason

/**
 * Comments for both reels and community posts.
 *
 * The two live in separate tables (the polymorphic alternative would have cost the foreign key
 * and turned every RLS policy into a CASE), but they render identically, so the UI is shared and
 * callers flatten their rows into [CommentRow].
 */
data class CommentRow(val id: String, val username: String, val body: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    comments: List<CommentRow>,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onReportComment: (String, ReportReason, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf("") }
    var reportingId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                if (comments.isEmpty()) "Comments" else "${comments.size} comments",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (comments.isEmpty()) {
                Text(
                    "Pehla comment aap likhein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(comments, key = { it.id }) { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("@${c.username}", style = MaterialTheme.typography.labelLarge)
                                Text(c.body, style = MaterialTheme.typography.bodyMedium)
                            }
                            // Comments are user-generated content too, so each one is reportable.
                            IconButton(onClick = { reportingId = c.id }) {
                                Icon(
                                    Icons.Outlined.Flag,
                                    contentDescription = "Report this comment",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    placeholder = { Text("Add a comment") },
                    shape = PillShape,
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onSend(draft.trim()); draft = "" },
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }

    reportingId?.let { id ->
        ReportSheet(
            title = "Report this comment",
            onDismiss = { reportingId = null },
            onSubmit = { r, n -> reportingId = null; onReportComment(id, r, n) },
        )
    }
}

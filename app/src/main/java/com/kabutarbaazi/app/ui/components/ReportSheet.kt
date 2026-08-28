package com.kabutarbaazi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
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
 * Reporting is attached to every piece of user content and every profile.
 *
 * Google Play's UGC policy requires in-app reporting of both content and users for a public
 * social platform, and a reviewer will look for it specifically on the reels screen. The reasons
 * are not generic: animal cruelty and illegal sale exist because this is a live-animal
 * marketplace, and their presence is the clearest signal the moderation story was thought about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf<ReportReason?>(null) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                "Reports are reviewed. Content reported by enough people is hidden automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            ReportReason.entries.forEach { reason ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = reason }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == reason, onClick = { selected = reason })
                    Text(reasonLabel(reason), style = MaterialTheme.typography.bodyLarge)
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) note = it },
                label = { Text("Anything else? (optional)") },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Button(
                onClick = { selected?.let { onSubmit(it, note.ifBlank { null }) } },
                enabled = selected != null,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Send report") }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

private fun reasonLabel(r: ReportReason): String = when (r) {
    ReportReason.Spam -> "Spam or repeated posting"
    ReportReason.ScamOrFraud -> "Scam or fraud"
    ReportReason.AnimalCruelty -> "Cruelty to birds"
    ReportReason.NudityOrSexual -> "Nudity or sexual content"
    ReportReason.Violence -> "Violence"
    ReportReason.HateOrHarassment -> "Abuse or harassment"
    ReportReason.Impersonation -> "Pretending to be someone else"
    ReportReason.IllegalSale -> "Illegal or protected species"
    ReportReason.Other -> "Something else"
}

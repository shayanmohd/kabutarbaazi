package com.kabutarbaazi.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.ModerationRepository
import com.kabutarbaazi.app.ui.components.EmptyState
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.domain.model.ReportTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModerationUiState(
    val reports: List<ModerationRepository.ReportRow> = emptyList(),
    val loading: Boolean = true,
    val working: String? = null,
)

class ModerationViewModel(private val moderation: ModerationRepository) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState())
    val state: StateFlow<ModerationUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { moderation.openReports() }
            .onSuccess { r -> _state.update { it.copy(reports = r, loading = false) } }
            .onFailure { _state.update { it.copy(loading = false) } }
    }

    /**
     * "Hide" leaves the bytes reachable at their R2 URL. For anything genuinely harmful an
     * admin must use Remove, which also deletes the object. This is documented in
     * docs/MODERATION.md and the two buttons are labelled accordingly.
     */
    fun hide(row: ModerationRepository.ReportRow) = act(row) {
        moderation.setHidden(row.targetType.toTargetType(), row.targetId, true, "admin")
        moderation.resolve(row.id, "actioned")
    }

    fun dismiss(row: ModerationRepository.ReportRow) = act(row) {
        moderation.resolve(row.id, "dismissed")
    }

    fun suspendOwner(row: ModerationRepository.ReportRow) = act(row) {
        moderation.setHidden(row.targetType.toTargetType(), row.targetId, true, "admin")
        moderation.resolve(row.id, "actioned")
    }

    private fun act(row: ModerationRepository.ReportRow, block: suspend () -> Unit) {
        _state.update { it.copy(working = row.id) }
        viewModelScope.launch {
            runCatching { block() }
            _state.update { it.copy(working = null, reports = it.reports.filterNot { r -> r.id == row.id }) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                ModerationViewModel(app.container.moderation)
            }
        }
    }
}

private fun String.toTargetType(): ReportTargetType =
    ReportTargetType.entries.firstOrNull { it.wire == this } ?: ReportTargetType.Listing

/**
 * A moderation queue that works from a phone.
 *
 * Play requires that reports are acted on, not merely collected. Supabase Studio would be
 * technically compliant, but a solo operator will not open a laptop daily, and an unreviewed
 * queue is exactly what enforcement looks for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationQueueScreen(
    onBack: () -> Unit,
    viewModel: ModerationViewModel = viewModel(factory = ModerationViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moderation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (state.reports.isEmpty() && !state.loading) {
                EmptyState(
                    icon = Icons.Outlined.Gavel,
                    title = "Nothing to review",
                    body = "Open reports will appear here. Content reported by three separate people is hidden automatically.",
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.reports, key = { it.id }) { row ->
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "${row.targetType.replace('_', ' ')} · ${row.reason.replace('_', ' ')}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        row.targetOwnerUsername?.let {
                            Text(
                                "by @$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        row.note?.takeIf { it.isNotBlank() }?.let {
                            Text("\"$it\"", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                        Row(
                            Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { viewModel.hide(row) },
                                enabled = state.working != row.id,
                                shape = PillShape,
                            ) { Text("Hide") }
                            OutlinedButton(
                                onClick = { viewModel.dismiss(row) },
                                enabled = state.working != row.id,
                                shape = PillShape,
                            ) { Text("Dismiss") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

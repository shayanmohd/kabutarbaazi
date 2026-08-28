package com.kabutarbaazi.app.ui.screens.reels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.kabutarbaazi.app.ui.components.ErrorBanner
import com.kabutarbaazi.app.ui.theme.InnerShape
import com.kabutarbaazi.app.ui.theme.PillShape

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReelScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateReelViewModel = viewModel(factory = CreateReelViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.onVideoPicked(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New reel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .padding(top = 8.dp)
                    .clip(InnerShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (state.videoUri != null) {
                    AsyncImage(
                        model = state.videoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(34.dp))
                        Text(
                            "Video chunein",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "Up to 60 seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.caption,
                onValueChange = viewModel::onCaption,
                label = { Text("Caption (optional)") },
                shape = MaterialTheme.shapes.small,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            state.error?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(it, onRetry = viewModel::clearError)
            }

            if (state.submitting || state.stage != null) {
                Spacer(Modifier.height(14.dp))
                state.stage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Text(
                    "Video ko chhota kiya ja raha hai taki jaldi upload ho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Button(
                onClick = { viewModel.submit(context, onCreated) },
                enabled = state.canSubmit,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 18.dp),
            ) {
                Text(if (state.submitting) "Uploading..." else "Share reel")
            }
        }
    }
}

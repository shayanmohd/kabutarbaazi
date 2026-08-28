package com.kabutarbaazi.app.ui.screens.market

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kabutarbaazi.app.ui.components.ErrorBanner
import com.kabutarbaazi.app.ui.components.LtrBox
import com.kabutarbaazi.app.ui.theme.InnerShape
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.domain.media.MediaConstraints
import com.kabutarbaazi.domain.model.Breeds
import com.kabutarbaazi.domain.model.Regions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingScreen(
    language: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateListingViewModel = viewModel(factory = CreateListingViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var breedMenu by remember { mutableStateOf(false) }
    var regionMenu by remember { mutableStateOf(false) }

    // The Android Photo Picker needs no READ_MEDIA_* permission at all, which keeps the
    // manifest to INTERNET + POST_NOTIFICATIONS and makes the Data Safety form provable.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MediaConstraints.LISTING_MAX_IMAGES),
    ) { uris -> viewModel.onPhotosPicked(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post an ad") },
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Text("Photos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Up to ${MediaConstraints.LISTING_MAX_IMAGES}. The first one is the cover.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LtrBox {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 10.dp),
                ) {
                    items(state.photos) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(88.dp).clip(InnerShape),
                            )
                            IconButton(
                                onClick = { viewModel.removePhoto(uri) },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove photo",
                                    tint = MaterialTheme.colorScheme.surface,
                                )
                            }
                        }
                    }
                    if (state.photos.size < MediaConstraints.LISTING_MAX_IMAGES) {
                        item {
                            Box(
                                Modifier
                                    .size(88.dp)
                                    .clip(InnerShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        picker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.AddAPhoto, contentDescription = "Add photos")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Title") },
                placeholder = { Text("e.g. Teddy jodi, 6 months") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            // Prices stay left-to-right so Urdu does not scramble the digits.
            LtrBox {
                OutlinedTextField(
                    value = state.price,
                    onValueChange = viewModel::onPrice,
                    label = { Text("Price (₹)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Price negotiable", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.negotiable, onCheckedChange = viewModel::onNegotiable)
            }

            ExposedDropdownMenuBox(breedMenu, { breedMenu = it }) {
                OutlinedTextField(
                    value = state.breedId?.let { id ->
                        Breeds.ALL.getOrNull(id - 1)?.let { b ->
                            when (language) { "hi" -> b.nameHi; "ur" -> b.nameUr; else -> b.nameEn }
                        }
                    } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Breed") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(breedMenu) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(breedMenu, { breedMenu = false }) {
                    Breeds.ALL.forEachIndexed { i, b ->
                        DropdownMenuItem(
                            text = {
                                Text(when (language) { "hi" -> b.nameHi; "ur" -> b.nameUr; else -> b.nameEn })
                            },
                            onClick = { viewModel.onBreed(i + 1); breedMenu = false },
                        )
                    }
                }
            }
            if (Breeds.ALL.getOrNull((state.breedId ?: 0) - 1)?.slug == "other") {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.breedOther,
                    onValueChange = viewModel::onBreedOther,
                    label = { Text("Which breed?") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(regionMenu, { regionMenu = it }) {
                OutlinedTextField(
                    value = regionName(state.regionCode, language),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Area") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(regionMenu) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(regionMenu, { regionMenu = false }) {
                    Regions.ALL.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(regionName(r.code, language)) },
                            onClick = { viewModel.onRegion(r.code); regionMenu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.city,
                onValueChange = viewModel::onCity,
                label = { Text("City or mohalla (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text("Details (optional)") },
                placeholder = { Text("Age, colour, ring number, flying record") },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            state.blockedReason?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(it, onRetry = viewModel::clearError)
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(it, onRetry = viewModel::clearError)
            }

            if (state.submitting) {
                Spacer(Modifier.height(14.dp))
                state.progressLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }

            Button(
                onClick = { viewModel.submit(context, onCreated) },
                enabled = state.canSubmit,
                shape = PillShape,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 20.dp),
            ) {
                Text(if (state.submitting) "Posting..." else "Post ad")
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

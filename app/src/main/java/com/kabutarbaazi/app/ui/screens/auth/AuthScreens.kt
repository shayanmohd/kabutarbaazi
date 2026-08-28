package com.kabutarbaazi.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kabutarbaazi.app.R
import com.kabutarbaazi.app.ui.components.ErrorBanner
import com.kabutarbaazi.app.ui.components.LtrBox
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.domain.auth.PhoneNumber
import com.kabutarbaazi.domain.auth.UsernameError
import com.kabutarbaazi.domain.model.Regions

@Composable
private fun Brand() {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(
            "KabutarBaazi",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onGoToSignUp: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Brand()
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsername,
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            visible = showPassword,
            onToggleVisible = { showPassword = !showPassword },
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(it, onRetry = viewModel::clearError)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.signIn(onSignedIn) },
            enabled = state.canSignIn,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.log_in), style = MaterialTheme.typography.labelLarge)
            }
        }

        TextButton(
            onClick = onGoToSignUp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.new_here))
        }

        // Stated plainly rather than discovered. There is no email on file and no SMS in v1,
        // so a forgotten password genuinely cannot be reset yet.
        Text(
            stringResource(R.string.no_password_reset),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignedUp: () -> Unit,
    onGoToSignIn: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }
    var regionMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Brand()
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsername,
            label = { Text(stringResource(R.string.username)) },
            supportingText = {
                Text(state.usernameError?.let(::usernameErrorText) ?: "3 to 20 letters, numbers, dot or underscore")
            },
            isError = state.usernameError != null,
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayName,
            label = { Text(stringResource(R.string.your_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            visible = showPassword,
            onToggleVisible = { showPassword = !showPassword },
            supporting = "At least 6 characters",
        )
        Spacer(Modifier.height(8.dp))

        // Phone entry is forced left-to-right: under Urdu, a field mixing script and Latin
        // digits renders bidirectionally scrambled and the number becomes unreadable.
        LtrBox {
            Row(verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = state.dialCode,
                    onValueChange = viewModel::onDialCode,
                    label = { Text("Code") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.width(96.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.phoneLocal,
                    onValueChange = viewModel::onPhone,
                    label = { Text(stringResource(R.string.whatsapp_number)) },
                    supportingText = { Text(stringResource(R.string.phone_hint)) },
                    isError = state.phoneLocal.isNotBlank() &&
                        PhoneNumber.parse(state.dialCode, state.phoneLocal) == null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = regionMenuOpen,
            onExpandedChange = { regionMenuOpen = it },
        ) {
            OutlinedTextField(
                value = Regions.BY_CODE[state.regionCode]?.nameEn ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.your_area)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(regionMenuOpen) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(regionMenuOpen, { regionMenuOpen = false }) {
                Regions.ALL.forEach { region ->
                    DropdownMenuItem(
                        text = { Text(region.nameEn) },
                        onClick = {
                            viewModel.onRegion(region.code)
                            regionMenuOpen = false
                        },
                    )
                }
            }
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBanner(it, onRetry = viewModel::clearError)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.signUp(onSignedUp) },
            enabled = state.canSignUp,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.create_account), style = MaterialTheme.typography.labelLarge)
            }
        }
        TextButton(onClick = onGoToSignIn, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.have_account))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.password)) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun usernameErrorText(e: UsernameError): String = when (e) {
    UsernameError.Empty -> "Please choose a username"
    UsernameError.TooShort -> "At least 3 characters"
    UsernameError.TooLong -> "At most 20 characters"
    UsernameError.MustStartWithLetter -> "Must start with a letter"
    UsernameError.IllegalCharacters -> "Only letters, numbers, dot and underscore"
    UsernameError.AdjacentSeparators -> "No two dots or underscores together"
    UsernameError.TrailingSeparator -> "Cannot end with a dot or underscore"
    UsernameError.Reserved -> "That username is reserved"
}

package com.kabutarbaazi.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kabutarbaazi.app.BuildConfig
import com.kabutarbaazi.app.ui.screens.SetupRequiredScreen
import com.kabutarbaazi.app.ui.screens.auth.SignInScreen
import com.kabutarbaazi.app.ui.screens.auth.SignUpScreen
import com.kabutarbaazi.app.ui.screens.terms.TermsGateScreen

/**
 * Three gates, in order: is there a session, has the user accepted the community rules, and
 * only then the app itself. The rules gate is not skippable, and the database enforces the same
 * condition on every content insert.
 */
@Composable
fun AppRoot() {
    // Checked before anything touches the network layer, so an unconfigured clone gets an
    // explanation rather than a lazy-init crash.
    if (BuildConfig.SUPABASE_URL.isBlank()) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            SetupRequiredScreen()
        }
        return
    }

    val viewModel: RootViewModel = viewModel(factory = RootViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSignUp by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            !state.signedIn -> if (showSignUp) {
                SignUpScreen(
                    onSignedUp = { viewModel.refresh() },
                    onGoToSignIn = { showSignUp = false },
                )
            } else {
                SignInScreen(
                    onSignedIn = { viewModel.refresh() },
                    onGoToSignUp = { showSignUp = true },
                )
            }

            !state.termsAccepted -> TermsGateScreen(
                onAccept = viewModel::acceptTerms,
                accepting = state.accepting,
            )

            else -> KabutarRoot(
                isAdmin = state.isAdmin,
                currentUserId = state.userId.orEmpty(),
                onSignOut = viewModel::signOut,
            )
        }
    }
}

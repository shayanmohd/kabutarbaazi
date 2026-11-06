package com.kabutarbaazi.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * A ViewModelStore for one screen instance, such as one listing, one chat or one signed-in user.
 *
 * Without it every screen reads the activity's store, where a ViewModel is keyed by class alone:
 * the second listing opened got the first listing's ViewModel and showed its data. The factory
 * and the creation extras (which carry the Application the factories read) still come from the
 * activity, so the screens' factories work unchanged.
 */
internal class ScopedViewModelStoreOwner(
    private val parent: HasDefaultViewModelProviderFactory,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = parent.defaultViewModelProviderFactory
    override val defaultViewModelCreationExtras: CreationExtras
        get() = parent.defaultViewModelCreationExtras
}

/**
 * Gives [content] its own ViewModels for as long as [scope] stays the same. When [scope] changes
 * or the content leaves the composition, those ViewModels are cleared, so a chat's live channel
 * closes and a form starts empty next time.
 */
@Composable
internal fun ScopedViewModels(scope: Any, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    key(scope) {
        val owner = remember { ScopedViewModelStoreOwner(parent as HasDefaultViewModelProviderFactory) }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
    }
}

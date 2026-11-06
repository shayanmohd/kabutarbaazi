package com.kabutarbaazi.app.ui

import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedViewModelStoreOwnerTest {

    /** Stands in for ListingDetailViewModel: its id comes from the factory, like the real one. */
    class ItemViewModel(val itemId: String, val app: Any?) : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    private val appKey = object : CreationExtras.Key<String> {}

    /** The activity: one store, plus the extras (the Application) the factories read. */
    private val activity = object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
        override val viewModelStore = ViewModelStore()
        override val defaultViewModelProviderFactory = ViewModelProvider.NewInstanceFactory()
        override val defaultViewModelCreationExtras: CreationExtras =
            MutableCreationExtras().apply { set(appKey, "the-app") }
    }

    private fun factory(id: String) = viewModelFactory { initializer { ItemViewModel(id, this[appKey]) } }

    private fun open(owner: ViewModelStoreOwner, id: String): ItemViewModel {
        val extras = (owner as HasDefaultViewModelProviderFactory).defaultViewModelCreationExtras
        return ViewModelProvider.create(owner, factory(id), extras)[ItemViewModel::class]
    }

    @Test
    fun `the shared activity store hands item B the ViewModel of item A`() {
        // The 1.0.2 behaviour, kept as a record of the bug: keyed by class alone.
        val a = open(activity, "A")
        val b = open(activity, "B")
        assertSame(a, b)
        assertEquals("A", b.itemId)
    }

    @Test
    fun `each route scope gets its own ViewModel for its own item`() {
        val a = open(ScopedViewModelStoreOwner(activity), "A")
        val b = open(ScopedViewModelStoreOwner(activity), "B")
        assertNotSame(a, b)
        assertEquals("A", a.itemId)
        assertEquals("B", b.itemId)
    }

    @Test
    fun `factories still receive the activity's extras`() {
        val vm = open(ScopedViewModelStoreOwner(activity), "A")
        assertEquals("the-app", vm.app)
    }

    @Test
    fun `leaving the route clears its ViewModels`() {
        val scope = ScopedViewModelStoreOwner(activity)
        val vm = open(scope, "A")
        scope.viewModelStore.clear()
        assertTrue(vm.cleared)
    }
}

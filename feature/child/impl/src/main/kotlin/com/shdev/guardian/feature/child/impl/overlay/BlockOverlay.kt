/*
 * Copyright 2026 NeroSH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shdev.guardian.feature.child.impl.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.material3.Button as ComposeButton
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Renders the "limit reached" screen as a WindowManager overlay (TYPE_APPLICATION_OVERLAY — the only
 * type permitted since API 26). Preferred over a full-screen Activity: no task-switch animation and
 * harder to dismiss. The overlay permission also exempts the app from Android 10+ background-activity
 * -start restrictions, which is what lets this background service raise UI at all.
 *
 * Must be touched only from the main thread.
 */
class BlockOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var reasonState by mutableStateOf("")
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    var targetPkg: String? = null
        private set

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)
    val isOverlayShown: Boolean
        get() = view != null && targetPkg != null

    fun show(reason: String, pkg: String?) {
        val canDrawOverlay = canDraw()
        if (!canDrawOverlay) return
        if (view != null) {
            updateReason(reason); return
        } // idempotent — ticks must not stack views

        reasonState = reason
        val root = buildComposeView(reason)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE,
        )

        targetPkg = pkg
        view = root
        windowManager.addView(root, params)
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner?.destroy()
        lifecycleOwner = null
        view = null
        targetPkg = null
    }

    /** Bounce the user out of the blocked app. Home can't be intercepted by the app being blocked. */
    fun goHome() {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    private fun updateReason(reason: String) {
        reasonState = reason
    }

    private fun buildComposeView(reason: String): View {
        reasonState = reason
        val owner = OverlayLifecycleOwner().apply { create() }
        lifecycleOwner = owner
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            fitsSystemWindows = false
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor(0xFF0D47A1))
                        .padding(64.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Time's up",
                        color = ComposeColor.White,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = reasonState,
                        color = ComposeColor(0xFFBBDEFB),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 24.dp, bottom = 48.dp),
                    )
                    ComposeButton(onClick = { hide() }) {
                        Text("Go home")
                    }
                }
            }
        }
    }

    /**
     * A ComposeView raised from a Service has no Activity tree, so it lacks the lifecycle /
     * saved-state / viewmodel owners that setContent requires. This supplies minimal ones held
     * RESUMED while the overlay is up and DESTROYED on hide.
     */
    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner,
        SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val viewModelStore = ViewModelStore()
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun create() {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
}

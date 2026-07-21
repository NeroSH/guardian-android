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
package com.shdev.guardian.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.shdev.guardian.core.navigation.Navigator
import com.shdev.guardian.core.navigation.rememberNavigationState
import com.shdev.guardian.core.navigation.toEntries
import com.shdev.guardian.data.auth.ParentSessionManager
import com.shdev.guardian.data.auth.ParentSessionStore
import com.shdev.guardian.data.config.DeviceRole
import com.shdev.guardian.data.config.RoleStore
import com.shdev.guardian.feature.child.api.ChildRoutes
import com.shdev.guardian.feature.child.impl.childEntries
import com.shdev.guardian.feature.parent.api.ParentRoutes
import com.shdev.guardian.feature.parent.impl.parentEntries
import com.shdev.guardian.ui.common.RoleSelectionScreen
import com.shdev.guardian.ui.navigation.Routes
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Single onboarding entry point (both roles). Role selection routes into the parent QR-render flow or
 * the child scanner flow. Enforcement / MonitorService is started after the child completes pairing
 * and the remaining permission steps (Phase 0 funnel — separate).
 */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super/setContentView so the system splash (Theme.Splash) is installed and
        // its rotating AVD shows on the cold-start critical path. Kept on screen until the first
        // Compose frame draws (default), which is GuardianSplash — a seamless icon handoff.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GuardianTheme { OnboardingNavHost() } }
    }
}

@Composable
internal fun OnboardingNavHost() {
    val roleStore = koinInject<RoleStore>()
    val parentSessionStore = koinInject<ParentSessionStore>()

    // Role and session live in encrypted DataStores (suspend reads) — resolve them before choosing
    // the start route.
    val startRoute by produceState<NavKey?>(initialValue = null) {
        value = when (roleStore.role()) {
            DeviceRole.CHILD -> ChildRoutes.Done

            // A parent who has authenticated stays in the parent flow until they explicitly log out.
            // Only the presence of tokens matters here, NOT their freshness: an expired access token
            // is refreshed silently by the parent client on the first call. Falling back to role
            // selection on expiry is exactly the bug this replaces.
            DeviceRole.PARENT ->
                if (parentSessionStore.accessToken() != null) ParentRoutes.Dashboard
                else Routes.Role

            DeviceRole.NONE -> Routes.Role
        }
    }

    // Fire reportFullyDrawn() once the role read completes, so macrobenchmark's timeToFullDisplay
    // measures the real startup gate (not just the initial splash frame).
    ReportDrawnWhen { startRoute != null }

    // Hold the in-app splash for a minimum beat so the entrance animation plays even when the role
    // read returns instantly (otherwise the splash would flash and vanish).
    var minTimePassed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1100.milliseconds)
        minTimePassed = true
    }
    val resolvedStartRoute = startRoute
    val ready = resolvedStartRoute != null && minTimePassed

    AnimatedContent(
        targetState = ready,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith
                    fadeOut(tween(300))
        },
        label = "splashToContent",
    ) { isReady ->
        if (!isReady || resolvedStartRoute == null) {
            GuardianSplash(Modifier.fillMaxSize())
        } else {
            OnboardingContent(startRoute = resolvedStartRoute)
        }
    }
}

@Composable
private fun OnboardingContent(startRoute: NavKey) {
    val navigationState = rememberNavigationState(
        startRoute = startRoute,
        topLevelRoutes = setOf(Routes.Role, ChildRoutes.Done, ParentRoutes.Dashboard),
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // The parent session ended — the token refresh failed, or the parent logged out. A plain 401
    // never lands here: the parent client refreshes and retries first.
    val parentSession = koinInject<ParentSessionManager>()
    LaunchedEffect(navigator) {
        parentSession.sessionExpired.collect { navigator.resetTo(Routes.Role) }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        val entryProvider = entryProvider {
            entry<Routes.Role> {
                RoleSelectionScreen(
                    onParent = { navigator.navigate(ParentRoutes.Pairing) },
                    onChild = { navigator.navigate(ChildRoutes.Scan) },
                    modifier = Modifier.padding(padding),
                )
            }
            parentEntries(
                // Promote the freshly authenticated parent into the dashboard, discarding the
                // onboarding stack so back can't re-enter role selection.
                onAuthenticated = { navigator.resetTo(ParentRoutes.Dashboard) },
                modifier = Modifier.padding(padding),
            )
            childEntries(
                onPaired = { navigator.navigate(ChildRoutes.Funnel) },
                onPermissionsGranted = { navigator.navigate(ChildRoutes.Done) },
                modifier = Modifier.padding(padding),
            )
        }

        NavDisplay(
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun GuardianTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
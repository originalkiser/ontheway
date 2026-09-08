package com.reststop.countdown

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.reststop.countdown.ui.MainViewModel
import com.reststop.countdown.ui.components.PermissionRequestScreen
import com.reststop.countdown.ui.screens.MapScreen
import com.reststop.countdown.ui.theme.RestStopCountdownTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as RestCountdownApplication
        MainViewModel.Factory(app.container.tripRepository, app.container.locationTracker)
    }

    private val requiredPermissions: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        // The Navigation SDK's ongoing turn-by-turn notification needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
        if (granted) requestNavigator()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.onPermissionResult(hasLocationPermission())
        if (!hasLocationPermission()) {
            requestLocationPermissions.launch(requiredPermissions)
        } else {
            requestNavigator()
        }

        setContent {
            RestStopCountdownTheme {
                AppRoot(
                    viewModel = viewModel,
                    onRequestPermission = { requestLocationPermissions.launch(requiredPermissions) },
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * One-time acquisition of the app-wide [Navigator] session. Also triggers Google's Navigation
     * Terms of Service dialog on its own the first time it's called, before [Navigator] is handed
     * back. If it fails (e.g. the Navigation SDK isn't enabled yet for this API key), the app
     * still works - the driving screen just won't have native turn-by-turn guidance.
     */
    private fun requestNavigator() {
        NavigationApi.getNavigator(
            this,
            object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(navigator: Navigator) {
                    viewModel.onNavigatorReady(navigator)
                }

                override fun onError(errorCode: Int) {
                    viewModel.onNavigatorUnavailable()
                }
            },
        )
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel, onRequestPermission: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.hasLocationPermission) {
        MapScreen(viewModel)
    } else {
        PermissionRequestScreen(onRequestPermission = onRequestPermission)
    }
}

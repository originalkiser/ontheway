package com.reststop.countdown

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reststop.countdown.ui.MainViewModel
import com.reststop.countdown.ui.components.PermissionRequestScreen
import com.reststop.countdown.ui.screens.MapScreen
import com.reststop.countdown.ui.theme.RestStopCountdownTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as RestCountdownApplication
        MainViewModel.Factory(app.container.tripRepository, app.container.locationTracker)
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.onPermissionResult(hasLocationPermission())
        if (!hasLocationPermission()) {
            requestLocationPermissions.launch(requiredPermissions)
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

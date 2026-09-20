package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.CadFolderScreen
import com.example.ui.screens.DxfViewerScreen
import com.example.ui.screens.HelpScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PrivacyPolicyScreen
import com.example.ui.screens.ProgramViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StlViewerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.util.FileTypeResolver

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())

        // Handle Intent if opened via WhatsApp or external file manager
        handleFileIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val pendingDestination by viewModel.pendingDestination.collectAsState()

                    // Automatically navigate to the corresponding viewer when opened from WhatsApp/Intent
                    LaunchedEffect(pendingDestination) {
                        pendingDestination?.let { destination ->
                            navController.navigate(destination) {
                                launchSingleTop = true
                            }
                            viewModel.consumePendingDestination()
                        }
                    }

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToProgramViewer = { navController.navigate("program_viewer") },
                                onNavigateToStlViewer = { navController.navigate("stl_viewer") },
                                onNavigateToDxfViewer = { navController.navigate("dxf_viewer") },
                                onNavigateToCadFolder = { navController.navigate("cad_folder") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToHelp = { navController.navigate("help") },
                                onNavigateToPrivacy = { navController.navigate("privacy") }
                            )
                        }
                        composable("cad_folder") {
                            CadFolderScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToDxfViewer = { navController.navigate("dxf_viewer") }
                            )
                        }
                        composable("program_viewer") {
                            ProgramViewerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("stl_viewer") {
                            StlViewerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("dxf_viewer") {
                            DxfViewerScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("help") {
                            HelpScreen(onBack = { navController.popBackStack() })
                        }
                        composable("privacy") {
                            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFileIntent(intent)
    }

    private fun handleFileIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val resolved = FileTypeResolver.resolveFromIntent(applicationContext, intent)
            if (resolved != null) {
                viewModel.openResolvedFile(resolved, autoNavigate = true)
            }
        }
    }
}

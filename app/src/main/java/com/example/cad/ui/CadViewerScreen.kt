package com.example.cad.ui

import androidx.compose.runtime.Composable
import com.example.ui.screens.DxfViewerScreen
import com.example.ui.viewmodel.MainViewModel

/**
 * Clean entry point for CAD View Pro Viewer screen.
 */
@Composable
fun CadViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    DxfViewerScreen(viewModel = viewModel, onBack = onBack)
}

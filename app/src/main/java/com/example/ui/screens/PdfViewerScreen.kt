package com.example.ui.screens

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.parser.PdfHelper
import com.example.ui.viewmodel.ActiveModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.FileTypeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeModel by viewModel.activeModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val pdfInfo = (activeModel as? ActiveModel.Pdf)?.info
    val totalPages = pdfInfo?.pageCount ?: 0

    // Continuous vertical scrolling list state
    val listState = rememberLazyListState()

    // Calculate current visible page index dynamically from vertical scroll position
    val currentVisiblePage by remember(pdfInfo, totalPages) {
        derivedStateOf {
            if (totalPages <= 0) 0
            else listState.firstVisibleItemIndex.coerceIn(0, totalPages - 1)
        }
    }

    // High-performance In-Memory LRU Cache for rendered PDF pages (avoids OOM on large PDFs)
    val pageCache = remember(pdfInfo?.localFilePath) {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKb = (maxMemoryKb / 8).coerceAtLeast(16 * 1024) // 16MB minimum
        object : LruCache<Int, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: Int, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    // Canvas Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Quick Jump Dialog state
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageSliderValue by remember { mutableFloatStateOf(1f) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val resolved = FileTypeResolver.resolve(context, it)
            viewModel.openResolvedFile(resolved, autoNavigate = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    val fileName = pdfInfo?.fileName ?: "PDF Document"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEF4444), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PDF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    val subtitleText = if (totalPages > 0) {
                        "Page ${currentVisiblePage + 1} of $totalPages • Continuous Vertical Scroll • Zero Gap"
                    } else {
                        "PDF Document Viewer"
                    }
                    Text(
                        text = subtitleText,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("pdf_back_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.testTag("pdf_open_btn")
                ) {
                    Icon(
                        Icons.Default.FileOpen,
                        contentDescription = "Open PDF",
                        tint = Color(0xFF38BDF8)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Opening PDF Document...", color = Color.White, fontSize = 13.sp)
                }
            }
        } else if (pdfInfo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No PDF Document Loaded",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Open any PDF drawing, work order or technical sheet from storage",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.loadSamplePdf() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Text("Load Sample Drawing", color = Color.White)
                        }
                        Button(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                        ) {
                            Text("Open PDF File", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Main Continuous Vertical PDF Viewer Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF020617))
            ) {
                // Interactive Zoom & Pan Viewport with zero margin
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1.0f, 4.0f)
                                scale = newScale
                                if (newScale > 1.05f) {
                                    val maxOffsetX = 800f * (newScale - 1f)
                                    val maxOffsetY = 1200f * (newScale - 1f)
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1.0f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.0f
                                    }
                                }
                            )
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                ) {
                    // LazyColumn: Continuous Up/Down Vertical Scrolling with ZERO Extra Margin
                    // Each page attaches directly to the previous one without gaps or borders
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("pdf_vertical_lazy_column"),
                        contentPadding = PaddingValues(0.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp) // Strictly 0 margin between pages!
                    ) {
                        items(
                            count = totalPages,
                            key = { pageIdx -> "${pdfInfo.localFilePath}_page_$pageIdx" }
                        ) { pageIndex ->
                            PdfPageItem(
                                filePath = pdfInfo.localFilePath,
                                pageIndex = pageIndex,
                                pageCache = pageCache,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_page_item_$pageIndex")
                            )
                        }
                    }
                }

                // Top Left Overlay: Continuous Scroll Mode & Zoom Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xDD0F172A))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ViewStream,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VERTICAL STREAM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xDD0F172A))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "ZOOM: ${(scale * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFFD700)
                        )
                    }
                }

                // Floating Zoom / Reset buttons (Bottom Right)
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE1E293B)),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                scale = (scale + 0.35f).coerceAtMost(4.0f)
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("pdf_zoom_in_btn")
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextScale = (scale - 0.35f).coerceAtLeast(1.0f)
                                scale = nextScale
                                if (nextScale <= 1.05f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("pdf_zoom_out_btn")
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                scale = 1.0f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("pdf_fit_screen_btn")
                        ) {
                            Icon(
                                Icons.Default.FitScreen,
                                contentDescription = "Fit to Width",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Bottom Center: Seamless Page Status Pill with Up/Down Smooth Navigation
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE0F172A)),
                    elevation = CardDefaults.cardElevation(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Scroll Up to Previous Page
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val target = (currentVisiblePage - 1).coerceAtLeast(0)
                                    listState.animateScrollToItem(target)
                                }
                            },
                            enabled = currentVisiblePage > 0,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("pdf_page_up_btn")
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Scroll Page Up",
                                tint = if (currentVisiblePage > 0) Color(0xFF00E5FF) else Color(0xFF475569)
                            )
                        }

                        // Page readout badge (Tappable to jump to specific page)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B))
                                .clickable {
                                    jumpPageSliderValue = (currentVisiblePage + 1).toFloat()
                                    showJumpDialog = true
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Page ${currentVisiblePage + 1} / $totalPages",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }

                        // Scroll Down to Next Page
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val target = (currentVisiblePage + 1).coerceAtMost(totalPages - 1)
                                    listState.animateScrollToItem(target)
                                }
                            },
                            enabled = currentVisiblePage < totalPages - 1,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("pdf_page_down_btn")
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll Page Down",
                                tint = if (currentVisiblePage < totalPages - 1) Color(0xFF00E5FF) else Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Jump to Page Dialog
    if (showJumpDialog && totalPages > 1) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "Jump to Page",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Page ${jumpPageSliderValue.toInt()} of $totalPages",
                        color = Color(0xFF00E5FF),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = jumpPageSliderValue,
                        onValueChange = { jumpPageSliderValue = it },
                        valueRange = 1f..totalPages.toFloat(),
                        steps = if (totalPages > 2) totalPages - 2 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetPage = (jumpPageSliderValue.toInt() - 1).coerceIn(0, totalPages - 1)
                        coroutineScope.launch {
                            listState.scrollToItem(targetPage)
                        }
                        showJumpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("Go to Page", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

/**
 * High-performance PDF Page Item.
 * Renders edge-to-edge at full width with ZERO margin or border,
 * allowing subsequent pages to be seamlessly connected one after another.
 */
@Composable
private fun PdfPageItem(
    filePath: String,
    pageIndex: Int,
    pageCache: LruCache<Int, Bitmap>,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(filePath, pageIndex) {
        mutableStateOf<Bitmap?>(pageCache.get(pageIndex))
    }
    var isLoading by remember(filePath, pageIndex) {
        mutableStateOf(bitmap == null)
    }

    LaunchedEffect(filePath, pageIndex) {
        if (bitmap == null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val cached = pageCache.get(pageIndex)
                if (cached != null) {
                    withContext(Dispatchers.Main) {
                        bitmap = cached
                        isLoading = false
                    }
                } else {
                    // Render at high resolution (1440px width) for ultra-clear technical blueprint clarity
                    val rendered = PdfHelper.renderPageToBitmap(filePath, pageIndex, targetWidth = 1440)
                    if (rendered != null) {
                        pageCache.put(pageIndex, rendered)
                    }
                    withContext(Dispatchers.Main) {
                        bitmap = rendered
                        isLoading = false
                    }
                }
            }
        }
    }

    val currentBmp = bitmap
    if (currentBmp != null) {
        // Crisp, zero-margin edge-to-edge page presentation
        Image(
            bitmap = currentBmp.asImageBitmap(),
            contentDescription = "PDF Page ${pageIndex + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = modifier
        )
    } else {
        // Standard A4 aspect ratio placeholder while rendering asynchronously
        Box(
            modifier = modifier
                .aspectRatio(1f / 1.4142f)
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFEF4444),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Rendering Page ${pageIndex + 1}...",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

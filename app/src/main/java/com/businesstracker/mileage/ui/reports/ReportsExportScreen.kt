package com.businesstracker.mileage.ui.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Route
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.businesstracker.mileage.ui.components.AppFloatingBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.businesstracker.mileage.ui.theme.SlateNeutral
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import com.businesstracker.mileage.ui.theme.PersonalBlue
import com.businesstracker.mileage.ui.theme.SucculentSecondary
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ReportsExportScreen(
    viewModel: ReportsExportViewModel = hiltViewModel(),
    onNavigateToTrips: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateReportDialog by rememberSaveable { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var showBusiness by rememberSaveable { mutableStateOf(true) }
    var showPersonal by rememberSaveable { mutableStateOf(true) }
    var selectedMonthIso by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val reportsListState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(state.exportErrorMessage) {
        state.exportErrorMessage?.let { errorMsg ->
            val result = snackbarHostState.showSnackbar(
                message = errorMsg,
                actionLabel = "Retry",
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                showCreateReportDialog = true
            }
            viewModel.clearExportError()
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pageBackground = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF1F4F9)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = reportsListState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (showCreateReportDialog || previewPdfFile != null) Modifier.blur(8.dp) else Modifier)
                .background(pageBackground),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reports",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Button(
                    onClick = { showCreateReportDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Create report",
                        modifier = Modifier.size(14.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Create report",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        item {
            AnnualOverviewSection(
                year = state.selectedYear,
                availableYears = state.availableYears,
                ytdLogged = state.totalDeduction,
                totalMiles = state.totalMiles,
                drivesCount = state.totalTrips,
                onSelectYear = viewModel::selectYear
            )
        }

        item {
            MonthlyMileageChartCard(
                monthlySummaries = state.monthlySummaries,
                showBusiness = showBusiness,
                showPersonal = showPersonal,
                selectedMonthIso = selectedMonthIso,
                onToggleBusiness = { showBusiness = !showBusiness },
                onTogglePersonal = { showPersonal = !showPersonal },
                onSelectMonth = { monthIso ->
                    selectedMonthIso = monthIso
                    detailsExpanded = true
                }
            )
        }

        item {
            MonthlyDetailsSection(
                year = state.selectedYear,
                summaries = state.monthlySummaries,
                expanded = detailsExpanded,
                selectedMonthIso = selectedMonthIso,
                onExpandedChange = { detailsExpanded = it },
                onSelectMonth = { selectedMonthIso = it },
                onExportMonth = { monthIso ->
                    viewModel.exportToCsv(monthIso) { intent -> context.startActivity(intent) }
                },
                onViewMonthDrives = { monthIso ->
                    onNavigateToTrips?.invoke(monthIso)
                }
            )
        }
    }

    if (showCreateReportDialog) {
        var selectedFormat by rememberSaveable { mutableStateOf("PDF") }

        AppFloatingBottomSheet(
            onDismissRequest = { showCreateReportDialog = false },
            title = "Create Yearly Report",
            onClose = { showCreateReportDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        // 1. Tax Year Selection (Inline with label)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tax Year",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SlateNeutral
                            )
                            CompactFilterMenu(
                                label = "${state.selectedYear} Tax Year",
                                options = state.availableYears.map { it to "$it tax year" },
                                onSelect = viewModel::selectYear
                            )
                        }

                        // 2. Drive Type (Business, Personal, All) Selection
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Drive Type",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SlateNeutral
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val driveTypes = listOf(
                                    "ALL" to "All",
                                    "BUSINESS" to "Business",
                                    "PERSONAL" to "Personal"
                                )
                                driveTypes.forEach { (typeKey, typeLabel) ->
                                    val isSelected = state.selectedClassification == typeKey
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.selectClassification(typeKey) }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = typeLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Format Selection (CSV, PDF)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Format",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SlateNeutral
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("PDF", "CSV").forEach { format ->
                                    val isSelected = selectedFormat == format
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedFormat = format }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = format,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val isBusy = state.isGeneratingPdf || state.isExporting

                        if (isBusy) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = SucculentPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (state.isGeneratingPdf) "Preparing PDF report..." else "Generating CSV...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Action buttons: Preview (for PDF) and Export
                            if (selectedFormat == "PDF") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.generatePdfFile(
                                                onGenerated = { file ->
                                                    showCreateReportDialog = false
                                                    previewPdfFile = file
                                                }
                                            )
                                        },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .height(44.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Visibility,
                                                contentDescription = "Preview",
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Preview",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.exportToPdf(
                                                onFileReady = { intent ->
                                                    showCreateReportDialog = false
                                                    context.startActivity(intent)
                                                }
                                            )
                                        },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(44.dp)
                                    ) {
                                        Text(
                                            text = "Share PDF",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.exportToCsv { intent ->
                                            showCreateReportDialog = false
                                            context.startActivity(intent)
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    Text(
                                        text = "Export CSV",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

    if (previewPdfFile != null) {
        val file = previewPdfFile!!
        var zoomScale by remember { mutableFloatStateOf(1f) }
        val pages = remember(file) {
            val bitmaps = mutableListOf<Bitmap>()
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Render page at high quality
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                android.util.Log.e("ReportsExportScreen", "Error rendering PDF preview: ${e.message}", e)
            }
            bitmaps
        }

        Dialog(
            onDismissRequest = { previewPdfFile = null },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { previewPdfFile = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.85f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PDF Preview (${pages.size} page${if (pages.size > 1) "s" else ""})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomOut,
                                        contentDescription = "Zoom out",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.0f) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Zoom in",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Business Mileage PDF Report")
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(shareIntent, "Share Mileage PDF").apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Share", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                }
                                IconButton(
                                    onClick = { previewPdfFile = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close preview",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                        if (pages.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Rendering PDF...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            zoomScale = (zoomScale * zoom).coerceIn(0.75f, 3.5f)
                                        }
                                    }
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = zoomScale,
                                            scaleY = zoomScale
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(pages.size) { index ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                        ) {
                                            Image(
                                                bitmap = pages[index].asImageBitmap(),
                                                contentDescription = "Page ${index + 1}",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun CompactFilterMenu(
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AnnualOverviewSection(
    year: String,
    availableYears: List<String>,
    ytdLogged: Double,
    totalMiles: Double,
    drivesCount: Int,
    onSelectYear: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF172536)
    val mutedText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val emeraldAccent = if (isDark) Color(0xFF43D99A) else Color(0xFF168354)
    val dividerColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
    val sageBg = if (isDark) SucculentPrimary.copy(alpha = 0.10f) else Color(0xFFEDF5F0)
    val selectorBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    var yearMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Annual overview",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = selectorBg,
                    border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { yearMenuExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = mutedText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = year,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Select year",
                            tint = mutedText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = yearMenuExpanded,
                    onDismissRequest = { yearMenuExpanded = false }
                ) {
                    availableYears.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y) },
                            onClick = {
                                onSelectYear(y)
                                yearMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Estimated annual tax deduction",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = mutedText
            )
            Text(
                text = "$${String.format(Locale.US, "%,.2f", ytdLogged)}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = emeraldAccent
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = sageBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReportMetricCell(
                    icon = Icons.Outlined.Route,
                    iconTint = emeraldAccent,
                    label = "Total miles",
                    value = String.format(Locale.US, "%,.1f", totalMiles),
                    valueColor = primaryText,
                    labelColor = mutedText,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(dividerColor.copy(alpha = 0.9f))
                )
                ReportMetricCell(
                    icon = Icons.Default.DirectionsCar,
                    iconTint = emeraldAccent,
                    label = "Trips",
                    value = "$drivesCount",
                    valueColor = primaryText,
                    labelColor = mutedText,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(dividerColor.copy(alpha = 0.9f))
                )
                ReportMetricCell(
                    icon = Icons.Default.CalendarMonth,
                    iconTint = emeraldAccent,
                    label = "Period",
                    value = year,
                    valueColor = primaryText,
                    labelColor = mutedText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReportMetricCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                maxLines = 1
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun MonthlyMileageChartCard(
    monthlySummaries: List<MonthSummary>,
    showBusiness: Boolean,
    showPersonal: Boolean,
    selectedMonthIso: String?,
    onToggleBusiness: () -> Unit,
    onTogglePersonal: () -> Unit,
    onSelectMonth: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val primaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF172536)
    val mutedText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val dividerColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
    val emeraldAccent = if (isDark) Color(0xFF43D99A) else Color(0xFF168354)
    val personalAccent = if (isDark) Color(0xFF75B9FF) else PersonalBlue

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly mileage",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Text(
                    text = "mi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = mutedText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MileageCategoryToggle(
                    label = "Business",
                    selected = showBusiness,
                    color = emeraldAccent,
                    mutedColor = mutedText,
                    onClick = onToggleBusiness
                )
                MileageCategoryToggle(
                    label = "Personal",
                    selected = showPersonal,
                    color = personalAccent,
                    mutedColor = mutedText,
                    onClick = onTogglePersonal
                )
            }

            val chartMonths = monthlySummaries.take(6).reversed()
            if (!showBusiness && !showPersonal) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Select a category to view mileage.",
                        fontSize = 12.sp,
                        color = mutedText
                    )
                }
            } else {
                MonthlyStackedMileageChart(
                    months = chartMonths,
                    showBusiness = showBusiness,
                    showPersonal = showPersonal,
                    selectedMonthIso = selectedMonthIso,
                    businessColor = emeraldAccent,
                    personalColor = personalAccent,
                    onSelectMonth = onSelectMonth
                )
                chartMonths.firstOrNull { it.monthIso == selectedMonthIso }?.let { selected ->
                    HorizontalDivider(color = dividerColor.copy(alpha = 0.7f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${selected.monthName} ${selected.year}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText
                            )
                            val detailText = buildString {
                                if (showBusiness) append("Business ${String.format(Locale.US, "%.1f", selected.businessMiles)} mi")
                                if (showBusiness && showPersonal) append(" · ")
                                if (showPersonal) append("Personal ${String.format(Locale.US, "%.1f", selected.personalMiles)} mi")
                            }
                            Text(
                                text = detailText,
                                fontSize = 12.sp,
                                color = mutedText
                            )
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", selected.loggedDeduction)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = emeraldAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyDetailsSection(
    year: String,
    summaries: List<MonthSummary>,
    expanded: Boolean,
    selectedMonthIso: String?,
    onExpandedChange: (Boolean) -> Unit,
    onSelectMonth: (String?) -> Unit,
    onExportMonth: (String) -> Unit,
    onViewMonthDrives: (String) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val primaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF172536)
    val mutedText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val dividerColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
    val emeraldAccent = if (isDark) Color(0xFF43D99A) else Color(0xFF168354)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "monthly_details_rotation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = emeraldAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = emeraldAccent,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly details",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText
                    )
                    Text(
                        text = if (expanded) "Tap a month for breakdown & export" else "${summaries.size} months in $year",
                        fontSize = 12.sp,
                        color = mutedText
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide monthly details" else "Show monthly details",
                    tint = mutedText,
                    modifier = Modifier.rotate(rotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = dividerColor.copy(alpha = 0.8f))
                    summaries.forEachIndexed { index, summary ->
                        if (index > 0) HorizontalDivider(color = dividerColor.copy(alpha = 0.6f))
                        CompactMonthRow(
                            summary = summary,
                            expanded = selectedMonthIso == summary.monthIso,
                            onToggle = { onSelectMonth(if (selectedMonthIso == summary.monthIso) null else summary.monthIso) },
                            onExportMonth = { onExportMonth(summary.monthIso) },
                            onViewMonthDrives = { onViewMonthDrives(summary.monthIso) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactMonthRow(
    summary: MonthSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExportMonth: () -> Unit,
    onViewMonthDrives: () -> Unit
) {
    val rowRequester = remember { BringIntoViewRequester() }
    androidx.compose.runtime.LaunchedEffect(expanded) {
        if (expanded) rowRequester.bringIntoView()
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(150),
        label = "month_rotation_${summary.monthIso}"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(rowRequester)
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        summary.monthName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summary.unreviewedDrives > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Unreviewed drives",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    "${summary.totalDrives} drives • ${String.format(Locale.US, "%.1f", summary.totalMiles)} mi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "\$${String.format(Locale.US, "%.2f", summary.loggedDeduction)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = SucculentPrimary
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val classifiedMiles = summary.businessMiles + summary.personalMiles
                val businessFraction = if (classifiedMiles > 0.0) (summary.businessMiles / classifiedMiles).toFloat().coerceIn(0f, 1f) else 0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (summary.businessMiles > 0.0) Box(Modifier.weight(businessFraction).fillMaxHeight().background(SucculentPrimary))
                    if (summary.businessMiles > 0.0 && summary.personalMiles > 0.0) Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White))
                    if (summary.personalMiles > 0.0) Box(Modifier.weight(1f - businessFraction).fillMaxHeight().background(PersonalBlue))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("● Business ${String.format(Locale.US, "%.1f", summary.businessMiles)} mi", color = SucculentPrimary, style = MaterialTheme.typography.bodySmall)
                    Text("● Personal ${String.format(Locale.US, "%.1f", summary.personalMiles)} mi", color = PersonalBlue, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportMonth,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Export CSV")
                    }
                    Button(
                        onClick = onViewMonthDrives,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SucculentPrimary)
                    ) {
                        Text("View drives")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthMetric(
    label: String,
    value: String,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

internal fun safeMiles(value: Double): Double = value.takeIf { it.isFinite() && it > 0.0 } ?: 0.0

internal fun visibleMiles(summary: MonthSummary, business: Boolean, personal: Boolean): Double =
    (if (business) safeMiles(summary.businessMiles) else 0.0) +
        (if (personal) safeMiles(summary.personalMiles) else 0.0)

internal fun mileageAxisMaximum(maxValue: Double): Double {
    if (!maxValue.isFinite() || maxValue <= 0.0) return 1.0
    val magnitude = 10.0.pow(kotlin.math.floor(log10(maxValue)))
    val step = magnitude / 2.0
    return (ceil(maxValue / step) * step).coerceAtLeast(1.0)
}

@Composable
private fun MileageCategoryToggle(
    label: String,
    selected: Boolean,
    color: Color,
    mutedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) color else mutedColor.copy(alpha = 0.4f))
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) color else mutedColor
            )
        }
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(2.dp)
                .background(if (selected) color else Color.Transparent)
        )
    }
}

@Composable
private fun MonthlyStackedMileageChart(
    months: List<MonthSummary>,
    showBusiness: Boolean,
    showPersonal: Boolean,
    selectedMonthIso: String?,
    businessColor: Color,
    personalColor: Color,
    onSelectMonth: (String) -> Unit
) {
    val maximum = mileageAxisMaximum(months.maxOfOrNull { visibleMiles(it, showBusiness, showPersonal) } ?: 0.0)
    val surfaceColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.surface else Color.White
    Row(Modifier.fillMaxWidth().height(148.dp)) {
        Column(Modifier.width(34.dp).height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("${String.format(Locale.US, "%.0f", maximum)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${String.format(Locale.US, "%.0f", maximum / 2)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                months.forEach { month ->
                    val business = if (showBusiness) safeMiles(month.businessMiles) else 0.0
                    val personal = if (showPersonal) safeMiles(month.personalMiles) else 0.0
                    val businessHeight = (120.0 * business / maximum).toFloat().coerceIn(0f, 120f).dp
                    val personalHeight = (120.0 * personal / maximum).toFloat().coerceIn(0f, 120f).dp
                    Column(Modifier.weight(1f).clickable { onSelectMonth(month.monthIso) },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.BottomCenter) {
                            Column(Modifier.width(20.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))) {
                                if (personal > 0.0) Box(Modifier.fillMaxWidth().height(personalHeight).background(personalColor))
                                if (business > 0.0 && personal > 0.0) Box(Modifier.fillMaxWidth().height(2.dp).background(surfaceColor))
                                if (business > 0.0) Box(Modifier.fillMaxWidth().height(businessHeight).background(businessColor))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = month.monthName.take(3),
                            fontSize = 11.sp,
                            color = if (month.monthIso == selectedMonthIso) businessColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (month.monthIso == selectedMonthIso) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

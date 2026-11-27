package com.businesstracker.mileage.ui.reports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.LocationOn
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
    var selectedReportTab by rememberSaveable { mutableStateOf(0) } // 0=Monthly, 1=By vehicle, 2=Saved reports
    var showAllMonths by rememberSaveable { mutableStateOf(false) }
    var showPeriodPicker by remember { mutableStateOf(false) }
    val reportsListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pageBackground = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF1F4F9)
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val primaryText = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF172536)
    val mutedText = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF64748B)
    val emeraldAccent = if (isDark) Color(0xFF43D99A) else Color(0xFF168354)
    val dividerColor = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = reportsListState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (showCreateReportDialog || previewPdfFile != null) Modifier.blur(8.dp) else Modifier)
                .background(pageBackground),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // 1. Header: "Reports" on left, compact year selector + single "Create report" button on right
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
                    color = primaryText
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showPeriodPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = mutedText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = state.selectedPeriodLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryText,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "Select reporting period",
                                tint = mutedText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

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
        }

        // 2. Mileage Report Summary Card (no duplicate button inside)
        item {
            AnnualMileageReportCard(
                periodLabel = state.selectedPeriodLabel,
                isFullYear = state.selectedMonths.isEmpty(),
                totalDeduction = state.totalDeduction,
                totalMiles = state.totalMiles,
                totalTrips = state.totalTrips,
                cardBg = cardBg,
                primaryText = primaryText,
                mutedText = mutedText,
                emeraldAccent = emeraldAccent,
                dividerColor = dividerColor
            )
        }

        // 3. Monthly Comparison Line Chart
        item {
            MonthlyComparisonLineChartCard(
                summaries = state.monthlySummaries,
                cardBg = cardBg,
                primaryText = primaryText,
                mutedText = mutedText,
                emeraldAccent = emeraldAccent,
                dividerColor = dividerColor
            )
        }

        // 4. Trip Review Section
        item {
            TripReviewCard(
                unclassifiedCount = state.unclassifiedCount,
                incompleteCount = state.incompleteCount,
                missingPurposeCount = state.missingPurposeCount,
                cardBg = cardBg,
                primaryText = primaryText,
                mutedText = mutedText,
                emeraldAccent = emeraldAccent,
                dividerColor = dividerColor,
                onReviewClick = { onNavigateToTrips?.invoke(state.selectedYear) }
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

        if (showPeriodPicker) {
            ReportingPeriodBottomSheet(
                initialYear = state.selectedYear,
                initialMonths = state.selectedMonths,
                availableYears = state.availableYears,
                onDismiss = { showPeriodPicker = false },
                onApply = { year, months ->
                    viewModel.setSelectedPeriod(year, months)
                    showPeriodPicker = false
                }
            )
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
private fun AnnualMileageReportCard(
    periodLabel: String,
    isFullYear: Boolean,
    totalDeduction: Double,
    totalMiles: Double,
    totalTrips: Int,
    cardBg: Color,
    primaryText: Color,
    mutedText: Color,
    emeraldAccent: Color,
    dividerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = emeraldAccent.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = emeraldAccent,
                        modifier = Modifier.padding(7.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (isFullYear) "Annual Mileage Report" else "Mileage Report",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText
                    )
                    Text(
                        text = if (isFullYear) "Jan 1 – Dec 31, $periodLabel" else periodLabel,
                        fontSize = 12.sp,
                        color = mutedText
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Est. tax deduction",
                        fontSize = 11.sp,
                        color = mutedText
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", totalDeduction)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = emeraldAccent
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(dividerColor.copy(alpha = 0.8f))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Total mileage",
                        fontSize = 11.sp,
                        color = mutedText
                    )
                    Text(
                        text = "${String.format(Locale.US, "%,.1f", totalMiles)} mi",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = primaryText
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(dividerColor.copy(alpha = 0.8f))
                )
                Column(
                    modifier = Modifier
                        .weight(0.75f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Total trips",
                        fontSize = 11.sp,
                        color = mutedText
                    )
                    Text(
                        text = "$totalTrips",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = primaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyComparisonLineChartCard(
    summaries: List<MonthSummary>,
    cardBg: Color,
    primaryText: Color,
    mutedText: Color,
    emeraldAccent: Color,
    dividerColor: Color
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val personalBlue = if (isDark) Color(0xFF75B9FF) else PersonalBlue
    var showBusiness by rememberSaveable { mutableStateOf(true) }
    var showPersonal by rememberSaveable { mutableStateOf(true) }
    var selectedMetric by rememberSaveable { mutableStateOf(0) } // 0=Mileage, 1=Est. deduction, 2=Trip count
    var metricMenuExpanded by remember { mutableStateOf(false) }
    var tappedMonthIso by rememberSaveable { mutableStateOf<String?>(null) }

    val metricLabels = listOf("Mileage", "Est. deduction", "Trip count")
    val chronological = remember(summaries) { summaries.sortedBy { it.monthIso } }
    val chartMonths = remember(chronological) {
        if (chronological.size > 6) chronological.takeLast(6) else chronological
    }

    fun seriesValue(m: MonthSummary, isBiz: Boolean): Double = when (selectedMetric) {
        0 -> if (isBiz) safeMiles(m.businessMiles) else safeMiles(m.personalMiles)
        1 -> if (isBiz) m.loggedDeduction.coerceAtLeast(0.0) else 0.0
        else -> if (isBiz) m.businessDrives.toDouble() else (m.totalDrives - m.businessDrives).coerceAtLeast(0).toDouble()
    }

    val maxVal = remember(chartMonths, showBusiness, showPersonal, selectedMetric) {
        val rawMax = chartMonths.maxOfOrNull { m ->
            maxOf(
                if (showBusiness) seriesValue(m, true) else 0.0,
                if (showPersonal) seriesValue(m, false) else 0.0
            )
        } ?: 0.0
        mileageAxisMaximum(rawMax)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly comparison",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { metricMenuExpanded = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = metricLabels[selectedMetric],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = mutedText
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Select metric",
                            tint = mutedText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = metricMenuExpanded,
                        onDismissRequest = { metricMenuExpanded = false }
                    ) {
                        metricLabels.forEachIndexed { idx, label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedMetric = idx
                                    metricMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showBusiness = !showBusiness }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (showBusiness) emeraldAccent else mutedText.copy(alpha = 0.3f))
                    )
                    Text(
                        text = "Business",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (showBusiness) primaryText else mutedText
                    )
                }

                if (selectedMetric != 1) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showPersonal = !showPersonal }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (showPersonal) personalBlue else mutedText.copy(alpha = 0.3f))
                        )
                        Text(
                            text = "Personal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (showPersonal) primaryText else mutedText
                        )
                    }
                }
            }

            if (chartMonths.isEmpty() || (!showBusiness && !showPersonal)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (chartMonths.isEmpty()) "No data for selected period." else "Select a series to display.",
                        fontSize = 12.sp,
                        color = mutedText
                    )
                }
            } else {
                val plotHeight = 124.dp
                Row(modifier = Modifier.fillMaxWidth().height(plotHeight + 22.dp)) {
                    Column(
                        modifier = Modifier.width(34.dp).height(plotHeight),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val topStr = if (selectedMetric == 1) "$${String.format(Locale.US, "%.0f", maxVal)}" else String.format(Locale.US, "%.0f", maxVal)
                        val midStr = if (selectedMetric == 1) "$${String.format(Locale.US, "%.0f", maxVal / 2)}" else String.format(Locale.US, "%.0f", maxVal / 2)
                        Text(topStr, fontSize = 10.sp, color = mutedText)
                        Text(midStr, fontSize = 10.sp, color = mutedText)
                        Text("0", fontSize = 10.sp, color = mutedText)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(plotHeight)
                        ) {
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            val gridColor = dividerColor.copy(alpha = 0.7f)
                            listOf(0f, size.height / 2f, size.height).forEach { y ->
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }

                            val count = chartMonths.size
                            val stepX = if (count > 1) size.width / (count - 1).coerceAtLeast(1) else size.width / 2f

                            fun drawSeries(isBiz: Boolean, lineColor: Color) {
                                var prevPoint: Offset? = null
                                chartMonths.forEachIndexed { idx, m ->
                                    if (m.totalDrives == 0) {
                                        prevPoint = null
                                        return@forEachIndexed
                                    }
                                    val v = seriesValue(m, isBiz)
                                    val x = if (count == 1) size.width / 2f else idx * stepX
                                    val y = (size.height * (1.0 - (v / maxVal).coerceIn(0.0, 1.0))).toFloat()
                                    val pt = Offset(x, y)
                                    prevPoint?.let { prev ->
                                        drawLine(
                                            color = lineColor,
                                            start = prev,
                                            end = pt,
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                    drawCircle(
                                        color = lineColor,
                                        radius = if (m.monthIso == tappedMonthIso) 4.5.dp.toPx() else 3.dp.toPx(),
                                        center = pt
                                    )
                                    prevPoint = pt
                                }
                            }

                            if (showPersonal && selectedMetric != 1) {
                                drawSeries(false, personalBlue)
                            }
                            if (showBusiness) {
                                drawSeries(true, emeraldAccent)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            chartMonths.forEach { m ->
                                Text(
                                    text = m.monthName.take(3),
                                    fontSize = 11.sp,
                                    fontWeight = if (m.monthIso == tappedMonthIso) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (m.monthIso == tappedMonthIso) emeraldAccent else mutedText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            tappedMonthIso = if (tappedMonthIso == m.monthIso) null else m.monthIso
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                chartMonths.firstOrNull { it.monthIso == tappedMonthIso }?.let { selected ->
                    val prevCalIso = runCatching {
                        java.time.LocalDate.parse("${selected.monthIso}-01").minusMonths(1)
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                    }.getOrNull()
                    val prevMonth = prevCalIso?.let { iso -> summaries.firstOrNull { it.monthIso == iso && it.totalDrives > 0 } }
                    val curVal = seriesValue(selected, true)
                    val momText = if (prevMonth != null) {
                        val prevVal = seriesValue(prevMonth, true)
                        val diff = curVal - prevVal
                        val sign = if (diff >= 0) "+" else ""
                        if (selectedMetric == 1) " ($sign$${String.format(Locale.US, "%.2f", diff)} vs ${prevMonth.monthName.take(3)})"
                        else if (selectedMetric == 0) " ($sign${String.format(Locale.US, "%.1f", diff)} mi vs ${prevMonth.monthName.take(3)})"
                        else " ($sign${diff.toInt()} vs ${prevMonth.monthName.take(3)})"
                    } else ""

                    HorizontalDivider(color = dividerColor.copy(alpha = 0.6f))
                    Text(
                        text = "${selected.monthName} ${selected.year}: " + when (selectedMetric) {
                            0 -> "Biz ${String.format(Locale.US, "%.1f", selected.businessMiles)} mi · Pers ${String.format(Locale.US, "%.1f", selected.personalMiles)} mi$momText"
                            1 -> "$${String.format(Locale.US, "%,.2f", selected.loggedDeduction)}$momText"
                            else -> "${selected.totalDrives} trips$momText"
                        },
                        fontSize = 12.sp,
                        color = primaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportingPeriodBottomSheet(
    initialYear: String,
    initialMonths: Set<String>,
    availableYears: List<String>,
    onDismiss: () -> Unit,
    onApply: (String, Set<String>) -> Unit
) {
    var draftYear by remember { mutableStateOf(initialYear) }
    var draftMonths by remember { mutableStateOf(initialMonths) }
    val yearInt = draftYear.toIntOrNull() ?: java.time.LocalDate.now().year
    val now = java.time.LocalDate.now()
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    AppFloatingBottomSheet(
        onDismissRequest = onDismiss,
        title = "Reporting period",
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { draftYear = (yearInt - 1).toString() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year")
                }
                Text(
                    text = draftYear,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { draftYear = (yearInt + 1).toString() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next year")
                }
            }

            // Quick presets (2x2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val maxM = if (yearInt == now.year) now.monthValue else 12
                            draftMonths = (1..maxM).map { String.format(Locale.US, "%s-%02d", draftYear, it) }.toSet()
                        },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Year to date", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            val endM = if (yearInt == now.year) now.monthValue else 12
                            val startM = (endM - 2).coerceAtLeast(1)
                            draftMonths = (startM..endM).map { String.format(Locale.US, "%s-%02d", draftYear, it) }.toSet()
                        },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Last 3 months", fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val endM = if (yearInt == now.year) now.monthValue else 12
                            val startM = (endM - 5).coerceAtLeast(1)
                            draftMonths = (startM..endM).map { String.format(Locale.US, "%s-%02d", draftYear, it) }.toSet()
                        },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Last 6 months", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { draftMonths = emptySet() },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("All year", fontSize = 12.sp) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select multiple months",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (draftMonths.isEmpty()) "All year" else "${draftMonths.size} selected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SucculentPrimary
                    )
                    Text(
                        text = "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { draftMonths = emptySet() }
                    )
                }
            }

            // 4x3 Month Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..3) {
                            val mIdx = row * 4 + col + 1
                            val iso = String.format(Locale.US, "%s-%02d", draftYear, mIdx)
                            val isSelected = iso in draftMonths
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SucculentPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        draftMonths = if (isSelected) draftMonths - iso else draftMonths + iso
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = monthNames[mIdx - 1],
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onApply(draftYear, draftMonths) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
            ) {
                val applyLabel = if (draftMonths.isEmpty()) "Apply · All $draftYear" else "Apply · ${draftMonths.size} months"
                Text(
                    text = applyLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@Composable
private fun TripReviewCard(
    unclassifiedCount: Int,
    incompleteCount: Int,
    missingPurposeCount: Int,
    cardBg: Color,
    primaryText: Color,
    mutedText: Color,
    emeraldAccent: Color,
    dividerColor: Color,
    onReviewClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trip review",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Text(
                    text = "Before exporting",
                    fontSize = 12.sp,
                    color = mutedText
                )
            }

            TripReviewRow(
                icon = Icons.Outlined.AssignmentTurnedIn,
                label = "Unclassified trips",
                count = unclassifiedCount,
                primaryText = primaryText,
                mutedText = mutedText,
                emeraldAccent = emeraldAccent,
                onClick = onReviewClick
            )
            HorizontalDivider(color = dividerColor.copy(alpha = 0.6f))
            TripReviewRow(
                icon = Icons.Outlined.LocationOn,
                label = "Incomplete trip records",
                count = incompleteCount,
                primaryText = primaryText,
                mutedText = mutedText,
                emeraldAccent = emeraldAccent,
                onClick = onReviewClick
            )
            HorizontalDivider(color = dividerColor.copy(alpha = 0.6f))
            TripReviewRow(
                icon = Icons.Outlined.BusinessCenter,
                label = "Missing business purpose",
                count = missingPurposeCount,
                primaryText = primaryText,
                mutedText = mutedText,
                emeraldAccent = emeraldAccent,
                onClick = onReviewClick
            )
        }
    }
}

@Composable
private fun TripReviewRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    primaryText: Color,
    mutedText: Color,
    emeraldAccent: Color,
    onClick: () -> Unit
) {
    val hasIssues = count > 0
    val badgeColor = if (hasIssues) Color(0xFFD97706) else emeraldAccent
    val badgeBg = if (hasIssues) Color(0xFFFEF3C7) else emeraldAccent.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = mutedText,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = primaryText,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = badgeBg
        ) {
            Text(
                text = "$count",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = badgeColor
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = mutedText,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ReportCenterTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) activeColor else inactiveColor
    Column(
        modifier = modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) activeColor else Color.Transparent)
        )
    }
}

@Composable
private fun MonthlyRecordsTableCard(
    year: String,
    summaries: List<MonthSummary>,
    showAllMonths: Boolean,
    onToggleShowAll: () -> Unit,
    onSelectMonth: (String) -> Unit,
    cardBg: Color,
    primaryText: Color,
    mutedText: Color,
    emeraldAccent: Color,
    dividerColor: Color
) {
    val visibleSummaries = if (showAllMonths) summaries else summaries.take(3)
    val personalBlue = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF75B9FF) else PersonalBlue

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly records",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                Text(
                    text = year,
                    fontSize = 12.sp,
                    color = mutedText
                )
            }

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Month", fontSize = 11.sp, color = mutedText, modifier = Modifier.weight(0.85f))
                Text("Business", fontSize = 11.sp, color = emeraldAccent, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
                Text("Personal", fontSize = 11.sp, color = personalBlue, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
                Text("Est. deduction", fontSize = 11.sp, color = mutedText, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1.1f))
                Spacer(modifier = Modifier.width(18.dp))
            }
            HorizontalDivider(color = dividerColor.copy(alpha = 0.8f))

            visibleSummaries.forEachIndexed { idx, s ->
                if (idx > 0) HorizontalDivider(color = dividerColor.copy(alpha = 0.5f))
                val hasData = s.totalDrives > 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectMonth(s.monthIso) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.monthName.take(3),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryText,
                        modifier = Modifier.weight(0.85f)
                    )
                    Text(
                        text = if (hasData) "${String.format(Locale.US, "%.1f", s.businessMiles)} mi" else "—",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                        color = if (hasData) primaryText else mutedText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (hasData) "${String.format(Locale.US, "%.1f", s.personalMiles)} mi" else "—",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                        color = if (hasData) primaryText else mutedText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (hasData) "$${String.format(Locale.US, "%,.2f", s.loggedDeduction)}" else "—",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        color = if (hasData && s.loggedDeduction > 0.0) emeraldAccent else mutedText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1.1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = mutedText,
                        modifier = Modifier.size(16.dp).padding(start = 2.dp)
                    )
                }
            }

            if (summaries.size > 3) {
                HorizontalDivider(color = dividerColor.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleShowAll)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAllMonths) "Show fewer months" else "View all months",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = emeraldAccent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (showAllMonths) Icons.Default.KeyboardArrowDown else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = emeraldAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleReportsCard(
    vehicles: List<VehicleReportSummary>,
    onSelectVehicle: (VehicleReportSummary) -> Unit,
    cardBg: Color,
    primaryText: Color,
    mutedText: Color,
    emeraldAccent: Color,
    dividerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "By vehicle",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            if (vehicles.isEmpty()) {
                Text(
                    text = "No vehicle trips recorded for this period.",
                    fontSize = 13.sp,
                    color = mutedText,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                vehicles.forEachIndexed { idx, v ->
                    if (idx > 0) HorizontalDivider(color = dividerColor.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectVehicle(v) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = v.vehicleName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryText
                            )
                            Text(
                                text = "${v.tripCount} trips · Biz ${String.format(Locale.US, "%.1f", v.businessMiles)} mi · Pers ${String.format(Locale.US, "%.1f", v.personalMiles)} mi",
                                fontSize = 12.sp,
                                color = mutedText
                            )
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", v.estimatedDeduction)}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (v.estimatedDeduction > 0.0) emeraldAccent else mutedText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = mutedText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedReportsCard(
    reports: List<SavedReportItem>,
    onOpenReport: (SavedReportItem) -> Unit,
    onShareReport: (SavedReportItem) -> Unit,
    cardBg: Color,
    primaryText: Color,
    mutedText: Color,
    emeraldAccent: Color,
    dividerColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, dividerColor.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Saved reports",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            if (reports.isEmpty()) {
                Text(
                    text = "Generated PDF and CSV reports will appear here.",
                    fontSize = 13.sp,
                    color = mutedText,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                reports.forEachIndexed { idx, item ->
                    if (idx > 0) HorizontalDivider(color = dividerColor.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenReport(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryText,
                                maxLines = 1
                            )
                            Text(
                                text = "${item.period} · ${item.createdDate} · ${item.format}",
                                fontSize = 12.sp,
                                color = mutedText
                            )
                        }
                        IconButton(
                            onClick = { onOpenReport(item) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Open report",
                                tint = emeraldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onShareReport(item) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share report",
                                tint = mutedText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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

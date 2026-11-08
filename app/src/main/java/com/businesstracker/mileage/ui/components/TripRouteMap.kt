package com.businesstracker.mileage.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.businesstracker.mileage.BuildConfig
import com.businesstracker.mileage.data.local.entity.TripEntity
import com.businesstracker.mileage.ui.theme.SucculentPrimary
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.INetworkAvailablityCheck
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private enum class TileStatus { LOADING, AVAILABLE, UNAVAILABLE }

@Composable
fun TripRouteMap(trip: TripEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val data = remember(trip.waypointsJson, trip.startLatitude, trip.startLongitude, trip.endLatitude, trip.endLongitude) {
        routeMapData(trip)
    }
    var status by remember(trip.id) { mutableStateOf(TileStatus.LOADING) }
    val background = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val map = remember(context, trip.id, data) {
        if (data.endpoints.isEmpty()) null else createRouteMap(context, data).apply {
            overlayManager.tilesOverlay.loadingBackgroundColor = background
            tileProvider.tileRequestCompleteHandlers.add(Handler(Looper.getMainLooper()) { message ->
                status = if (message.what == MapTileProviderBase.MAPTILE_SUCCESS_ID) TileStatus.AVAILABLE else TileStatus.UNAVAILABLE
                false
            })
        }
    }
    DisposableEffect(map, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) map?.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) map?.onPause()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map?.onResume()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            map?.onPause()
            map?.onDetach()
        }
    }
    LaunchedEffect(map) {
        status = TileStatus.LOADING
        delay(20_000)
        if (status == TileStatus.LOADING) status = TileStatus.UNAVAILABLE
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (map == null) {
            Text("Map unavailable: no recorded locations", Modifier.align(Alignment.Center).padding(16.dp))
        } else {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            if (status != TileStatus.AVAILABLE) {
                Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        if (status == TileStatus.LOADING) "Loading street map…" else "Map tiles unavailable. Check your internet connection.",
                        Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Surface(Modifier.align(Alignment.BottomEnd), color = androidx.compose.ui.graphics.Color.White) {
                Text(
                    "© OpenStreetMap contributors",
                    Modifier.clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
                    }.padding(4.dp), color = androidx.compose.ui.graphics.Color.DarkGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

internal fun createRouteMap(context: Context, data: RouteMapData, allowNetwork: Boolean = true): MapView {
    Configuration.getInstance().apply {
        userAgentValue = "${context.packageName}/${BuildConfig.VERSION_NAME}"
        osmdroidBasePath = File(context.cacheDir, "route-maps").apply { mkdirs() }
        osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
        tileFileSystemCacheMaxBytes = 64L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 48L * 1024 * 1024
        isDebugMode = false
        isDebugMapView = false
        isDebugTileProviders = false
        isDebugMapTileDownloader = false
    }
    val provider = object : MapTileProviderBasic(context, TileSourceFactory.MAPNIK, PrivateMapTileCache()) {
        override fun createDownloaderProvider(check: INetworkAvailablityCheck?, source: ITileSource): MapTileDownloader =
            super.createDownloaderProvider(check, source).apply { setTileDownloader(PrivateMapTileDownloader()) }
    }
    return MapView(context, provider).apply {
        setDestroyMode(false) // Compose's DisposableEffect owns detachment.
        setUseDataConnection(allowNetwork)
        setMultiTouchControls(true)
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        minZoomLevel = 2.0
        maxZoomLevel = 19.0
        contentDescription = "Trip street map with recorded route and start and end markers"
        val points = data.recordedPoints.map { GeoPoint(it.latitude, it.longitude) }
        if (points.size > 1) overlays.add(Polyline(this).apply {
            setPoints(points)
            outlinePaint.color = SucculentPrimary.toArgb()
            outlinePaint.strokeWidth = 2.5f * resources.displayMetrics.density
            infoWindow = null
        })
        data.endpoints.forEachIndexed { index, point ->
            overlays.add(Marker(this).apply {
                position = GeoPoint(point.latitude, point.longitude)
                title = if (index == 0) "Start" else "End"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (index == 0) SucculentPrimary.toArgb() else AndroidColor.RED)
                    setStroke(2, AndroidColor.WHITE)
                    setSize((14 * resources.displayMetrics.density).toInt(), (14 * resources.displayMetrics.density).toInt())
                }
                infoWindow = null
            })
        }
        val boundsPoints = (data.recordedPoints + data.endpoints).map { GeoPoint(it.latitude, it.longitude) }
        if (boundsPoints.isNotEmpty()) addOnFirstLayoutListener { _, _, _, _, _ ->
            zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(boundsPoints), false, (24 * resources.displayMetrics.density).toInt(), 16.0, null)
        }
    }
}

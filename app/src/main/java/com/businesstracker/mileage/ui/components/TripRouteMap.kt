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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
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
    val data = remember(trip.waypointsJson, trip.startLatitude, trip.startLongitude, trip.endLatitude, trip.endLongitude) {
        routeMapData(trip)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (data.endpoints.isEmpty() && data.recordedPoints.isEmpty()) {
            Text("Map unavailable: no recorded locations", Modifier.align(Alignment.Center).padding(16.dp))
        } else {
            val allPoints = remember(data) {
                (data.recordedPoints + data.endpoints).map { LatLng(it.latitude, it.longitude) }
            }
            val polylinePoints = remember(data) {
                data.recordedPoints.map { LatLng(it.latitude, it.longitude) }
            }
            val initialPosition = remember(allPoints) {
                allPoints.firstOrNull() ?: LatLng(0.0, 0.0)
            }
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(initialPosition, 14f)
            }

            LaunchedEffect(allPoints) {
                if (allPoints.isNotEmpty()) {
                    if (allPoints.size == 1) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(allPoints.first(), 13.5f))
                    } else {
                        var minLat = Double.MAX_VALUE
                        var maxLat = -Double.MAX_VALUE
                        var minLon = Double.MAX_VALUE
                        var maxLon = -Double.MAX_VALUE

                        allPoints.forEach { pt ->
                            if (pt.latitude < minLat) minLat = pt.latitude
                            if (pt.latitude > maxLat) maxLat = pt.latitude
                            if (pt.longitude < minLon) minLon = pt.longitude
                            if (pt.longitude > maxLon) maxLon = pt.longitude
                        }

                        val latSpan = maxLat - minLat
                        val lonSpan = maxLon - minLon

                        // Ensure a minimum span of at least ~0.016 degrees (~1.1 miles) so short trips aren't over-zoomed
                        val minSpan = 0.016
                        val baseLatPad = latSpan * 0.40
                        val extraLatPad = maxOf(baseLatPad, (minSpan - latSpan).coerceAtLeast(0.0) / 2.0, 0.005)
                        val baseLonPad = lonSpan * 0.40
                        val extraLonPad = maxOf(baseLonPad, (minSpan - lonSpan).coerceAtLeast(0.0) / 2.0, 0.005)

                        // Google Map marker pins stand upright with their anchor at (0.5, 1.0) so the pin head extends
                        // well above the coordinate. In addition, the close button overlays the top-left area.
                        // Allocate extra north padding so the start/destination markers are never cropped at the top edge.
                        val northPad = extraLatPad * 1.6
                        val southPad = extraLatPad

                        val paddedBounds = LatLngBounds(
                            LatLng(minLat - southPad, minLon - extraLonPad),
                            LatLng(maxLat + northPad, maxLon + extraLonPad)
                        )

                        // 120px padding ensures markers and route are well within the visible frame
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(paddedBounds, 120))
                    }
                }
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = remember {
                    MapUiSettings(
                        zoomControlsEnabled = false,
                        myLocationButtonEnabled = false,
                        mapToolbarEnabled = true,
                        compassEnabled = true
                    )
                },
                properties = remember {
                    MapProperties(
                        isBuildingEnabled = true,
                        isTrafficEnabled = false
                    )
                }
            ) {
                if (polylinePoints.size > 1) {
                    Polyline(
                        points = polylinePoints,
                        color = SucculentPrimary,
                        width = 12f
                    )
                }

                data.endpoints.forEachIndexed { index, point ->
                    val isStart = index == 0
                    Marker(
                        state = MarkerState(position = LatLng(point.latitude, point.longitude)),
                        title = if (isStart) "Start" else "End",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (isStart) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_RED
                        )
                    )
                }
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
            zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(boundsPoints), false, (36 * resources.displayMetrics.density).toInt(), 15.0, null)
        }
    }
}

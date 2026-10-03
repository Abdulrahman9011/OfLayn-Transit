package com.oflayn.app

import android.Manifest
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oflayn.core.model.GeoPoint
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Real MapLibre map: basemap from a configurable style, stops/route/user location drawn from the local database. */
@Composable
fun MapScreen(c: AppContainer) {
    val st = c.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    remember { MapLibre.getInstance(ctx) }
    val mapView = remember { MapView(ctx) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("") }
    var me by remember { mutableStateOf<GeoPoint?>(null) }
    var info by remember { mutableStateOf<com.oflayn.domain.transit.DatasetInfo?>(null) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
    }

    LaunchedEffect(Unit) {
        info = c.repo.info()
        mapView.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder().target(LatLng(40.19, 29.06)).zoom(11.0).build()
            m.setStyle(st.mapStyleUrl) { style -> scope.launch { draw(c, style, null); styleReady = true } }
            m.addOnMapClickListener { latLng ->
                val pt = m.projection.toScreenLocation(latLng)
                val f = m.queryRenderedFeatures(pt, STOPS_LAYER).firstOrNull()
                selected = f?.getStringProperty("name") ?: ""
                f != null
            }
        }
    }

    // Redraw when route/stop focus or user position changes.
    LaunchedEffect(styleReady, c.mapFocusRouteId, c.mapFocusStopId, me) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        m.getStyle { style ->
            scope.launch {
                val focusStop = c.mapFocusStopId?.let { id -> c.repo.stops().firstOrNull { it.id == id } }
                draw(c, style, me)
                if (focusStop != null) m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focusStop.lat, focusStop.lon), 16.0))
                else me?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 15.0)) }
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { me = c.location.current() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ mapView }, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 3.dp) {
                Column(Modifier.padding(8.dp)) {
                    Text(info?.let { "${it.label} · ${it.stopCount} / ${it.routeCount}" } ?: st.s("لا توجد بيانات محطات — نزّلها من تبويب المواصلات", "Durak verisi yok — Ulaşım sekmesinden indirin", "No stop data — download it in the Transit tab"), style = MaterialTheme.typography.labelMedium)
                    if (selected.isNotBlank()) Text(selected, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalAlignment = Alignment.End) {
            Button(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text(st.s("موقعي", "Konumum", "My location")) }
            Text("© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private const val STOPS_LAYER = "stops-layer"

private suspend fun draw(c: AppContainer, style: Style, me: GeoPoint?) {
    run {
        val stops = c.repo.stops()
        val routes = c.repo.routes()
        val stopFeatures = stops.map { s -> Feature.fromGeometry(Point.fromLngLat(s.lon, s.lat)).also { it.addStringProperty("name", s.name) } }
        upsert(style, "stops", FeatureCollection.fromFeatures(stopFeatures)) { src ->
            style.addLayer(CircleLayer(STOPS_LAYER, "stops").withProperties(circleRadius(4f), circleColor(Color.parseColor("#1565C0")), circleStrokeWidth(1f), circleStrokeColor(Color.WHITE)))
        }
        val byId = stops.associateBy { it.id }
        val line = c.mapFocusRouteId?.let { id -> routes.firstOrNull { it.id == id } }
            ?.let { r -> r.stopIds.mapNotNull { byId[it] }.map { Point.fromLngLat(it.lon, it.lat) } }.orEmpty()
        val routeFc = if (line.size >= 2) FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(line))) else FeatureCollection.fromFeatures(emptyList<Feature>())
        upsert(style, "route", routeFc) { style.addLayerBelow(LineLayer("route-layer", "route").withProperties(lineColor(Color.parseColor("#E65100")), lineWidth(4f)), STOPS_LAYER) }
        val meFc = me?.let { FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: FeatureCollection.fromFeatures(emptyList<Feature>())
        upsert(style, "me", meFc) { style.addLayer(CircleLayer("me-layer", "me").withProperties(circleRadius(8f), circleColor(Color.parseColor("#2E7D32")), circleStrokeWidth(2f), circleStrokeColor(Color.WHITE))) }
    }
}

private fun upsert(style: Style, id: String, fc: FeatureCollection, addLayers: (GeoJsonSource) -> Unit) {
    val existing = style.getSourceAs<GeoJsonSource>(id)
    if (existing != null) existing.setGeoJson(fc)
    else { val src = GeoJsonSource(id, fc); style.addSource(src); addLayers(src) }
}

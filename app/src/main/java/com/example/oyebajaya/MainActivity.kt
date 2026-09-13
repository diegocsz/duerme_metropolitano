package com.example.oyebajaya

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.tileprovider.tilesource.XYTileSource

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val osmPrefs = getSharedPreferences(
            "osmdroid",
            MODE_PRIVATE
        )

        Configuration.getInstance().load(
            applicationContext,
            osmPrefs
        )

        Configuration.getInstance().userAgentValue =
            "OyeBajaYa/1.0 (Android; contacto: diegocsalcedoz@gmail.com)"

        setContent {
            MaterialTheme {
                PantallaPrincipal()
            }
        }
    }
}

@Composable
fun PantallaPrincipal() {
    val context = LocalContext.current

    var destino by remember {
        mutableStateOf<GeoPoint?>(null)
    }

    var radioMetros by remember {
        mutableFloatStateOf(500f)
    }

    var servicioActivo by remember {
        mutableStateOf(false)
    }

    var mapView by remember {
        mutableStateOf<MapView?>(null)
    }

    var destinoMarker by remember {
        mutableStateOf<Marker?>(null)
    }

    var circuloRadio by remember {
        mutableStateOf<Polygon?>(null)
    }

    fun redibujarDestino(punto: GeoPoint) {
        val mv = mapView ?: return

        destinoMarker?.let {
            mv.overlays.remove(it)
        }

        circuloRadio?.let {
            mv.overlays.remove(it)
        }

        destinoMarker = Marker(mv).apply {
            position = punto
            setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )
            title = "Destino"
        }

        mv.overlays.add(destinoMarker)

        circuloRadio = Polygon().apply {
            points = Polygon.pointsAsCircle(
                punto,
                radioMetros.toDouble()
            )

            fillColor = 0x220A0AFF
            strokeColor = android.graphics.Color.parseColor("#3F51B5")
            strokeWidth = 3f
        }

        mv.overlays.add(circuloRadio)
        mv.invalidate()
    }

    val backgroundLocationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedido ->

            if (!concedido) {
                Toast.makeText(
                    context,
                    "Sin ubicación en segundo plano la app no podrá avisarte con la pantalla apagada",
                    Toast.LENGTH_LONG
                ).show()
            }

            pedirIgnorarOptimizacionBateria(context)
        }

    val permisosLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { resultados ->

            val fineOk =
                resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (!fineOk) {
                Toast.makeText(
                    context,
                    "Se necesita permiso de ubicación para funcionar",
                    Toast.LENGTH_LONG
                ).show()

                return@rememberLauncherForActivityResult
            }

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            } else {
                pedirIgnorarOptimizacionBateria(context)
            }
        }

    LaunchedEffect(Unit) {
        val permisos = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permisos.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        permisosLauncher.launch(
            permisos.toTypedArray()
        )
    }

    LaunchedEffect(radioMetros) {
        destino?.let {
            redibujarDestino(it)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),

            factory = { ctx ->
                MapView(ctx).apply {

                    setTileSource(
                        XYTileSource(
                            "OSM",
                            0,
                            19,
                            256,
                            ".png",
                            arrayOf(
                                "https://tile.openstreetmap.org/"
                            )
                        )
                    )

                    setUseDataConnection(true)
                    setMultiTouchControls(true)

                    controller.setZoom(14.0)

                    controller.setCenter(
                        GeoPoint(
                            -12.0464,
                            -77.0428
                        )
                    )

                    val miUbicacionOverlay =
                        MyLocationNewOverlay(
                            GpsMyLocationProvider(ctx),
                            this
                        )

                    miUbicacionOverlay.enableMyLocation()

                    miUbicacionOverlay.runOnFirstFix {
                        post {
                            miUbicacionOverlay.myLocation?.let {
                                controller.animateTo(it)
                            }
                        }
                    }

                    overlays.add(
                        miUbicacionOverlay
                    )

                    /*
                     * Overlay invisible para seleccionar
                     * el destino tocando el mapa.
                     */
                    overlays.add(
                        object : Overlay() {

                            override fun onSingleTapConfirmed(
                                e: MotionEvent?,
                                mv: MapView?
                            ): Boolean {

                                if (
                                    servicioActivo ||
                                    e == null ||
                                    mv == null
                                ) {
                                    return false
                                }

                                val punto =
                                    mv.projection.fromPixels(
                                        e.x.toInt(),
                                        e.y.toInt()
                                    ) as GeoPoint

                                destino = punto
                                redibujarDestino(punto)

                                return true
                            }
                        }
                    )

                    mapView = this
                }
            }
        )

        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Radio:")

                Slider(
                    value = radioMetros,

                    onValueChange = {
                        if (!servicioActivo) {
                            radioMetros = it
                        }
                    },

                    valueRange = 100f..3000f,

                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Text(
                    "${radioMetros.toInt()} m"
                )
            }

            Button(
                onClick = {
                    if (servicioActivo) {

                        detenerAlarma(context)
                        servicioActivo = false

                    } else {

                        val destinoActual = destino

                        if (destinoActual == null) {
                            Toast.makeText(
                                context,
                                "Primero toca el mapa para elegir un destino",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            iniciarAlarma(
                                context,
                                destinoActual,
                                radioMetros.toDouble()
                            )

                            servicioActivo = true
                        }
                    }
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),

                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        if (servicioActivo) {
                            Color(0xFFD32F2F)
                        } else {
                            Color(0xFF3F51B5)
                        }
                )
            ) {
                Text(
                    if (servicioActivo) {
                        "Detener monitoreo"
                    } else {
                        "Activar alarma"
                    },

                    color = Color.White
                )
            }
        }
    }
}

private fun iniciarAlarma(
    context: Context,
    destino: GeoPoint,
    radioMetros: Double
) {
    val intent = Intent(
        context,
        AlarmForegroundService::class.java
    ).apply {
        action = AlarmForegroundService.ACTION_START

        putExtra(
            AlarmForegroundService.EXTRA_LAT,
            destino.latitude
        )

        putExtra(
            AlarmForegroundService.EXTRA_LNG,
            destino.longitude
        )

        putExtra(
            AlarmForegroundService.EXTRA_RADIO,
            radioMetros
        )
    }

    ContextCompat.startForegroundService(
        context,
        intent
    )
}

private fun detenerAlarma(
    context: Context
) {
    val intent = Intent(
        context,
        AlarmForegroundService::class.java
    ).apply {
        action = AlarmForegroundService.ACTION_STOP
    }

    context.startService(intent)
}

private fun pedirIgnorarOptimizacionBateria(
    context: Context
) {
    val powerManager =
        context.getSystemService(
            Context.POWER_SERVICE
        ) as PowerManager

    if (
        !powerManager.isIgnoringBatteryOptimizations(
            context.packageName
        )
    ) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = Uri.parse(
                    "package:${context.packageName}"
                )
            }

            context.startActivity(intent)

        } catch (_: Exception) {
            // Algunos fabricantes bloquean este intent.
        }
    }
}
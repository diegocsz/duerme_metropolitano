package com.example.oyebajaya
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Ya no hace polling manual de ubicación. Registra un geofence en el
 * sistema (Geofencing API) y el propio Android se encarga de despertar
 * a la app cuando el usuario entra al radio — usando GPS/wifi/celda de
 * forma mucho más eficiente en batería que pedir ubicación cada 15s.
 *
 * El foreground service sigue siendo necesario para: 1) mostrar la
 * notificación persistente que Android exige para trabajo en background
 * relacionado a ubicación, y 2) reaccionar cuando el geofence se dispara.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.duermemetropolitano.START"
        const val ACTION_STOP = "com.example.duermemetropolitano.STOP"
        const val ACTION_TRIGGER = "com.example.duermemetropolitano.TRIGGER"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_RADIO = "extra_radio"

        private const val GEOFENCE_ID = "destino"
        private const val CHANNEL_ID = "geofence_channel"
        private const val NOTIF_ID = 1
    }

    private lateinit var geofencingClient: GeofencingClient
    private lateinit var alarmSoundManager: AlarmSoundManager
    private lateinit var geofencePendingIntent: PendingIntent

    override fun onCreate() {
        super.onCreate()
        geofencingClient = LocationServices.getGeofencingClient(this)
        alarmSoundManager = AlarmSoundManager(this)
        crearCanalNotificacion()

        val geofenceIntent = Intent(this, GeofenceBroadcastReceiver::class.java).apply {
            action = GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT
        }
        geofencePendingIntent = PendingIntent.getBroadcast(
            this, 0, geofenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                detener()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER -> {
                dispararAlarma()
            }
            else -> {
                val lat = intent?.getDoubleExtra(EXTRA_LAT, 0.0) ?: 0.0
                val lng = intent?.getDoubleExtra(EXTRA_LNG, 0.0) ?: 0.0
                val radio = intent?.getDoubleExtra(EXTRA_RADIO, 500.0) ?: 500.0

                startForeground(NOTIF_ID, construirNotificacion("Monitoreando tu ubicación..."))
                registrarGeofence(lat, lng, radio.toFloat())
            }
        }
        return START_STICKY
    }

    private fun registrarGeofence(lat: Double, lng: Double, radio: Float) {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(lat, lng, radio)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val request = GeofencingRequest.Builder()
            // Si el usuario ya está dentro del radio al momento de activar,
            // dispara la alarma de inmediato en vez de esperar a "entrar".
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent)
        } catch (e: SecurityException) {
            actualizarNotificacion("ERROR: falta permiso de ubicación en segundo plano")
        }
    }

    private fun dispararAlarma() {
        alarmSoundManager.startAlarm()
        actualizarNotificacion("¡Llegaste al destino! La alarma está activa.", pantallaCompleta = true)
        lanzarPantallaCompleta()
    }

    private fun lanzarPantallaCompleta() {
        val intent = Intent(this, FullScreenAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun detener() {
        geofencingClient.removeGeofences(geofencePendingIntent)
        alarmSoundManager.stopAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        geofencingClient.removeGeofences(geofencePendingIntent)
        alarmSoundManager.stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "Alarma por ubicación",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación para mantener el monitoreo activo"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(canal)
        }
    }

    private fun construirNotificacion(texto: String, pantallaCompleta: Boolean = false): Notification {
        val stopIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Duerme Metropolitano")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (pantallaCompleta) {
            val fullScreenIntent = Intent(this, FullScreenAlarmActivity::class.java)
            val fullScreenPendingIntent = PendingIntent.getActivity(
                this, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            builder.addAction(android.R.drawable.ic_media_pause, "Detener", stopPendingIntent)
        }

        return builder.build()
    }

    private fun actualizarNotificacion(texto: String, pantallaCompleta: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, construirNotificacion(texto, pantallaCompleta))
    }
}

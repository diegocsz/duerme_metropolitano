import 'dart:async';

import 'package:alarm/alarm.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:geolocator/geolocator.dart';

bool shouldTriggerAlarm(double distance, double radius, bool alarmAlreadyActive) {
  return distance <= radius && !alarmAlreadyActive;
}

// Esta función arranca el handler dentro del isolate que usa el foreground service.
@pragma('vm:entry-point')
void startGeofenceCallback() {
  FlutterForegroundTask.setTaskHandler(GeofenceTaskHandler());
}

class GeofenceTaskHandler extends TaskHandler {
  static const int _alarmId = 1;
  bool _alarmSonando = false;

  double? destinoLat;
  double? destinoLng;
  double radioMetros = 500;

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    // Datos guardados por la UI antes de iniciar el servicio.
    destinoLat = await FlutterForegroundTask.getData<double>(key: 'lat');
    destinoLng = await FlutterForegroundTask.getData<double>(key: 'lng');
    radioMetros =
        await FlutterForegroundTask.getData<double>(key: 'radio') ?? 500;
    _alarmSonando = false;
  }

  Future<void> _triggerAlarm() async {
    if (_alarmSonando) return;

    _alarmSonando = true;

    FlutterForegroundTask.updateService(
      notificationTitle: 'Duerme Metropolitano',
      notificationText: '¡Llegaste al destino! La alarma está activa.',
    );

    try {
      await Alarm.stop(_alarmId);
      await Alarm.set(
        alarmSettings: AlarmSettings(
          id: _alarmId,
          dateTime: DateTime.now().add(const Duration(seconds: 1)),
          assetAudioPath: 'assets/alarm.mp3',
          loopAudio: true,
          vibrate: true,
          warningNotificationOnKill: true,
          androidFullScreenIntent: true,
          volumeSettings: VolumeSettings.fixed(
            volume: 1.0,
            volumeEnforced: true,
            showSystemUI: false,
          ),
          notificationSettings: const NotificationSettings(
            title: 'Duerme Metropolitano',
            body: '¡Llegaste al destino! Pulsa detener para apagar la alarma.',
            stopButton: 'Detener',
          ),
        ),
      );
    } catch (e) {
      FlutterForegroundTask.updateService(
        notificationTitle: 'Duerme Metropolitano',
        notificationText: '¡Llegaste al destino! El audio no pudo iniciarse: $e',
      );
    }
  }

  @override
  Future<void> onRepeatEvent(DateTime timestamp) async {
    if (destinoLat == null || destinoLng == null) {
      FlutterForegroundTask.updateService(
        notificationTitle: 'Duerme Metropolitano',
        notificationText: 'ERROR: no se guardó el destino',
      );
      return;
    }

    double distancia;
    try {
      final posicion = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );

      distancia = Geolocator.distanceBetween(
        posicion.latitude,
        posicion.longitude,
        destinoLat!,
        destinoLng!,
      );

      // Actualiza el texto de la notificación persistente.
      // Este texto es tu forma de "debuggear" sin logs: si ves la distancia
      // bajando en tiempo real, el tracking sí funciona.
      FlutterForegroundTask.updateService(
        notificationTitle: 'Duerme Metropolitano',
        notificationText: 'Distancia al destino: ${distancia.toStringAsFixed(0)} m',
      );
    } catch (e) {
      // Ya NO se traga el error: lo muestra en la notificación para que puedas verlo.
      FlutterForegroundTask.updateService(
        notificationTitle: 'Duerme Metropolitano',
        notificationText: 'ERROR ubicación: $e',
      );
      return;
    }

    if (shouldTriggerAlarm(distancia, radioMetros, _alarmSonando)) {
      await _triggerAlarm();
    }
  }

  @override
  Future<void> onDestroy(DateTime timestamp) async {
    await Alarm.stop(_alarmId);
  }

  // Se llama cuando el usuario toca la notificación (opcional para el MVP).
  @override
  void onNotificationPressed() {
    FlutterForegroundTask.launchApp();
  }
}
import 'dart:async';
import 'package:audioplayers/audioplayers.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:geolocator/geolocator.dart';

// Esta función arranca el handler dentro del isolate que usa el foreground service.
@pragma('vm:entry-point')
void startGeofenceCallback() {
  FlutterForegroundTask.setTaskHandler(GeofenceTaskHandler());
}

class GeofenceTaskHandler extends TaskHandler {
  final AudioPlayer _player = AudioPlayer();
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

    if (distancia <= radioMetros && !_alarmSonando) {
      _alarmSonando = true;
      try {
        await _player.setReleaseMode(ReleaseMode.loop);
        await _player.play(AssetSource('alarm.mp3'));
      } catch (e) {
        // Si el audio falla (ej. falta el archivo assets/alarm.mp3), esto
        // te lo dice explícitamente en vez de quedarse en silencio.
        FlutterForegroundTask.updateService(
          notificationTitle: 'Duerme Metropolitano',
          notificationText: 'ERROR audio: $e',
        );
      }
    }
  }

  @override
  Future<void> onDestroy(DateTime timestamp) async {
    await _player.stop();
    await _player.dispose();
  }

  // Se llama cuando el usuario toca la notificación (opcional para el MVP).
  @override
  void onNotificationPressed() {
    FlutterForegroundTask.launchApp();
  }
}
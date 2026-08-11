import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import 'geofence_task_handler.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  FlutterForegroundTask.initCommunicationPort();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Duerme Metropolitano',
      theme: ThemeData(primarySwatch: Colors.indigo),
      home: const MapaScreen(),
    );
  }
}

class MapaScreen extends StatefulWidget {
  const MapaScreen({super.key});

  @override
  State<MapaScreen> createState() => _MapaScreenState();
}

class _MapaScreenState extends State<MapaScreen> {
  final MapController _mapController = MapController();
  StreamSubscription<Position>? _posicionSub;

  LatLng? _destino;
  LatLng? _miUbicacion;
  double _radio = 500; // metros
  bool _servicioActivo = false;
  LatLng _centroInicial = const LatLng(-12.0464, -77.0428); // Lima por defecto

  @override
  void initState() {
    super.initState();
    _initForegroundTask();
    _ubicarme();
  }

  @override
  void dispose() {
    _posicionSub?.cancel();
    super.dispose();
  }

  void _escucharUbicacion() {
    // Solo actualiza el punto azul en el mapa mientras la app está abierta.
    // El monitoreo real para la alarma lo hace el foreground service aparte.
    _posicionSub = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 10,
      ),
    ).listen((pos) {
      final nuevaUbicacion = LatLng(pos.latitude, pos.longitude);
      setState(() => _miUbicacion = nuevaUbicacion);
    });
  }

  void _centrarEnMiUbicacion() {
    if (_miUbicacion != null) {
      _mapController.move(_miUbicacion!, 16);
    }
  }

  void _initForegroundTask() {
    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'geofence_channel',
        channelName: 'Alarma por ubicación',
        channelDescription: 'Notificación para mantener el monitoreo activo',
      ),
      iosNotificationOptions: const IOSNotificationOptions(),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.repeat(15000), // cada 15s
        autoRunOnBoot: false,
        allowWakeLock: true,
        allowWifiLock: true,
      ),
    );
  }

  Future<void> _ubicarme() async {
    final permisoOk = await _pedirPermisos();
    if (!permisoOk) return;

    try {
      final pos = await Geolocator.getCurrentPosition();
      final ubicacion = LatLng(pos.latitude, pos.longitude);
      setState(() {
        _centroInicial = ubicacion;
        _miUbicacion = ubicacion;
      });
      // initialCenter solo aplica al construir el mapa, así que hay que
      // moverlo explícitamente una vez que tenemos la ubicación real.
      _mapController.move(ubicacion, 16);
    } catch (_) {
      // Se queda con la ubicación por defecto si falla.
    }

    _escucharUbicacion();
  }

  Future<bool> _pedirPermisos() async {
    final estadoUbicacion = await Permission.locationWhenInUse.request();
    if (!estadoUbicacion.isGranted) return false;

    // Necesario para que el servicio funcione con la app en segundo plano.
    await Permission.locationAlways.request();

    if (await FlutterForegroundTask.isIgnoringBatteryOptimizations == false) {
      await FlutterForegroundTask.requestIgnoreBatteryOptimization();
    }

    final notifPermiso =
    await FlutterForegroundTask.checkNotificationPermission();
    if (notifPermiso != NotificationPermission.granted) {
      await FlutterForegroundTask.requestNotificationPermission();
    }

    return true;
  }

  Future<void> _iniciarAlarma() async {
    if (_destino == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Primero toca el mapa para elegir un destino')),
      );
      return;
    }

    await FlutterForegroundTask.saveData(key: 'lat', value: _destino!.latitude);
    await FlutterForegroundTask.saveData(key: 'lng', value: _destino!.longitude);
    await FlutterForegroundTask.saveData(key: 'radio', value: _radio);

    await FlutterForegroundTask.startService(
      serviceId: 1,
      notificationTitle: 'Duerme Metropolitano',
      notificationText: 'Monitoreando tu ubicación...',
      callback: startGeofenceCallback,
    );

    setState(() => _servicioActivo = true);
  }

  Future<void> _detenerAlarma() async {
    await FlutterForegroundTask.stopService();
    setState(() => _servicioActivo = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Duerme Metropolitano')),
      body: Column(
        children: [
          Expanded(
            child: Stack(
              children: [
                FlutterMap(
                  mapController: _mapController,
                  options: MapOptions(
                    initialCenter: _centroInicial,
                    initialZoom: 14,
                    onTap: (tapPosition, point) {
                      if (_servicioActivo) return; // no cambiar destino con servicio activo
                      setState(() => _destino = point);
                    },
                  ),
                  children: [
                    TileLayer(
                      urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: 'com.example.duerme_metropolitano',
                    ),
                    if (_destino != null)
                      CircleLayer(
                        circles: [
                          CircleMarker(
                            point: _destino!,
                            radius: _radio,
                            useRadiusInMeter: true,
                            color: Colors.indigo.withOpacity(0.2),
                            borderColor: Colors.indigo,
                            borderStrokeWidth: 2,
                          ),
                        ],
                      ),
                    MarkerLayer(
                      markers: [
                        if (_destino != null)
                          Marker(
                            point: _destino!,
                            width: 40,
                            height: 40,
                            child: const Icon(Icons.location_pin, color: Colors.red, size: 40),
                          ),
                        if (_miUbicacion != null)
                          Marker(
                            point: _miUbicacion!,
                            width: 24,
                            height: 24,
                            child: Container(
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: Colors.blue,
                                border: Border.all(color: Colors.white, width: 3),
                                boxShadow: const [
                                  BoxShadow(color: Colors.black26, blurRadius: 4),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
                Positioned(
                  bottom: 16,
                  right: 16,
                  child: FloatingActionButton(
                    mini: true,
                    backgroundColor: Colors.white,
                    onPressed: _centrarEnMiUbicacion,
                    child: const Icon(Icons.my_location, color: Colors.indigo),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                Row(
                  children: [
                    const Text('Radio:'),
                    Expanded(
                      child: Slider(
                        min: 100,
                        max: 3000,
                        divisions: 29,
                        value: _radio,
                        label: '${_radio.toStringAsFixed(0)} m',
                        onChanged: _servicioActivo
                            ? null
                            : (v) => setState(() => _radio = v),
                      ),
                    ),
                    Text('${_radio.toStringAsFixed(0)} m'),
                  ],
                ),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: _servicioActivo ? _detenerAlarma : _iniciarAlarma,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _servicioActivo ? Colors.red : Colors.indigo,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    child: Text(
                      _servicioActivo ? 'Detener monitoreo' : 'Activar alarma',
                      style: const TextStyle(color: Colors.white, fontSize: 16),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
# Duerme Metropolitano

Una app Flutter tipo MVP para crear una alarma por geolocalización. El usuario elige un destino en el mapa, define un radio de llegada y deja un monitoreo en segundo plano activo. Cuando la ubicación entra en el área configurada, la app muestra una notificación persistente y dispara una alarma nativa con sonido, vibración y aviso en pantalla.

## Qué hace

- Muestra un mapa interactivo con `flutter_map`.
- Usa la ubicación actual con `geolocator`.
- Pide permisos de ubicación y notificaciones.
- Mantiene un `foreground service` con `flutter_foreground_task` para seguir la distancia aunque la app esté en segundo plano.
- Reproduce una alarma nativa con `alarm` cuando el destino se alcanza.

## Flujo de uso

1. Abre la app y concede los permisos solicitados.
2. Toca el mapa para elegir tu destino.
3. Ajusta el radio de llegada con el slider.
4. Pulsa `Activar alarma`.
5. La app vigila tu posición cada cierto intervalo.
6. Cuando entras en el radio configurado, se activa la alarma y la notificación cambia a estado de llegada.

## Tecnologías

- Flutter
- `alarm`
- `flutter_map`
- `geolocator`
- `permission_handler`
- `flutter_foreground_task`

## Instalación

```bash
flutter pub get
```

Luego ejecuta la app en un dispositivo o emulador compatible:

```bash
flutter run
```

Para compilar release:

```bash
flutter build apk --release
```

## Requisitos nativos

El paquete `alarm` necesita algo de configuración nativa para funcionar bien, y este proyecto ya la incluye.

### Android

- `minSdkVersion` 23.
- Permisos de ubicación, vibración, notificaciones, wake lock y alarmas exactas.
- Servicio de foreground para el monitoreo de ubicación.

### iOS

- `UIBackgroundModes` con `audio` y `fetch`.
- `BGTaskSchedulerPermittedIdentifiers` configurado.
- Registro del plugin en `AppDelegate` para tareas de fondo.

## Estructura principal

- `lib/main.dart`: UI del mapa, permisos y arranque del servicio en foreground.
- `lib/geofence_task_handler.dart`: lógica de distancia, disparo de alarma y estado persistente.
- `assets/alarm.mp3`: audio usado por la alarma.

## Notas de funcionamiento

- La alarma ya no depende de `audioplayers`; usa `alarm` para una reproducción más robusta en Android e iOS.
- El servicio deja visible el estado en la notificación persistente para facilitar pruebas y depuración.
- Si la ubicación o los permisos fallan, la notificación muestra el error para que sea fácil de detectar.

## Personalización rápida

- Cambia `assets/alarm.mp3` por tu propio sonido si quieres otro tono.
- Ajusta el radio por defecto en `lib/geofence_task_handler.dart` o desde el slider de la interfaz.
- Modifica los textos de notificación en `lib/main.dart` y `lib/geofence_task_handler.dart`.

## Estado del proyecto

Este proyecto está pensado como un MVP funcional. El objetivo principal es probar la experiencia de alarma por destino sin ocultar la complejidad del fondo: permisos, servicio persistente y reproducción nativa.

import 'package:flutter_test/flutter_test.dart';
import 'package:duerme_metropolitano/geofence_task_handler.dart';

void main() {
  test('debe disparar la alarma al entrar dentro del radio', () {
    expect(shouldTriggerAlarm(50, 100, false), isTrue);
  });

  test('no debe disparar la alarma si ya está activa', () {
    expect(shouldTriggerAlarm(50, 100, true), isFalse);
  });

  test('no debe disparar la alarma si está fuera del radio', () {
    expect(shouldTriggerAlarm(150, 100, false), isFalse);
  });
}

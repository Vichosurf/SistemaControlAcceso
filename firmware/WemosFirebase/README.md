# Firmware Wemos + Firebase

- Sketch: `WemosFirebase.ino`
- Board target: Wemos D1 mini / ESP8266.
- Serial monitor: 115200 baudios.
- Librerías: Firebase ESP Client y Servo.
- El sketch usa HC-SR04 (TRIG D1, ECHO D2), servo D5, LED rojo D6, LED verde D7 y LDR A0.

Antes de cargarlo, completa las credenciales locales del sketch y ajusta los pines a tu montaje real. Crea un usuario Firebase dedicado al Wemos con Email/Password. No uses credenciales reales en commits públicos.

El firmware publica `/sensores`, lee `/comandos/ultimo` y publica el estado en `/acceso`. La comunicación se realiza por Wi-Fi/HTTPS a través de Firebase; el puerto Serial se usa para diagnóstico.

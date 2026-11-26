# Firmware Arduino Uno + Wemos ESP8266

## Responsabilidades
- Arduino Uno: HC-SR04, LDR, LEDs y servomotor; calcula el modo automático y controla las salidas.
- Wemos ESP8266: Wi-Fi, autenticación de dispositivo en Firebase, publicación de telemetría y recepción de órdenes de Android.
- Android: publica configuración/órdenes en `/comandos/ultimo` y lee `/sensores` y `/acceso`.

## Cableado UART propuesto (9600 baudios)
| Arduino Uno | Wemos D1 mini | Función |
|---|---|---|
| D11 (TX, 5 V) | D5 (RX, 3,3 V) mediante divisor resistivo | Datos Uno -> Wemos |
| D10 (RX) | D6 (TX, 3,3 V) | Datos Wemos -> Uno |
| GND | GND | Tierra común |

**No conectes directamente el TX de 5 V del Uno al RX de 3,3 V del Wemos.** Usa divisor resistivo o conversor lógico. Alimenta el servo con una fuente adecuada para su corriente, une las tierras y no lo alimentes desde 3,3 V del Wemos.

## Pines propuestos Arduino Uno
- HC-SR04 TRIG D8, ECHO D9.
- Servo D6.
- LED rojo D4 y LED verde D5, cada uno con resistencia de 220-330 ohm.
- LDR en A0 con divisor resistivo.

Los pines son una propuesta de firmware: verifica el cableado real del protoboard antes de energizar y cámbialos en el sketch si no coinciden.

## Carga
1. Arduino IDE: selecciona Arduino Uno y carga `ArduinoUnoControlAcceso.ino`.
2. Selecciona Wemos D1 mini / ESP8266 y carga `WemosFirebase.ino`.
3. En el sketch Wemos completa SSID, contraseña, API Key, URL RTDB y usuario Firebase dedicado al dispositivo.
4. Instala ESP8266 core, Firebase ESP Client y las librerías requeridas por el sketch.
5. Monitor serial del Wemos: 115200 baudios. Enlace Uno-Wemos: 9600 baudios.

## Protocolo
- Uno -> Wemos: `S|seq|distanciaCm|luzADC|angulo|modo|emergencia|millis`
- Wemos -> Uno: `C|seq|SET_CONFIG|AUTO/OPEN/CLOSED|umbralDistancia|umbralLuz|emergencia`
- Firebase: `/sensores`, `/acceso`, `/comandos/ultimo`.

El checksum de la orden lo valida el Wemos antes de reenviarla al Uno. Este prototipo académico no es un sistema certificado ni apto para controlar una barrera real sin enclavamientos y revisión de seguridad.

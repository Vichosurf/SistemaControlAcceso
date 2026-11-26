/*
 * SistemaControlAcceso - Wemos D1 mini / ESP8266 (puente Wi-Fi)
 * El Arduino Uno lee sensores y gobierna servo/LEDs.
 * El Wemos intercambia Firebase Realtime Database <-> UART hacia el Uno.
 *
 * UART: Wemos D5 (RX) <- divisor desde Uno D11 (TX 5V)
 *       Wemos D6 (TX) -> Uno D10 (RX), GND comun.
 *       SoftwareSerial 9600 baudios.
 */
#include <ESP8266WiFi.h>
#include <SoftwareSerial.h>
#include <Firebase_ESP_Client.h>
#include <Hash.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

#define WIFI_SSID "TU_WIFI"
#define WIFI_PASSWORD "TU_CLAVE_WIFI"
#define API_KEY "TU_FIREBASE_WEB_API_KEY"
#define DATABASE_URL "https://TU-PROYECTO-default-rtdb.firebaseio.com/"
#define DEVICE_EMAIL "wemos-device@tu-dominio"
#define DEVICE_PASSWORD "CLAVE_DE_CUENTA_DE_DISPOSITIVO"

#define UNO_RX D5
#define UNO_TX D6
SoftwareSerial unoSerial(UNO_RX, UNO_TX); // RX, TX

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
unsigned long lastCommandPoll = 0;
long lastCommandSequence = 0;
String uartLine;

String makeChecksum(long seq, const String &command, const String &payload, unsigned long long timestamp) {
  String raw = String(seq) + "|" + command + "|" + payload + "|" + String(timestamp);
  String digest = sha256(raw + "_OT_KEY_2026");
  return digest.substring(0, 8).toUpperCase();
}

String valueFromPayload(const String &payload, const String &key, const String &fallback) {
  const String token = key + "=";
  int start = payload.indexOf(token);
  if (start < 0) return fallback;
  start += token.length();
  int end = payload.indexOf(';', start);
  if (end < 0) end = payload.length();
  return payload.substring(start, end);
}

void publishTelemetry(const String &line) {
  // S|telemetrySeq|distance|light|angle|mode|emergency|unoMillis
  long seq; int distance, light, angle, emergency;
  char mode[12] = "AUTO";
  unsigned long unoMillis;
  if (sscanf(line.c_str(), "S|%ld|%d|%d|%d|%11[^|]|%d|%lu",
      &seq, &distance, &light, &angle, mode, &emergency, &unoMillis) != 7) return;

  const int rssi = WiFi.RSSI();
  FirebaseJson sensors;
  sensors.set("distancia", distance);
  sensors.set("luz", light);
  sensors.set("anguloServo", angle);
  sensors.set("modo", mode);
  sensors.set("emergencia", emergency == 1);
  sensors.set("rssi", rssi);
  sensors.set("secuenciaArduino", seq);
  sensors.set("actualizadoEn", (long long)millis());

  if (!Firebase.RTDB.setJSON(&fbdo, "/sensores", &sensors)) {
    Serial.printf("Error publicando sensores: %s\n", fbdo.errorReason().c_str());
    return;
  }

  FirebaseJson access;
  access.set("barreraAbierta", angle == 90);
  access.set("anguloServo", angle);
  access.set("modo", mode);
  access.set("emergencia", emergency == 1);
  access.set("confirmadoArduino", true);
  access.set("secuenciaArduino", seq);
  access.set("actualizadoEn", (long long)millis());
  Firebase.RTDB.setJSON(&fbdo, "/acceso", &access);
}

void pollAndroidCommand() {
  if (!Firebase.RTDB.getJSON(&fbdo, "/comandos/ultimo")) return;
  FirebaseJson *json = fbdo.to<FirebaseJson *>();
  FirebaseJsonData value;

  json->get(value, "seq");
  long seq = value.intValue;
  if (seq <= lastCommandSequence) return;

  json->get(value, "comando");
  String command = value.stringValue;
  json->get(value, "payload");
  String payload = value.stringValue;
  json->get(value, "timestamp");
  unsigned long long timestamp = (unsigned long long)value.doubleValue;
  json->get(value, "checksum");
  String receivedChecksum = value.stringValue;

  if (receivedChecksum != makeChecksum(seq, command, payload, timestamp)) {
    Serial.println("Orden ignorada: checksum incorrecto.");
    return;
  }
  if (command != "SET_CONFIG") {
    Serial.println("Orden ignorada: comando no soportado.");
    return;
  }

  String mode = valueFromPayload(payload, "mode", "AUTO");
  String distance = valueFromPayload(payload, "distanceThreshold", "20");
  String light = valueFromPayload(payload, "lightThreshold", "400");
  String emergency = valueFromPayload(payload, "emergency", "0");

  String uartCommand = "C|" + String(seq) + "|SET_CONFIG|" + mode + "|" +
      distance + "|" + light + "|" + emergency;
  unoSerial.println(uartCommand);
  lastCommandSequence = seq;
  Serial.println("Orden Firebase enviada al Arduino por UART: " + uartCommand);
}

void setup() {
  Serial.begin(115200);
  unoSerial.begin(9600);
  uartLine.reserve(100);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWi-Fi conectado: " + WiFi.localIP().toString());

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  auth.user.email = DEVICE_EMAIL;
  auth.user.password = DEVICE_PASSWORD;
  config.token_status_callback = tokenStatusCallback;
  Firebase.reconnectWiFi(true);
  Firebase.begin(&config, &auth);
}

void loop() {
  while (unoSerial.available()) {
    const char c = (char)unoSerial.read();
    if (c == '\n') {
      uartLine.trim();
      if (uartLine.startsWith("S|") && Firebase.ready()) publishTelemetry(uartLine);
      uartLine = "";
    } else if (c != '\r' && uartLine.length() < 100) {
      uartLine += c;
    } else if (uartLine.length() >= 100) {
      uartLine = "";
    }
  }

  if (Firebase.ready() && millis() - lastCommandPoll >= 400) {
    lastCommandPoll = millis();
    pollAndroidCommand();
  }
  yield();
}

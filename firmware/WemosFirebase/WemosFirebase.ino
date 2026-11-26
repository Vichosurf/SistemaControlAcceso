#include <ESP8266WiFi.h>
#include <Firebase_ESP_Client.h>
#include <Servo.h>
#include <Hash.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// Completar localmente. Nunca subir credenciales reales a Git.
#define WIFI_SSID "TU_WIFI"
#define WIFI_PASSWORD "TU_CLAVE_WIFI"
#define API_KEY "TU_FIREBASE_WEB_API_KEY"
#define DATABASE_URL "https://TU-PROYECTO-default-rtdb.firebaseio.com/"
#define DEVICE_EMAIL "wemos-device@tu-dominio"
#define DEVICE_PASSWORD "CLAVE_DE_CUENTA_DE_DISPOSITIVO"

#define TRIG_PIN D1
#define ECHO_PIN D2
#define SERVO_PIN D5
#define LED_RED D6
#define LED_GREEN D7
#define LDR_PIN A0
#define MAX_DISTANCE_CM 300

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
Servo barrierServo;
unsigned long lastSensorPublish = 0;
unsigned long lastCommandPoll = 0;
long lastSequence = 0;

String frameChecksum(long seq, const String &command, const String &payload, unsigned long long timestamp) {
  String raw = String(seq) + "|" + command + "|" + payload + "|" + String(timestamp);
  String digest = sha256(raw);
  digest.toUpperCase();
  return digest.substring(0, 16);
}

void publishAccessState(int angle) {
  FirebaseJson state;
  state.set("barreraAbierta", angle == 90);
  state.set("anguloServo", angle);
  state.set("actualizadoEn", (long long)millis());
  Firebase.RTDB.setJSON(&fbdo, "/acceso", &state);
}

void applyCommand(const String &command, const String &payload) {
  if (command != "SET_BARRIER") return;
  const int angle = payload.indexOf("angulo=90") >= 0 ? 90 : 0;
  barrierServo.write(angle);
  digitalWrite(LED_RED, angle == 0 ? HIGH : LOW);
  digitalWrite(LED_GREEN, angle == 90 ? HIGH : LOW);
  publishAccessState(angle);
}

void pollCommand() {
  if (!Firebase.RTDB.getJSON(&fbdo, "/comandos/ultimo")) return;
  FirebaseJson *json = fbdo.to<FirebaseJson *>();
  FirebaseJsonData value;

  json->get(value, "seq");
  long seq = value.intValue;
  if (seq <= lastSequence) return;

  json->get(value, "comando");
  String command = value.stringValue;
  json->get(value, "payload");
  String payload = value.stringValue;
  json->get(value, "timestamp");
  unsigned long long timestamp = (unsigned long long)value.doubleValue;
  json->get(value, "checksum");
  String received = value.stringValue;

  if (!received.equalsIgnoreCase(frameChecksum(seq, command, payload, timestamp))) {
    Serial.println("Comando rechazado: checksum incorrecto");
    return;
  }

  applyCommand(command, payload);
  lastSequence = seq;
  Serial.printf("Comando aceptado #%ld: %s\n", seq, command.c_str());
}

void publishSensors() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long duration = pulseIn(ECHO_PIN, HIGH, 30000UL);
  int distance = duration == 0 ? MAX_DISTANCE_CM : (int)(duration * 0.0343 / 2.0);
  int light = analogRead(LDR_PIN);

  FirebaseJson sensors;
  sensors.set("distancia", distance);
  sensors.set("luz", light);
  sensors.set("actualizadoEn", (long long)millis());
  if (!Firebase.RTDB.setJSON(&fbdo, "/sensores", &sensors)) {
    Serial.printf("Error publicando sensores: %s\n", fbdo.errorReason().c_str());
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  barrierServo.attach(SERVO_PIN);
  barrierServo.write(0);
  digitalWrite(LED_RED, HIGH);
  digitalWrite(LED_GREEN, LOW);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Conectando Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWi-Fi conectado");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  auth.user.email = DEVICE_EMAIL;
  auth.user.password = DEVICE_PASSWORD;
  config.token_status_callback = tokenStatusCallback;
  Firebase.reconnectWiFi(true);
  Firebase.begin(&config, &auth);
}

void loop() {
  if (WiFi.status() == WL_CONNECTED && Firebase.ready()) {
    if (millis() - lastSensorPublish >= 1000) {
      lastSensorPublish = millis();
      publishSensors();
    }
    if (millis() - lastCommandPoll >= 500) {
      lastCommandPoll = millis();
      pollCommand();
    }
  }
  delay(10);
}

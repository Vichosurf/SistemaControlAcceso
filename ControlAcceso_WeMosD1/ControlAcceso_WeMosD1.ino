#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Servo.h>
#include <bearssl/bearssl_hash.h>

// Conserva el mapeo de pines que ya usa Copy-Main.
// Protege ECHO del HC-SR04 con divisor de tensión (5 V -> 3.3 V).
// Alimenta el servo externamente y une las tierras (GND).

const char* AP_SSID = "SistemaControlAcceso";
const char* AP_PASSWORD = "12345678";
const char* STA_SSID = "";       // Opcional: Wi-Fi externa 2.4 GHz
const char* STA_PASSWORD = "";
const char* API_SECRET = "ClaveSecretaControlAcceso2026";

const uint8_t SERVO_PIN = D0;
const uint8_t TRIG_PIN = D1;
const uint8_t ECHO_PIN = D2;
const uint8_t LED_ROJO = D5;
const uint8_t LED_VERDE = D6;
const uint8_t LDR_PIN = A0;

ESP8266WebServer server(80);
Servo barrera;
bool estaAbierta = false;
bool aperturaManual = false;
int umbralDistancia = 20;
int umbralLuz = 600;
int ultimaDistancia = 400;
int ultimaLuz = 0;
String motivoActual = "INICIO";
String ultimoEvento = "SISTEMA_INICIADO";

unsigned long tSensores = 0, tSerial = 0, tReconexion = 0, tMantenerAbierta = 0;
const unsigned long INTERVALO_SENSORES = 200;
const unsigned long INTERVALO_SERIAL = 1000;
const unsigned long INTERVALO_RECONEXION = 10000;
const unsigned long TIEMPO_HOLD_MS = 3000;

struct Evento { String tipo; int distancia; int luz; String firma; };
const uint8_t MAX_EVENTOS = 20;
Evento eventos[MAX_EVENTOS];
uint8_t totalEventos = 0;

String sha256(const String& datos) {
  br_sha256_context ctx;
  br_sha256_init(&ctx);
  br_sha256_update(&ctx, datos.c_str(), datos.length());
  uint8_t digest[32];
  br_sha256_out(&ctx, digest);
  String salida;
  for (uint8_t i = 0; i < 32; i++) {
    char b[3]; snprintf(b, sizeof(b), "%02x", digest[i]); salida += b;
  }
  return salida;
}

void registrarEvento(const String& tipo) {
  ultimoEvento = tipo;
  String firma = sha256(tipo + "|" + String(ultimaDistancia) + "|" + String(ultimaLuz) + "|" + API_SECRET);
  if (totalEventos < MAX_EVENTOS) eventos[totalEventos++] = {tipo, ultimaDistancia, ultimaLuz, firma};
  else {
    for (uint8_t i = 1; i < MAX_EVENTOS; i++) eventos[i - 1] = eventos[i];
    eventos[MAX_EVENTOS - 1] = {tipo, ultimaDistancia, ultimaLuz, firma};
  }
  Serial.println("[EVENTO] " + tipo);
}

void abrirBarrera(const String& motivo) {
  motivoActual = motivo;
  if (!estaAbierta) {
    barrera.write(90); digitalWrite(LED_ROJO, HIGH); digitalWrite(LED_VERDE, LOW);
    estaAbierta = true; registrarEvento(motivo);
  }
}
void cerrarBarrera() {
  motivoActual = "NORMAL";
  if (estaAbierta) {
    barrera.write(0); digitalWrite(LED_ROJO, LOW); digitalWrite(LED_VERDE, HIGH);
    estaAbierta = false; registrarEvento("CIERRE_BARRERA");
  }
}
int leerDistanciaCm() {
  digitalWrite(TRIG_PIN, LOW); delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH); delayMicroseconds(10); digitalWrite(TRIG_PIN, LOW);
  unsigned long duracion = pulseIn(ECHO_PIN, HIGH, 25000);
  return duracion == 0 ? 400 : (int)(duracion / 58UL);
}

String construirJsonEstado() {
  String estado = estaAbierta ? "abierta" : "cerrada";
  String modo = aperturaManual ? "manual" : "automatico";
  String ip = WiFi.status() == WL_CONNECTED ? WiFi.localIP().toString() : WiFi.softAPIP().toString();
  String firma = sha256(estado + "|" + String(ultimaDistancia) + "|" + String(ultimaLuz) + "|" + API_SECRET);
  String json = "{";
  json += "\"status\":\"ok\",\"conectado\":true,\"ip\":\"" + ip + "\",";
  json += "\"barrera\":\"" + estado + "\",\"estado\":\"" + estado + "\",";
  json += "\"abierta\":" + String(estaAbierta ? "true" : "false") + ",";
  json += "\"modo\":\"" + modo + "\",\"distancia\":" + String(ultimaDistancia) + ",";
  json += "\"luz\":" + String(ultimaLuz) + ",\"umbral_distancia\":" + String(umbralDistancia) + ",";
  json += "\"umbral_luz\":" + String(umbralLuz) + ",\"motivo\":\"" + motivoActual + "\",";
  json += "\"evento\":\"" + ultimoEvento + "\",\"total_eventos\":" + String(totalEventos) + ",";
  json += "\"firma_sha256\":\"" + firma + "\"}";
  return json;
}
void responderEstado() {
  server.sendHeader("Cache-Control", "no-store");
  server.send(200, "application/json", construirJsonEstado());
}

void configurarServidor() {
  server.on("/", HTTP_GET, responderEstado);
  server.on("/estado", HTTP_GET, responderEstado);
  server.on("/status", HTTP_GET, responderEstado);
  server.on("/datos", HTTP_GET, responderEstado);
  server.on("/sensores", HTTP_GET, responderEstado);
  server.on("/lectura", HTTP_GET, responderEstado);
  server.on("/api/estado", HTTP_GET, responderEstado);
  server.on("/abrir", HTTP_GET, []() {
    aperturaManual = true; abrirBarrera("APERTURA_MANUAL_APP"); responderEstado();
  });
  server.on("/cerrar", HTTP_GET, []() {
    aperturaManual = true; cerrarBarrera(); registrarEvento("CIERRE_MANUAL_APP"); responderEstado();
  });
  server.on("/auto", HTTP_GET, []() {
    aperturaManual = false; motivoActual = "MODO_AUTOMATICO"; responderEstado();
  });
  server.on("/config", HTTP_GET, []() {
    if (server.hasArg("dist")) {
      int v = server.arg("dist").toInt(); if (v >= 1 && v <= 400) umbralDistancia = v;
    }
    if (server.hasArg("luz")) {
      int v = server.arg("luz").toInt(); if (v >= 0 && v <= 1023) umbralLuz = v;
    }
    responderEstado();
  });
  server.on("/eventos", HTTP_GET, []() {
    String json = "[";
    for (uint8_t i = 0; i < totalEventos; i++) {
      if (i) json += ",";
      json += "{\"evento\":\"" + eventos[i].tipo + "\",\"distancia\":" + String(eventos[i].distancia);
      json += ",\"luz\":" + String(eventos[i].luz) + ",\"firma_sha256\":\"" + eventos[i].firma + "\"}";
    }
    json += "]";
    server.send(200, "application/json", json);
  });
  server.onNotFound([]() { server.send(404, "application/json", "{\"status\":\"error\"}"); });
  server.begin();
}

void iniciarWiFi() {
  WiFi.mode(WIFI_AP_STA);
  WiFi.setSleepMode(WIFI_NONE_SLEEP);
  bool ok = WiFi.softAP(AP_SSID, AP_PASSWORD);
  Serial.println(ok ? "[AP] Red local activa" : "[AP] Error al iniciar AP");
  Serial.print("[AP] SSID: "); Serial.println(AP_SSID);
  Serial.print("[AP] IP: "); Serial.println(WiFi.softAPIP());
  if (strlen(STA_SSID) > 0) {
    WiFi.begin(STA_SSID, STA_PASSWORD);
    uint8_t n = 0;
    while (WiFi.status() != WL_CONNECTED && n++ < 12) delay(500);
    if (WiFi.status() == WL_CONNECTED) {
      Serial.print("[STA] IP externa: "); Serial.println(WiFi.localIP());
    } else Serial.println("[STA] No conectó; AP local continúa disponible.");
  }
}

void procesarSerial() {
  if (!Serial.available()) return;
  String cmd = Serial.readStringUntil('\n'); cmd.trim(); cmd.toUpperCase();
  if (cmd == "ABRIR") { aperturaManual = true; abrirBarrera("APERTURA_SERIAL"); }
  else if (cmd == "CERRAR") { aperturaManual = true; cerrarBarrera(); registrarEvento("CIERRE_SERIAL"); }
  else if (cmd == "AUTO") { aperturaManual = false; Serial.println("[SERIAL] AUTO"); }
  else if (cmd == "ESTADO") Serial.println(construirJsonEstado());
  else if (cmd == "CALIBRAR") {
    long suma = 0; for (uint8_t i = 0; i < 10; i++) { suma += analogRead(LDR_PIN); delay(20); }
    ultimaLuz = suma / 10; umbralLuz = constrain(ultimaLuz + 120, 0, 1023);
    Serial.println("[CALIBRACION] Umbral: " + String(umbralLuz));
  }
}

void setup() {
  Serial.begin(115200); delay(150);
  pinMode(TRIG_PIN, OUTPUT); pinMode(ECHO_PIN, INPUT);
  pinMode(LED_ROJO, OUTPUT); pinMode(LED_VERDE, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  barrera.attach(SERVO_PIN); barrera.write(0);
  digitalWrite(LED_ROJO, LOW); digitalWrite(LED_VERDE, HIGH);
  iniciarWiFi(); configurarServidor();
  Serial.println("[HTTP] Servidor listo. Comandos: ABRIR/CERRAR/AUTO/ESTADO/CALIBRAR");
}

void loop() {
  server.handleClient(); procesarSerial();
  unsigned long ahora = millis();
  if (ahora - tReconexion >= INTERVALO_RECONEXION) {
    tReconexion = ahora;
    if (strlen(STA_SSID) > 0 && WiFi.status() != WL_CONNECTED) WiFi.begin(STA_SSID, STA_PASSWORD);
  }
  if (ahora - tSensores >= INTERVALO_SENSORES) {
    tSensores = ahora;
    ultimaDistancia = leerDistanciaCm();
    ultimaLuz = analogRead(LDR_PIN);
    if (!aperturaManual) {
      // Lógica solicitada: proximidad O lectura LDR por encima del umbral.
      bool proximidad = ultimaDistancia > 0 && ultimaDistancia <= umbralDistancia;
      bool luzDetectada = ultimaLuz >= umbralLuz;
      if (proximidad || luzDetectada) {
        tMantenerAbierta = ahora;
        abrirBarrera(proximidad ? "PROXIMIDAD_DETECTADA" : "LUZ_DETECTADA");
      } else if (estaAbierta && ahora - tMantenerAbierta >= TIEMPO_HOLD_MS) cerrarBarrera();
    }
  }
  if (ahora - tSerial >= INTERVALO_SERIAL) {
    tSerial = ahora;
    Serial.printf("[SENSORES] Dist:%d cm | LDR:%d/%d | Barrera:%s | Modo:%s\n",
      ultimaDistancia, ultimaLuz, umbralLuz, estaAbierta ? "ABIERTA" : "CERRADA",
      aperturaManual ? "MANUAL" : "AUTO");
  }
}

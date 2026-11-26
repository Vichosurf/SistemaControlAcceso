#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <ESP8266WebServer.h>
#include <WiFiClient.h>
#include <Servo.h>
#include <bearssl/bearssl_hash.h>

// =========================================================================
// 1. CONFIGURACIÓN DE RED Y SEGURIDAD (Modificar con tu Hotspot 2.4 GHz)
// =========================================================================
const char* ssid     = "ZONA_WIFI_CONTROL_ACCESO";
const char* password = "ClaveSecretaControlAcceso2026";

// Opcional: URL de backend externo si usas servidor adicional
const bool usarBackendHttp = false;
const char* serverUrl      = "http://192.168.1.100:8000/api/acceso/";

// Clave secreta compartida con la App Android para validar integridad SHA-256
const String API_SECRET = "ClaveSecretaControlAcceso2026";

// =========================================================================
// 2. MAPEO DE PINES SEGÚN TU MONTAJE FÍSICO EN WEMOS D1
// =========================================================================
const int servoPin = D0;  // Etiqueta D2 en placa (señal servomotor)
const int trigPin  = D1;  // Etiqueta D3 en placa (HC-SR04 Trig)
const int echoPin  = D2;  // Etiqueta D4 en placa (HC-SR04 Echo)
const int ledRojo  = D5;  // Etiqueta D5 en placa (LED Rojo - Alerta/Abierta)
const int ledVerde = D6;  // Etiqueta D6 en placa (LED Verde - Normal/Cerrada)
const int ldrPin   = A0;  // Pin analógico A0 (Fotorresistencia LDR)

// =========================================================================
// 3. VARIABLES DE ESTADO, UMBRALES Y ALMACENAMIENTO OFFLINE (Rúbrica 3.1.1.4)
// =========================================================================
Servo barrera;
ESP8266WebServer server(80);

bool estaAbierta    = false;
bool aperturaManual = false;
int umbralDistancia = 20;   // cm
int umbralLuz       = 450;  // ADC (0-1023)

int ultimaDistancia = 400;
int ultimaLuz       = 500;

// Temporizadores no bloqueantes con millis()
unsigned long tAnteriorSensores   = 0;
unsigned long tAnteriorSerial     = 0;
unsigned long tAnteriorReconexion = 0;

const long INTERVALO_SENSORES   = 300;   // Lectura de sensores cada 300 ms
const long INTERVALO_SERIAL     = 1500;  // Telemetría serial cada 1.5 s
const long INTERVALO_RECONEXION = 10000; // Reintento Wi-Fi cada 10 s

// Buffer circular para almacenamiento temporal cuando no hay conexión Wi-Fi
struct EventoOffline {
  String evento;
  int distancia;
  int luz;
  unsigned long timestampMs;
  String firmaSha256;
};

const int MAX_OFFLINE = 15;
EventoOffline colaOffline[MAX_OFFLINE];
int totalOffline = 0;

// =========================================================================
// 4. CRIPTOGRAFÍA SHA-256 CON BEARSSL (Rúbrica 3.1.1.1 y 3.1.1.2)
// =========================================================================
String generarSHA256(String payload) {
  br_sha256_context ctx;
  br_sha256_init(&ctx);
  br_sha256_update(&ctx, payload.c_str(), payload.length());

  byte shaResult[32];
  br_sha256_out(&ctx, shaResult);

  String hashHex = "";
  for (int i = 0; i < 32; i++) {
    char str[3];
    sprintf(str, "%02x", (int)shaResult[i]);
    hashHex += str;
  }
  return hashHex;
}

String construirJsonEstado() {
  String estadoStr = estaAbierta ? "abierta" : "cerrada";
  String modoStr   = aperturaManual ? "manual" : "automatico";
  String rawFirma  = estadoStr + "|" + String(ultimaDistancia) + "|" + String(ultimaLuz) + "|" + API_SECRET;
  String firma     = generarSHA256(rawFirma);

  String json = "{";
  json += "\"barrera\":\"" + estadoStr + "\",";
  json += "\"modo\":\"" + modoStr + "\",";
  json += "\"distancia\":" + String(ultimaDistancia) + ",";
  json += "\"luz\":" + String(ultimaLuz) + ",";
  json += "\"pendientes_offline\":" + String(totalOffline) + ",";
  json += "\"firma_sha256\":\"" + firma + "\"";
  json += "}";
  return json;
}

// =========================================================================
// 5. CONTROL DE ACTUADORES Y GESTIÓN DE EVENTOS
// =========================================================================
void abrirBarrera() {
  barrera.write(90);
  digitalWrite(ledRojo, HIGH);
  digitalWrite(ledVerde, LOW);
  estaAbierta = true;
}

void cerrarBarrera() {
  barrera.write(0);
  digitalWrite(ledRojo, LOW);
  digitalWrite(ledVerde, HIGH);
  estaAbierta = false;
}

void guardarEnColaOffline(String evento, int dist, int luz, String firma) {
  if (totalOffline < MAX_OFFLINE) {
    colaOffline[totalOffline] = {evento, dist, luz, millis(), firma};
    totalOffline++;
  } else {
    // Desplaza el más antiguo (FIFO) si la cola está llena
    for (int i = 1; i < MAX_OFFLINE; i++) {
      colaOffline[i - 1] = colaOffline[i];
    }
    colaOffline[MAX_OFFLINE - 1] = {evento, dist, luz, millis(), firma};
  }
  Serial.println("[OFFLINE_STORAGE] Evento almacenado en memoria local (" + String(totalOffline) + "/" + String(MAX_OFFLINE) + ")");
}

void registrarEvento(int dist, int luz, String evento) {
  String datosFirma = evento + "|" + String(dist) + "|" + String(luz) + "|" + API_SECRET;
  String firma      = generarSHA256(datosFirma);

  String jsonPayload = "{";
  jsonPayload += "\"evento\":\"" + evento + "\",";
  jsonPayload += "\"distancia\":" + String(dist) + ",";
  jsonPayload += "\"luz\":" + String(luz) + ",";
  jsonPayload += "\"firma_sha256\":\"" + firma + "\"";
  jsonPayload += "}";

  Serial.println("[EVENTO_IoT] " + jsonPayload);

  if (WiFi.status() == WL_CONNECTED) {
    if (usarBackendHttp) {
      WiFiClient client;
      HTTPClient http;
      http.begin(client, serverUrl);
      http.addHeader("Content-Type", "application/json");
      int code = http.POST(jsonPayload);
      if (code <= 0) {
        guardarEnColaOffline(evento, dist, luz, firma);
      }
      http.end();
    }
  } else {
    // Plan de contingencia: guarda localmente mientras no haya Wi-Fi
    guardarEnColaOffline(evento, dist, luz, firma);
  }
}

void sincronizarColaOffline() {
  if (totalOffline == 0 || WiFi.status() != WL_CONNECTED) return;

  Serial.println("[SYNC_OFFLINE] Sincronizando " + String(totalOffline) + " eventos almacenados...");
  if (usarBackendHttp) {
    WiFiClient client;
    HTTPClient http;
    for (int i = 0; i < totalOffline; i++) {
      String payload = "{\"evento\":\"" + colaOffline[i].evento + "\",\"distancia\":" +
                       String(colaOffline[i].distancia) + ",\"luz\":" + String(colaOffline[i].luz) +
                       ",\"firma_sha256\":\"" + colaOffline[i].firmaSha256 + "\"}";
      http.begin(client, serverUrl);
      http.addHeader("Content-Type", "application/json");
      http.POST(payload);
      http.end();
    }
  }
  totalOffline = 0;
  Serial.println("[SYNC_OFFLINE] Sincronización completada.");
}

// =========================================================================
// 6. RECEPCIÓN DE INSTRUCCIONES POR SERIAL UART (Rúbrica 3.1.3.9)
// =========================================================================
void procesarComandosSerial() {
  if (Serial.available() > 0) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    cmd.toUpperCase();

    if (cmd == "ABRIR") {
      aperturaManual = true;
      abrirBarrera();
      registrarEvento(ultimaDistancia, ultimaLuz, "APERTURA_SERIAL");
      Serial.println("[RX_SERIAL] OK -> Barrera abierta manualmente.");
    } else if (cmd == "CERRAR") {
      aperturaManual = true;
      cerrarBarrera();
      registrarEvento(ultimaDistancia, ultimaLuz, "CIERRE_SERIAL");
      Serial.println("[RX_SERIAL] OK -> Barrera cerrada manualmente.");
    } else if (cmd == "AUTO") {
      aperturaManual = false;
      Serial.println("[RX_SERIAL] OK -> Modo automatico por sensores reactivado.");
    } else if (cmd == "ESTADO") {
      Serial.println("[RX_SERIAL] " + construirJsonEstado());
    } else if (cmd.startsWith("UMBRAL_DIST:")) {
      int nuevoDist = cmd.substring(12).toInt();
      if (nuevoDist > 0 && nuevoDist <= 400) {
        umbralDistancia = nuevoDist;
        Serial.println("[RX_SERIAL] Umbral distancia actualizado a " + String(umbralDistancia) + " cm");
      }
    } else if (cmd.startsWith("UMBRAL_LUZ:")) {
      int nuevoLuz = cmd.substring(11).toInt();
      if (nuevoLuz >= 0 && nuevoLuz <= 1023) {
        umbralLuz = nuevoLuz;
        Serial.println("[RX_SERIAL] Umbral luz actualizado a " + String(umbralLuz));
      }
    }
  }
}

// =========================================================================
// 7. CONFIGURACIÓN DE ENDPOINTS HTTP PARA LA APP ANDROID
// =========================================================================
void configurarServidorWeb() {
  server.on("/abrir", HTTP_GET, []() {
    aperturaManual = true;
    abrirBarrera();
    registrarEvento(ultimaDistancia, ultimaLuz, "APERTURA_REMOTA_APP");
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", construirJsonEstado());
  });

  server.on("/cerrar", HTTP_GET, []() {
    aperturaManual = true;
    cerrarBarrera();
    registrarEvento(ultimaDistancia, ultimaLuz, "CIERRE_REMOTO_APP");
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", construirJsonEstado());
  });

  server.on("/auto", HTTP_GET, []() {
    aperturaManual = false;
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", construirJsonEstado());
  });

  server.on("/estado", HTTP_GET, []() {
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", construirJsonEstado());
  });

  server.on("/config", HTTP_GET, []() {
    if (server.hasArg("dist")) umbralDistancia = server.arg("dist").toInt();
    if (server.hasArg("luz"))  umbralLuz       = server.arg("luz").toInt();
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", construirJsonEstado());
  });

  server.on("/offline", HTTP_GET, []() {
    String lista = "[";
    for (int i = 0; i < totalOffline; i++) {
      if (i > 0) lista += ",";
      lista += "{\"evento\":\"" + colaOffline[i].evento + "\",\"distancia\":" +
               String(colaOffline[i].distancia) + ",\"luz\":" + String(colaOffline[i].luz) +
               ",\"firma_sha256\":\"" + colaOffline[i].firmaSha256 + "\"}";
    }
    lista += "]";
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", lista);
  });

  server.begin();
}

// =========================================================================
// 8. SETUP Y LOOP PRINCIPAL NO BLOQUEANTE
// =========================================================================
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n=== SISTEMA DE CONTROL DE ACCESO OT/IoT (WEMOS D1) ===");

  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  pinMode(ledVerde, OUTPUT);
  pinMode(ledRojo, OUTPUT);

  barrera.attach(servoPin);

  // Auto-test inicial de actuadores al energizar
  digitalWrite(ledVerde, HIGH);
  digitalWrite(ledRojo, HIGH);
  barrera.write(90);
  delay(600);
  cerrarBarrera();

  // Configuración Wi-Fi con ahorro energético (Rúbrica 3.1.3.10)
  WiFi.mode(WIFI_STA);
  WiFi.setSleepMode(WIFI_LIGHT_SLEEP);
  WiFi.begin(ssid, password);

  Serial.print("Conectando a Wi-Fi");
  int intentos = 0;
  while (WiFi.status() != WL_CONNECTED && intentos < 16) {
    delay(500);
    Serial.print(".");
    intentos++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("\n[WIFI_OK] IP asignada para App Android: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n[WIFI_CONTINGENCIA] Operando en modo local autónomo (reintentará en 10s).");
  }

  configurarServidorWeb();
  Serial.println("Comandos Serial disponibles: ABRIR | CERRAR | AUTO | ESTADO | UMBRAL_DIST:20 | UMBRAL_LUZ:450");
}

void loop() {
  server.handleClient();
  procesarComandosSerial();

  unsigned long ahora = millis();

  // 1. Reconexión automática no bloqueante (Rúbrica 3.1.1.3)
  if (ahora - tAnteriorReconexion >= INTERVALO_RECONEXION) {
    tAnteriorReconexion = ahora;
    if (WiFi.status() != WL_CONNECTED) {
      Serial.println("[WIFI_RECONNECT] Reintentando enlace inalámbrico...");
      WiFi.reconnect();
    } else if (totalOffline > 0) {
      sincronizarColaOffline();
    }
  }

  // 2. Lectura periódica de sensores (Rúbrica 3.1.3.8)
  if (ahora - tAnteriorSensores >= INTERVALO_SENSORES) {
    tAnteriorSensores = ahora;

    // Medición HC-SR04
    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    long duracion = pulseIn(echoPin, HIGH, 30000);
    int distancia = duracion / 58;
    if (distancia == 0) distancia = 400;
    ultimaDistancia = distancia;

    // Medición LDR
    ultimaLuz = analogRead(ldrPin);

    // Lógica automática (solo si no hay anulación manual desde App o Serial)
    if (!aperturaManual) {
      bool condicionApertura = (ultimaDistancia > 0 && ultimaDistancia <= umbralDistancia && ultimaLuz < umbralLuz);

      if (condicionApertura && !estaAbierta) {
        abrirBarrera();
        registrarEvento(ultimaDistancia, ultimaLuz, "BARRERA_ABIERTA_AUTO");
      } else if (!condicionApertura && estaAbierta) {
        cerrarBarrera();
        registrarEvento(ultimaDistancia, ultimaLuz, "BARRERA_CERRADA_AUTO");
      }
    }
  }

  // 3. Envío periódico de telemetría por puerto Serial
  if (ahora - tAnteriorSerial >= INTERVALO_SERIAL) {
    tAnteriorSerial = ahora;
    Serial.print("[TX_SERIAL] Dist: ");
    Serial.print(ultimaDistancia);
    Serial.print(" cm | Luz: ");
    Serial.print(ultimaLuz);
    Serial.print(" | Barrera: ");
    Serial.print(estaAbierta ? "ABIERTA" : "CERRADA");
    Serial.print(" | Modo: ");
    Serial.println(aperturaManual ? "MANUAL" : "AUTO");
  }
}

/*
 * ====================================================================================
 * Sistema de Control de Acceso Inalámbrico OT / IoT con WeMos D1 (ESP8266)
 * ====================================================================================
 *
 * MAPA DE PINES EN WEMOS D1 (Formato Arduino UNO / ESP8266):
 * ------------------------------------------------------------------------------------
 * Componente               Pin WeMos D1    GPIO ESP8266    Detalles de Conexión
 * ------------------------------------------------------------------------------------
 * Servomotor SG90 (Señal)   D5              GPIO14          Cable Naranja / Amarillo
 * LED Verde (Estado Normal) D6              GPIO12          Ánodo (+) con Resistencia 220Ω
 * LED Rojo (Alerta / Open)  D7              GPIO13          Ánodo (+) con Resistencia 220Ω
 * HC-SR04 Trig              D1              GPIO5           Entrada de Disparo
 * HC-SR04 Echo              D2              GPIO4           Salida de Eco
 * Sensor de Luz LDR         A0              ADC0            Divisor de Tensión LDR + 10k
 * Servomotor VCC / GND      5V / GND        5V / GND        Alimentación
 * ------------------------------------------------------------------------------------
 */

#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <Servo.h>

// Credenciales Wi-Fi (Configurar el Hotspot / Zona Wi-Fi del Smartphone)
const char* ssid = "ZONA_WIFI_CONTROL_ACCESO";
const char* password = "ClaveSecretaControlAcceso2026";

// Servidor Web HTTP en Puerto 80
ESP8266WebServer server(80);
Servo servoBarrera;

// Asignación de Pines WeMos D1
const int PIN_SERVO     = D5; // GPIO14 - Servomotor SG90
const int PIN_LED_VERDE = D6; // GPIO12 - LED Verde (Estado Normal)
const int PIN_LED_ROJO  = D7; // GPIO13 - LED Rojo (Estado Alerta / Barrera Abierta)
const int PIN_TRIG      = D1; // GPIO5  - Sensor Ultrasonido Trig
const int PIN_ECHO      = D2; // GPIO4  - Sensor Ultrasonido Echo
const int PIN_LDR       = A0; // ADC0   - Sensor de Luz LDR

// Estado global de la barrera
String estadoBarrera = "cerrada"; // "abierta" o "cerrada"

// Medir distancia con HC-SR04 (en centímetros)
long medirDistanciaCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  long duracion = pulseIn(PIN_ECHO, HIGH, 25000); // 25ms timeout
  if (duracion == 0) return 999;
  return duracion * 0.034 / 2;
}

// Actualizar hardware físico de forma sincronizada
void actualizarActuadores(bool abrir) {
  if (abrir) {
    estadoBarrera = "abierta";
    servoBarrera.write(90);             // Servomotor a 90° (Barrera Abierta)
    digitalWrite(PIN_LED_ROJO, HIGH);   // LED Rojo ENTRADA ABIERTA / ALERTA (ON)
    digitalWrite(PIN_LED_VERDE, LOW);   // LED Verde (OFF)
  } else {
    estadoBarrera = "cerrada";
    servoBarrera.write(0);              // Servomotor a 0° (Barrera Cerrada)
    digitalWrite(PIN_LED_ROJO, LOW);    // LED Rojo (OFF)
    digitalWrite(PIN_LED_VERDE, HIGH);  // LED Verde REPOSO (ON)
  }
}

// Endpoint HTTP GET /abrir
void handleAbrir() {
  actualizarActuadores(true);
  int luz = analogRead(PIN_LDR);
  long dist = medirDistanciaCm();

  String json = "{\"barrera\":\"abierta\",\"luz\":" + String(luz) + ",\"distancia\":" + String(dist) + "}";
  server.send(200, "application/json", json);
}

// Endpoint HTTP GET /cerrar
void handleCerrar() {
  actualizarActuadores(false);
  int luz = analogRead(PIN_LDR);
  long dist = medirDistanciaCm();

  String json = "{\"barrera\":\"cerrada\",\"luz\":" + String(luz) + ",\"distancia\":" + String(dist) + "}";
  server.send(200, "application/json", json);
}

// Endpoint HTTP GET /estado
void handleEstado() {
  int luz = analogRead(PIN_LDR);
  long dist = medirDistanciaCm();

  String json = "{\"barrera\":\"" + estadoBarrera + "\",\"luz\":" + String(luz) + ",\"distancia\":" + String(dist) + "}";
  server.send(200, "application/json", json);
}

void setup() {
  Serial.begin(115200);
  delay(200);

  // Configuración de Pines
  pinMode(PIN_LED_VERDE, OUTPUT);
  pinMode(PIN_LED_ROJO, OUTPUT);
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);

  // Adjuntar Servo en D5
  servoBarrera.attach(PIN_SERVO);

  // Estado Inicial: Reposo -> Cerrada -> Servo 0° -> LED Verde ON, LED Rojo OFF
  actualizarActuadores(false);

  // Conexión Inalámbrica a Zona Wi-Fi
  Serial.println();
  Serial.print("Conectando a Hotspot Wi-Fi: ");
  Serial.println(ssid);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();
  Serial.println("=================================================");
  Serial.println("¡CONEXIÓN WI-FI ESTABLECIDA EXITOSAMENTE!");
  Serial.print("Dirección IP WeMos D1: ");
  Serial.println(WiFi.localIP());
  Serial.println("=================================================");

  // Registrar Endpoints HTTP REST
  server.on("/abrir", HTTP_GET, handleAbrir);
  server.on("/cerrar", HTTP_GET, handleCerrar);
  server.on("/estado", HTTP_GET, handleEstado);

  server.begin();
  Serial.println("Servidor HTTP REST WeMos D1 escuchando en puerto 80");
}

void loop() {
  server.handleClient();
}

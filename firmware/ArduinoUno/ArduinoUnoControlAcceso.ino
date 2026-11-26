/*
 * SistemaControlAcceso - Arduino Uno (control fisico)
 * Sensores y actuadores conectados al Uno. El Wemos solo proporciona Wi-Fi/Firebase.
 *
 * Cableado propuesto (ajustar si el montaje existente usa otros pines):
 * HC-SR04: TRIG D8, ECHO D9
 * Servo: D6
 * LED rojo: D4, LED verde: D5 (usar resistencias de 220-330 ohm)
 * LDR: A0 en divisor resistivo
 * UART hacia Wemos: Uno RX D10 <- Wemos TX D6; Uno TX D11 -> divisor -> Wemos RX D5
 * GND Uno y Wemos común. No alimentar el servo desde el pin 3.3V del Wemos.
 */
#include <SoftwareSerial.h>
#include <Servo.h>

const byte PIN_TRIG = 8;
const byte PIN_ECHO = 9;
const byte PIN_SERVO = 6;
const byte PIN_LED_RED = 4;
const byte PIN_LED_GREEN = 5;
const byte PIN_LDR = A0;
const byte PIN_WEMOS_RX = 10;
const byte PIN_WEMOS_TX = 11;

SoftwareSerial wemosSerial(PIN_WEMOS_RX, PIN_WEMOS_TX); // RX, TX
Servo barrierServo;

enum OperationMode { MODE_AUTO, MODE_OPEN, MODE_CLOSED };
OperationMode operationMode = MODE_AUTO;
int distanceThreshold = 20;
int lightThreshold = 400;
bool emergency = false;
int distanceCm = 300;
int lightValue = 0;
int servoAngle = 0;
unsigned long lastSample = 0;
unsigned long lastReport = 0;
long telemetrySequence = 0;
String inputLine;

int readDistanceCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);
  const unsigned long duration = pulseIn(PIN_ECHO, HIGH, 30000UL);
  if (duration == 0) return 300;
  const int cm = (int)(duration * 0.0343f / 2.0f);
  return constrain(cm, 2, 300);
}

void applyOutputs() {
  bool open = false;
  if (!emergency) {
    if (operationMode == MODE_OPEN) open = true;
    else if (operationMode == MODE_CLOSED) open = false;
    else open = (distanceCm < distanceThreshold) && (lightValue < lightThreshold);
  }
  servoAngle = open ? 90 : 0;
  barrierServo.write(servoAngle);
  digitalWrite(PIN_LED_RED, open ? HIGH : LOW);
  digitalWrite(PIN_LED_GREEN, open ? LOW : HIGH);
}

void sendTelemetry() {
  const char *modeName = operationMode == MODE_AUTO ? "AUTO" :
                         operationMode == MODE_OPEN ? "OPEN" : "CLOSED";
  wemosSerial.print("S|");
  wemosSerial.print(++telemetrySequence); wemosSerial.print("|");
  wemosSerial.print(distanceCm); wemosSerial.print("|");
  wemosSerial.print(lightValue); wemosSerial.print("|");
  wemosSerial.print(servoAngle); wemosSerial.print("|");
  wemosSerial.print(modeName); wemosSerial.print("|");
  wemosSerial.print(emergency ? 1 : 0); wemosSerial.print("|");
  wemosSerial.println(millis());
}

void processCommand(String line) {
  line.trim();
  if (!line.startsWith("C|")) return;
  // C|seq|SET_CONFIG|AUTO/OPEN/CLOSED|distanceThreshold|lightThreshold|emergency
  const int fieldsNeeded = 7;
  String fields[fieldsNeeded];
  int fieldIndex = 0;
  int start = 0;
  for (int i = 0; i <= line.length() && fieldIndex < fieldsNeeded; i++) {
    if (i == line.length() || line.charAt(i) == '|') {
      fields[fieldIndex++] = line.substring(start, i);
      start = i + 1;
    }
  }
  if (fieldIndex != fieldsNeeded || fields[0] != "C" || fields[2] != "SET_CONFIG") return;

  const long commandSeq = fields[1].toInt();
  if (commandSeq <= 0) return;

  if (fields[3] == "OPEN") operationMode = MODE_OPEN;
  else if (fields[3] == "CLOSED") operationMode = MODE_CLOSED;
  else operationMode = MODE_AUTO;

  distanceThreshold = constrain(fields[4].toInt(), 2, 300);
  lightThreshold = constrain(fields[5].toInt(), 0, 1023);
  emergency = fields[6].toInt() == 1;

  applyOutputs();
  Serial.print("Orden aplicada desde Wemos. Secuencia: ");
  Serial.println(commandSeq);
}

void setup() {
  Serial.begin(115200);
  wemosSerial.begin(9600);
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_LED_RED, OUTPUT);
  pinMode(PIN_LED_GREEN, OUTPUT);
  barrierServo.attach(PIN_SERVO);
  barrierServo.write(0);
  digitalWrite(PIN_LED_RED, LOW);
  digitalWrite(PIN_LED_GREEN, HIGH);
  inputLine.reserve(100);
  Serial.println("Arduino Uno listo; esperando Wemos por UART.");
}

void loop() {
  while (wemosSerial.available()) {
    const char c = (char)wemosSerial.read();
    if (c == '\n') {
      processCommand(inputLine);
      inputLine = "";
    } else if (c != '\r' && inputLine.length() < 100) {
      inputLine += c;
    } else if (inputLine.length() >= 100) {
      inputLine = "";
    }
  }

  if (millis() - lastSample >= 250) {
    lastSample = millis();
    distanceCm = readDistanceCm();
    lightValue = analogRead(PIN_LDR);
    applyOutputs();
  }

  if (millis() - lastReport >= 1000) {
    lastReport = millis();
    sendTelemetry();
  }
}

# VitiAlert / Vitic-AI — Backend

Backend **orquestador** del Sistema de Soporte de Decisiones para gestión hídrica en
vitivinicultura. Tesis de Licenciatura en Análisis de Datos.

Java 21 · Spring Boot 3.3 · Spring Web · Spring Data JPA · Bean Validation · Flyway · PostgreSQL · Maven

---

## 1. Objetivo

El backend Java es el punto en el que se juntan las tres fuentes de información del sistema
y desde el que se gobierna la electroválvula del nodo físico.

```
        IoT (ESP32)
              \
Meteorología ---> BACKEND JAVA ---> dataset / features
(Data Miner)  /                ---> futuro modelo ML
             /                 ---> recomendación de riego
   Satélite
(VitiAlert Python)
```

Reparto de responsabilidades:

| Java (este proyecto)                 | Python (módulos existentes)          |
|--------------------------------------|--------------------------------------|
| Recibir y validar la telemetría      | Data Miner (Open-Meteo ERA5, scraping) |
| Persistir de forma inmutable         | VitiAlert satelital (GOES, Sentinel, NDVI) |
| Reconstruir eventos de riego         | Entrenamiento y evaluación del modelo |
| Construir features temporales        | Serialización (.pkl)                  |
| Integrar y orquestar las fuentes     | Inferencia                            |
| Consultar la inferencia              |                                       |
| Tomar la decisión final y responder al ESP32 |                               |

**Java nunca carga un `.pkl`.** La única vía hacia el modelo es HTTP.

---

## 2. Arquitectura

```
ESP32  ──POST /api/data──►  Spring Boot  ──►  PostgreSQL
                                │
                                ├──► Data Miner        (CSV import / REST opcional)
                                ├──► VitiAlert satelital (HTTP, opcional)
                                └──► API Python de ML   (HTTP, opcional)
                                          │
                                          ▼
                                 decisión de riego  ──►  ESP32
```

Arquitectura por capas, sin microservicios, sin colas y sin caché distribuida:

```
controller/   endpoints REST, sin lógica de negocio
service/      reglas, features, agregación, decisión, importación, exportación
repository/   consultas Spring Data JPA, siempre acotadas por nodo y ventana temporal
domain/       entidades JPA y enums
dto/          contratos de entrada y salida (JSON snake_case)
mapper/       entidad <-> DTO
client/       integraciones HTTP con los servicios Python
config/       propiedades, clientes HTTP, seguridad opcional, OpenAPI
exception/    @RestControllerAdvice y respuesta de error uniforme
```

---

## 3. Contrato con el ESP32 (no cambia)

**Request** — `POST /api/data`

```json
{
  "nodo_id": "1",
  "temperatura_ambiente_c": 28.4,
  "humedad_relativa_pct": 45,
  "humedad_suelo_pct": 22,
  "humedad_suelo_raw": 2730,
  "velocidad_viento_kmh": 18.5,
  "caudal_l_min": 7.80,
  "volumen_total_l": 124.60,
  "valvula_abierta_actual": true,
  "decision_riego_local": true
}
```

**Response**

```json
{
  "abrir_valvula": true,
  "encender_luz": true
}
```

Mientras no exista un motor de decisión avanzado habilitado:

```
abrir_valvula = encender_luz = decision_riego_local
```

La arquitectura ya contempla la prioridad futura:

```
decisión backend  →  si existe y es confiable  →  se usa
                  →  si no                     →  fallback a decision_riego_local
```

El nodo nuevo se da de alta solo la primera vez que envía datos
(`vitialert.node.auto-register=true`), así que **apuntar la maqueta al backend Java solo
requiere cambiar la URL del servidor.**

### curl de prueba

```bash
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{
    "nodo_id": "1",
    "temperatura_ambiente_c": 28.4,
    "humedad_relativa_pct": 45,
    "humedad_suelo_pct": 22,
    "humedad_suelo_raw": 2730,
    "velocidad_viento_kmh": 18.5,
    "caudal_l_min": 7.80,
    "volumen_total_l": 124.60,
    "valvula_abierta_actual": true,
    "decision_riego_local": true
  }'
```

---

## 4. Cómo ejecutar

### Requisitos

- Java 21
- Maven 3.9+
- PostgreSQL 14+

### Base de datos

```sql
CREATE DATABASE vitialert;
CREATE USER vitialert WITH PASSWORD 'vitialert';
GRANT ALL PRIVILEGES ON DATABASE vitialert TO vitialert;
```

Flyway crea todo el esquema en el primer arranque (`V1__init_schema.sql`).

### Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/vitialert` | URL JDBC |
| `DB_USERNAME` | `vitialert` | usuario |
| `DB_PASSWORD` | `vitialert` | contraseña |
| `SERVER_PORT` | `8080` | puerto HTTP |
| `NODE_OFFLINE_AFTER_MINUTES` | `10` | minutos sin datos para marcar el nodo offline |
| `SATELLITE_ENABLED` / `SATELLITE_BASE_URL` | `false` / `http://localhost:8000` | VitiAlert satelital |
| `WEATHER_PULL_ENABLED` / `WEATHER_BASE_URL` | `false` / `http://localhost:8001` | pull REST del Data Miner |
| `ML_ENABLED` / `ML_BASE_URL` | `false` / `http://localhost:8002` | API Python de inferencia |
| `NODE_KEY_ENABLED` | `false` | exige `X-Node-Key` en `/api/data` |
| `ADMIN_KEY_ENABLED` / `ADMIN_KEY` | `false` / vacío | exige `X-Admin-Key` en `/api/admin/**` |

Hay un `.env.example` como plantilla. **El backend no guarda contraseñas de WiFi.**

### Comandos

```bash
mvn clean verify          # compila y ejecuta todos los tests (H2 en memoria)
mvn spring-boot:run       # arranca el backend
mvn clean package && java -jar target/vitialert-backend-0.1.0.jar
```

Windows (PowerShell):

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/vitialert"
$env:DB_USERNAME="vitialert"
$env:DB_PASSWORD="vitialert"
mvn spring-boot:run
```

### Swagger / OpenAPI

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

---

## 5. Endpoints

### IoT

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/data` | recepción de telemetría del ESP32 |
| GET | `/api/nodes` | lista de nodos |
| GET | `/api/nodes/{nodeId}` | detalle del nodo |
| GET | `/api/nodes/{nodeId}/telemetry/latest` | última lectura |
| GET | `/api/nodes/{nodeId}/telemetry?from&to&page&size` | histórico paginado |
| GET | `/api/nodes/{nodeId}/irrigation-events?from&to&page&size` | eventos de riego |
| GET | `/api/nodes/{nodeId}/irrigation-events/current` | riego en curso |
| GET | `/api/nodes/{nodeId}/status` | estado operativo (online/offline) |
| GET | `/api/nodes/{nodeId}/decisions?page&size` | trazabilidad de decisiones |
| GET | `/api/nodes/{nodeId}/features?at` | features temporales IoT |
| GET | `/api/nodes/{nodeId}/aggregations?from&to&granularity` | agregación temporal |
| GET | `/api/health` | estado del backend y de las integraciones |

### Administración

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/admin/weather/import` | importa el CSV diario del Data Miner (`file`, `source`) |
| GET | `/api/admin/dataset/export?nodeId&from&to&granularity` | exporta el dataset unificado en CSV |

`from` / `to` aceptan ISO-8601 (`2026-09-03T10:00:00Z`) o fecha suelta (`2026-09-03`,
interpretada como el comienzo del día en UTC). Todo el backend trabaja en **UTC**.

Ejemplo de `status`:

```json
{
  "node_id": "1",
  "last_seen": "2026-09-03T14:32:11Z",
  "online": true,
  "offline_after_minutes": 10,
  "latest": { "...": "..." },
  "current_irrigation": { "...": "..." }
}
```

Ejemplos de administración:

```bash
curl -X POST http://localhost:8080/api/admin/weather/import \
  -F "file=@dataset_meteorologico.csv" \
  -F "source=OPEN_METEO_ERA5"

curl "http://localhost:8080/api/admin/dataset/export?nodeId=1&from=2026-08-01&to=2026-09-01&granularity=HOURLY" \
  -o dataset.csv
```

---

## 6. Modelo de datos

| Tabla | Contenido |
|---|---|
| `node` | nodo físico (ESP32). `latitud`/`longitud` solo para resolver información espacial, **no son features** |
| `telemetry_reading` | una fila **inmutable** por cada POST; nunca se sobrescribe |
| `irrigation_event` | período real de riego reconstruido por transición de válvula |
| `decision_record` | decisión local, decisión del backend y decisión final de cada respuesta |
| `weather_observation` | meteorología diaria del Data Miner (fuente separada de la telemetría) |

Índices creados por la migración:

```
telemetry_reading (node_id, timestamp_received DESC)
telemetry_reading (node_id, timestamp_received) WHERE humedad_suelo_pct IS NOT NULL
irrigation_event  (node_id, started_at DESC) y (node_id, ended_at)
irrigation_event  UNIQUE (node_id) WHERE estado = 'OPEN'
decision_record   (node_id, decision_timestamp DESC)
weather_observation (observation_date) y UNIQUE (observation_date, source)
```

---

## 7. Calidad del dato

La validación de Bean Validation rechaza únicamente lo **físicamente imposible**:

```
-40 <= temperatura <= 70      0 <= humedad relativa <= 100
  0 <= humedad suelo <= 100   humedad_suelo_raw >= 0
  0 <= viento <= 200          caudal >= 0        volumen >= 0
```

Todo lo demás se **acepta y se marca** con un `quality_flag` y una nota legible:

| Flag | Cuándo |
|---|---|
| `VALID` | todo presente y dentro del rango operativo |
| `MISSING` | falta alguna variable central del payload |
| `SENSOR_ERROR` | el ADC quedó pegado a un extremo (0 o 4095) |
| `OUT_OF_RANGE` | valor posible pero fuera del rango operativo plausible |
| `SUSPECT` | caudal con válvula cerrada (posible fuga), válvula abierta sin caudal (obstrucción), o RAW sin cambios por demasiado tiempo (sensor congelado) |

**Ninguno de estos chequeos altera la decisión de riego.** Solo generan trazabilidad.

---

## 8. Features temporales — corrección respecto del prototipo Python

El `server.py` actual usa `shift(1)`, `shift(3)`, `shift(6)`, `shift(24)`, que son
desplazamientos **por posición de fila**. Como el ESP32 transmite cada pocos segundos,
`shift(1)` no es "hace una hora": es "la observación anterior".

En Java los lags se resuelven **siempre por timestamp real**: se busca la observación más
próxima a `t - Xh` dentro de una tolerancia de `min(30 min, 25 % del lag)`. Si no existe
ninguna, el valor es **`null` / celda vacía, nunca cero** — un cero artificial sería
indistinguible de "suelo completamente seco" y arruinaría el entrenamiento.

Features IoT calculadas hoy: `soil_moisture_lag_1h/3h/6h/24h`, `soil_moisture_slope_3h/12h`
(puntos porcentuales por hora), `soil_moisture_mean_24h`, `irrigation_volume_1h/24h`.
Las meteorológicas (`precipitation_mm`, `et0_mm`, `vpd_kpa`, `solar_radiation`) provienen
de `weather_observation` y se integran en el dataset exportado.

---

## 9. Agregación temporal y dataset

Nunca se asume "un registro = un minuto" ni "un registro = una hora". Los buckets se
calculan truncando el timestamp real (`FIVE_MINUTES`, `FIFTEEN_MINUTES`, `HOURLY`) y cada
fila expone su `sample_count` para poder descartar buckets con poca cobertura.

El tiempo de válvula abierta se integra sobre los intervalos reales entre lecturas,
ignorando huecos mayores a `vitialert.aggregation.max-gap-seconds` (un hueco es nodo
offline, no riego continuo). El consumo de agua se calcula sumando los incrementos del
contador acumulado; un salto negativo se interpreta como reinicio del ESP32 y no como
consumo.

Columnas del CSV exportado:

```
timestamp, node_id, soil_moisture_pct,
soil_moisture_lag_1h, soil_moisture_lag_3h, soil_moisture_lag_6h, soil_moisture_lag_24h,
soil_moisture_slope_3h, soil_moisture_slope_12h, soil_moisture_mean_24h,
temperature_c, relative_humidity_pct, wind_speed_kmh, flow_l_min,
irrigation_volume_1h, irrigation_volume_24h,
precipitation_mm, et0_mm, vpd_kpa, solar_radiation,
sample_count, valve_open_seconds,
soil_moisture_t_plus_24h
```

El target `soil_moisture_t_plus_24h` se obtiene buscando el bucket cuyo inicio es
exactamente `t + 24h`. **No se usa `shift(24)`**: con huecos, `shift` corre el target y
contamina el entrenamiento; acá un hueco produce simplemente un target faltante.

---

## 10. Decisión de riego

`IrrigationDecisionService` implementa la prioridad backend → fallback local. Mientras
`vitialert.ml.enabled=false` **no se calculan features en el camino caliente ni se hace
ninguna llamada de red**: la respuesta al ESP32 se mantiene mínima.

Cada respuesta queda registrada en `decision_record` con `decision_local`,
`decision_backend`, `decision_final`, `accion` (`ABRIR` / `CERRAR` / `MANTENER`), `motivo`,
`source` (`LOCAL_FALLBACK`, `BACKEND_RULES`, `ML_MODEL`, `MANUAL`) y `model_version`.

**No hay ninguna IA ficticia ni lógica meteorológica inventada.**

---

## 11. Resiliencia de las integraciones

Si VitiAlert satelital, el Data Miner o la API de inferencia están caídos:

- la telemetría **se sigue aceptando y persistiendo**;
- se registra la indisponibilidad con `WARN`;
- la válvula cae al fallback local.

Los clientes HTTP tienen timeouts cortos y devuelven `Optional.empty()` en lugar de
propagar excepciones. Hay un test dedicado que apunta las integraciones a puertos cerrados.

---

## 12. Errores

`@RestControllerAdvice` con respuesta uniforme:

```json
{
  "timestamp": "2026-09-03T14:32:11.482Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "El payload no cumple las validaciones de rango del sistema.",
  "path": "/api/data",
  "details": { "temperaturaAmbienteC": "temperatura_ambiente_c debe ser <= 70" }
}
```

---

## 13. Seguridad

Deliberadamente mínima para no romper el prototipo, sin Spring Security y sin OAuth para
el ESP32:

- `X-Node-Key` sobre `/api/data`, clave por nodo en `node.api_key`
  (`NODE_KEY_ENABLED=true` para activarla);
- `X-Admin-Key` sobre `/api/admin/**` (`ADMIN_KEY_ENABLED=true` + `ADMIN_KEY=...`).

Ambas desactivadas por defecto para el ambiente local.

---

## 14. Tests

```bash
mvn test
```

JUnit 5 + H2 en memoria (modo PostgreSQL). Cubren:

- POST válido con el payload real del ESP32 y persistencia de la lectura;
- validaciones de rango (temperatura, humedad, caudal, viento, `nodo_id`);
- payload incompleto aceptado y marcado `MISSING`;
- caudal con válvula cerrada marcado `SUSPECT`;
- apertura y cierre de evento de riego;
- cálculo de volumen aplicado, duración y caudal promedio;
- reinicio del contador del ESP32 → `CLOSED_WITH_WARNING`;
- lags por timestamp real con ruido de alta frecuencia (el test falla si se usa `shift`);
- tolerancia proporcional del lag;
- media de 24 h;
- agregación horaria, integración de válvula abierta y consumo de agua;
- huecos de datos que no se contabilizan como riego;
- exportación del dataset: cabecera, lags/target por timestamp y celdas vacías;
- integración de la meteorología diaria;
- importación CSV idempotente, separador `;`, fechas `dd/MM/yyyy` y celdas vacías → `null`;
- nodo online / offline / sin telemetría;
- satélite y modelo caídos → telemetría guardada + fallback local;
- respuesta `abrir_valvula` / `encender_luz`.

---

## 15. Decisiones técnicas

1. **Sin Lombok.** Menos magia, cero riesgo de configuración de procesadores de anotaciones
   y código más defendible en la revisión de tesis. Los DTO son `record`, así que la
   verbosidad queda acotada a las entidades.
2. **`BigDecimal` para agua, `Double` para ambiente.** El volumen es un contador acumulado
   sobre el que se hacen restas: ahí la precisión importa. La temperatura o la humedad
   están limitadas por el sensor, no por el tipo.
3. **`ddl-auto: none` + Flyway** como única fuente de verdad del esquema en producción.
   En los tests Hibernate genera el esquema en H2 porque la migración usa índices
   parciales propios de PostgreSQL.
4. **Sin interfaces vacías.** `PredictionService`, `SatelliteClient` y `WeatherClient` son
   clases concretas: no hay una segunda implementación que justifique una abstracción.
5. **Alta automática de nodos.** Permite conectar la maqueta actual sin ninguna carga previa.
6. **Filtro de clave propio en lugar de Spring Security.** El requisito es una cabecera
   opcional; agregar Spring Security implicaría una cadena de filtros y un modelo de
   usuarios que el prototipo no necesita.
7. **CSV escrito a mano** (parser y writer mínimos) en lugar de una dependencia extra.
8. **Agregación en memoria** en lugar de `date_trunc` nativo: es portable, testeable y el
   rango está acotado por `vitialert.aggregation.max-range-days`.
9. **Timestamp del backend.** El ESP32 todavía no envía su propio timestamp; cuando lo
   haga se agrega una columna `timestamp_sensor` sin romper el contrato.
10. **Sin dirección de viento, sin granizo, sin Random Forest heredado.** No hay ninguna
    referencia funcional a esas variables.

---

## 16. Pendientes explícitos para la siguiente etapa

1. Servicio Python de inferencia (`POST /predict`) y activación de `vitialert.ml.enabled`.
   El contrato ya está definido en `PredictionRequest` / `PredictionResponse`.
2. Integrar las variables satelitales al dataset exportado (hoy `SatelliteClient` consulta
   pero no persiste): falta la tabla `satellite_observation` y su columna en el CSV.
3. Persistir la meteorología por nodo/coordenada cuando haya más de una finca.
4. Reglas propias del backend (`BACKEND_RULES`) como escalón intermedio entre el fallback
   local y el modelo.
5. Endpoint de control manual (`MANUAL`) para forzar apertura o cierre desde una UI.
6. Cierre automático de eventos de riego que quedan `OPEN` porque el nodo se cayó con la
   válvula abierta (hoy quedan abiertos hasta que el nodo reporta el cierre).
7. `timestamp_sensor` enviado por el firmware, para separar el momento de la medición del
   momento de la recepción.
8. Autenticación real de los endpoints administrativos si el sistema sale del laboratorio.
9. Materializar la serie horaria en una tabla si el volumen de datos hace lenta la
   agregación en memoria.

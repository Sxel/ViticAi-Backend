# VitiAlert / Vitic-AI — Backend

Backend del Sistema de Soporte de Decisiones para gestión hídrica en vitivinicultura.
Tesis de Licenciatura en Análisis de Datos.

Java 21 · Spring Boot 3.3 · Spring Data JPA · Bean Validation · Flyway · PostgreSQL · Maven

---

## Qué hace, en una frase

> Recibe los datos del ESP32, los guarda, reconstruye los eventos de riego, integra la
> meteorología y el contexto satelital, construye el dataset y —más adelante— consulta el
> modelo de Ciencia de Datos.

## Arquitectura

```
                       ESP32
                         │  POST /api/data
                         ▼
                   Backend Java  ──────►  PostgreSQL
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
  Data Miner          VitiAI          Modelo Python
  (histórico        (features          (entrena e
 meteorológico)      satelitales)        infiere)
       │                 │                 │
       └─────────────────┴─────────────────┘
                         │
                 recomendación de riego
                         ▼
                       ESP32
```

Cada pieza tiene una sola responsabilidad:

| Componente | Responsabilidad |
|---|---|
| **Backend (este proyecto)** | Integra y almacena. Valida, persiste, reconstruye riegos, construye el dataset |
| **Data Miner** (Python) | Genera el histórico meteorológico (Open-Meteo ERA5 + scraping) |
| **VitiAI** (Python) | Genera las features satelitales (GOES, Sentinel) |
| **Modelo ML** (Python) | Entrena, evalúa e infiere |

**Java nunca carga un `.pkl`.** La única vía hacia el modelo es HTTP.

## El flujo completo

1. El ESP32 envía datos.
2. Java los valida y los guarda.
3. Java reconstruye los eventos de riego.
4. El histórico meteorológico se importa desde el Data Miner.
5. VitiAI puede consultarse para obtener contexto satelital.
6. Java genera una serie horaria.
7. Java exporta un dataset.
8. Python entrena el modelo.
9. En el futuro Java consulta ese modelo.
10. Si el modelo no está, usa `decision_riego_local`.

---

## Contrato con el ESP32 (no cambia)

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
{ "abrir_valvula": true, "encender_luz": true }
```

Mientras el modelo esté apagado: `abrir_valvula = encender_luz = decision_riego_local`.

El nodo se da de alta solo en su primer POST, así que **apuntar la maqueta al backend solo
requiere cambiar la URL del servidor**.

```bash
curl -X POST http://localhost:8080/api/data \
  -H "Content-Type: application/json" \
  -d '{"nodo_id":"1","temperatura_ambiente_c":28.4,"humedad_relativa_pct":45,
       "humedad_suelo_pct":22,"humedad_suelo_raw":2730,"velocidad_viento_kmh":18.5,
       "caudal_l_min":7.80,"volumen_total_l":124.60,
       "valvula_abierta_actual":true,"decision_riego_local":true}'
```

---

## Cómo ejecutar

**Requisitos:** Java 21, Maven 3.9+, PostgreSQL 14+.

```sql
CREATE DATABASE vitialert;
CREATE USER vitialert WITH PASSWORD 'vitialert';
GRANT ALL PRIVILEGES ON DATABASE vitialert TO vitialert;
```

Flyway crea y migra el esquema solo en el arranque.

```bash
mvn clean verify        # compila y corre los 23 tests (H2 en memoria, sin Docker)
mvn spring-boot:run     # arranca
```

> **Si no tenés Maven instalado en el sistema**, el proyecto trae una copia portátil en
> `.tools/` (ignorada por git). Usala con la ruta completa:
>
> ```powershell
> .tools\apache-maven-3.9.11\bin\mvn.cmd clean verify
> ```
>
> Se puede borrar `.tools/` en cualquier momento; instalar Maven en el sistema lo reemplaza.

| Variable | Por defecto |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/vitialert` |
| `DB_USERNAME` / `DB_PASSWORD` | `vitialert` / `vitialert` |
| `SERVER_PORT` | `8080` |
| `SATELLITE_ENABLED` / `SATELLITE_BASE_URL` | `false` / `http://localhost:8000` |
| `ML_ENABLED` / `ML_BASE_URL` | `false` / `http://localhost:8002` |

Swagger: <http://localhost:8080/swagger-ui.html>

---

## Endpoints (9)

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/data` | Telemetría del ESP32 |
| GET | `/api/nodes` | Lista de nodos |
| GET | `/api/nodes/{id}/telemetry/latest` | Última lectura |
| GET | `/api/nodes/{id}/telemetry` | Histórico paginado (`from`, `to`, `page`, `size`) |
| GET | `/api/nodes/{id}/irrigation-events` | Riegos reconstruidos |
| GET | `/api/nodes/{id}/features` | Vector de features de una hora (`at`) |
| GET | `/api/nodes/{id}/satellite` | Contexto satelital de VitiAI |
| POST | `/api/weather/import` | Importa el CSV del Data Miner (`file`, `source`) |
| GET | `/api/dataset/export` | Dataset horario en CSV (`nodeId`, `from`, `to`) |
| GET | `/health` | Estado del backend y sus integraciones |

`from` / `to` admiten ISO-8601 (`2026-09-03T10:00:00Z`) o fecha suelta (`2026-09-03`).
Todo el backend trabaja en **UTC**.

```bash
curl -X POST http://localhost:8080/api/weather/import \
  -F "file=@dataset_meteorologico.csv" -F "source=OPEN_METEO_ERA5"

curl "http://localhost:8080/api/dataset/export?nodeId=1&from=2026-08-01&to=2026-09-01" -o dataset.csv
```

---

## Estructura

```
controller/  4   TelemetryController · NodeController · DataController · RequestTimes
service/     7   TelemetryService · IrrigationService · AggregationService
                 DatasetService · WeatherService · HourlyPoint · SimpleCsvParser
client/      2   SatelliteClient (VitiAI) · PredictionClient (modelo Python)
repository/  4   Node · TelemetryReading · IrrigationEvent · WeatherObservation
domain/      6   4 entidades + 2 enums
dto/        11   Contratos de entrada y salida, en snake_case
exception/   3   Manejo uniforme de errores
config/      2   VitiAlertProperties · OpenApiConfig
```

40 clases, ~3.300 líneas.

## Modelo de datos

| Tabla | Contenido |
|---|---|
| `node` | Nodo físico. `latitud`/`longitud` solo para consultar meteorología y satélite; **no son features** |
| `telemetry_reading` | Una fila **inmutable** por POST. Nunca se sobrescribe |
| `irrigation_event` | Período real de riego, derivado de las transiciones de la válvula |
| `weather_observation` | Meteorología diaria del Data Miner (fuente separada de la telemetría) |

---

## Lo importante para la tesis

### Los lags se calculan por timestamp real

El prototipo Python usaba `shift(1)`, `shift(3)`, `shift(24)`, que desplazan **posiciones de
fila**. Con el ESP32 transmitiendo cada pocos segundos, `shift(24)` no es "hace 24 horas":
son minutos. Y si el target se construye con `shift(-24)`, el modelo predice unos minutos
hacia adelante, muestra un R² altísimo y en realidad aprendió a copiar la última medición.

Acá los lags y el target se resuelven sobre la **serie horaria**, buscando el bucket cuyo
inicio es exactamente `t − Xh` (o `t + 24h` para el target). Si ese bucket no existe, la
celda queda **vacía**.

### Un faltante es un faltante

Nunca se rellena con cero. Un `0` en `soil_moisture_lag_24h` significa "hace 24 horas el
suelo estaba completamente seco" — un valor válido del dominio que el modelo interpretaría
como tal. Con celda vacía, pandas lo lee como `NaN`, `df.isna().sum()` lo muestra, y la
decisión de imputar se toma explícitamente en el análisis.

### Una sola implementación de features

`DatasetService` es la única clase que calcula features en todo el sistema. El CSV de
entrenamiento, el endpoint de inspección y la consulta al modelo usan exactamente el mismo
código, así que no puede aparecer *training/serving skew*.

### Columnas del dataset

```
timestamp, node_id, soil_moisture_pct,
soil_moisture_lag_1h, soil_moisture_lag_3h, soil_moisture_lag_6h, soil_moisture_lag_24h,
soil_moisture_slope_3h, soil_moisture_slope_12h, soil_moisture_mean_24h,
temperature_c, relative_humidity_pct, wind_speed_kmh, flow_l_min,
irrigation_volume_1h, irrigation_volume_24h,
precipitation_mm, et0_mm, vpd_kpa, solar_radiation,
sample_count, soil_moisture_t_plus_24h
```

Notas metodológicas:

- `sample_count` permite descartar horas con poca cobertura.
- `soil_moisture_mean_24h` se publica solo si existe al menos la mitad de los buckets.
- Las variables meteorológicas son **diarias** y se replican en las 24 filas del día.
- El tiempo de válvula abierta se integra sobre los intervalos reales; un hueco mayor a
  120 s es nodo offline y no se cuenta como riego.

### Calidad del dato

Bean Validation rechaza lo **físicamente imposible** (HTTP 400):

```
-40 ≤ temperatura ≤ 70    0 ≤ humedades ≤ 100    0 ≤ viento ≤ 200
caudal ≥ 0    volumen ≥ 0    humedad_suelo_raw ≥ 0
```

Todo lo demás se acepta y se marca:

| Flag | Cuándo |
|---|---|
| `VALID` | Todo presente y coherente |
| `MISSING` | Falta alguna variable central del payload |
| `SUSPECT` | Caudal con la válvula cerrada (fuga), o válvula abierta sin caudal (obstrucción) |

**Ningún flag modifica la decisión de riego.** La detección de sensor congelado se hace en
el preprocesamiento del dataset en Python, con `diff()`.

---

## Seguridad

**La autenticación está fuera del alcance del prototipo actual.** El sistema corre en
ambiente de laboratorio, sin datos personales y sin exposición pública. No hay Spring
Security, ni claves por nodo, ni filtros propios. Si el sistema saliera del laboratorio,
haría falta HTTPS y autenticación real.

## Pendientes

1. Conectar el modelo Python (`ML_ENABLED=true`) y reintroducir `decision_record` para
   comparar decisión local, decisión del modelo y decisión final.
2. Agregar las columnas satelitales al dataset (hoy VitiAI se consulta pero no se persiste).
3. Cerrar automáticamente los riegos que quedan abiertos si el nodo se cae con la válvula
   abierta.
4. `timestamp_sensor` enviado por el firmware, para separar el momento de la medición del de
   la recepción.

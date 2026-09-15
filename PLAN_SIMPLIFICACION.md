# Plan de simplificación — VitiAlert backend

**Estado: EJECUTADO** — commits `7e95bcd` (refactor) y `6845aa8` (fix de carga perezosa),
integrados con `origin/main` en el merge `943bf3f`. `mvn clean verify` en verde: 26/26 tests.

> Este documento se conserva como registro del plan tal como se aprobó. La sección 10 al final
> anota en qué se desvió la ejecución de lo planificado.

Base analizada: 71 clases de producción (4.584 LOC), 9 clases de test (1.091 LOC),
14 endpoints, 5 entidades, 1 migración.

---

## 1. Resumen del resultado propuesto

| | Antes | Después | Δ |
|---|---|---|---|
| Clases de producción | 71 | **40** | −44 % |
| LOC de producción | 4.584 | **3.284** | −28 % |
| Clases de test | 9 | **7** | −22 % |
| Casos de test | 35 | **26** | −26 % |
| Endpoints | 14 | **10** | −29 % |
| Entidades / tablas | 5 | **4** | −20 % |
| Clases de configuración | 12 | **2** | −83 % |
| Consultas en el `POST /api/data` | 3 + 2 escrituras | **2 + 1 escritura** | −40 % |

---

## 2. Clasificación de las 71 clases actuales

Leyenda: **KEEP** se conserva igual · **SIMPLIFY** se conserva recortada ·
**MERGE** se absorbe en otra clase · **REMOVE** se elimina · **POSTPONE** se elimina ahora y
vuelve en una etapa posterior.

### `controller/` (6 clases → 4)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `TelemetryController` | 40 | **KEEP** | Contrato con el ESP32. Intocable |
| `NodeController` | 196 | **SIMPLIFY** | De 10 endpoints a 5 (quedaron 6: se sumó `/satellite`, ver 10.3). Lista, telemetría última, telemetría paginada, eventos de riego, features |
| `AdminDatasetController` | 58 | **MERGE** → `DataController` | El prefijo `/api/admin` existía para el filtro de seguridad, que se elimina |
| `AdminWeatherController` | 45 | **MERGE** → `DataController` | Ídem |
| `HealthController` | 48 | **MERGE** → `DataController` | Queda como un método de 12 líneas en `/health` |
| `RequestTimes` | 45 | **KEEP** | Parseo de fechas de query params. Pequeño y usado por 4 endpoints |

### `service/` (13 clases → 6)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `TelemetryService` | 134 | **SIMPLIFY** | Absorbe calidad y decisión. Queda en ~180 LOC |
| `IrrigationEventService` | 104 | **KEEP** (renombrar `IrrigationService`) | Máquina de estados correcta. No se toca la lógica |
| `AggregationService` | 218 | **SIMPLIFY** | Solo granularidad horaria. Se elimina el sistema genérico de granularidades |
| `DatasetExportService` | 286 | **SIMPLIFY** (renombrar `DatasetService`) | Absorbe el cálculo de features sobre la serie horaria |
| `FeatureService` | 167 | **MERGE** → `DatasetService` | Ver decisión D2. Se elimina toda la maquinaria de vecino temporal sobre lecturas crudas |
| `WeatherImportService` | 209 | **SIMPLIFY** (renombrar `WeatherService`) | Solo nuestro formato de CSV. Se eliminan los alias múltiples |
| `NodeService` | 66 | **MERGE** → `TelemetryService` | Son 2 métodos reales (`resolveForIngest`, `requireByExternalId`) |
| `QualityEvaluator` | 172 | **SIMPLIFY** + **MERGE** → `TelemetryService` | De 6 chequeos a 2. Queda como método privado de ~45 LOC |
| `QualityAssessment` | 16 | **MERGE** | Record privado dentro de `TelemetryService` |
| `IrrigationDecisionService` | 112 | **MERGE** → `TelemetryService` | Ver decisión D1. Sin `DecisionRecord` son 12 líneas |
| `DecisionOutcome` | 20 | **REMOVE** | Ídem |
| `PredictionService` | 106 | **SIMPLIFY** → `client/PredictionClient` | Pertenece a `client/`, no a `service/` |
| `SimpleCsvParser` | 64 | **SIMPLIFY** | Se conserva; se recorta la heurística de separador si molesta |

**Clase nueva:** `HourlyPoint` (record, ~25 LOC) — reemplaza a `AggregatedTelemetryDto`,
que deja de ser un DTO de API porque el endpoint `/aggregations` se elimina.

### `repository/` (5 clases → 4)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `TelemetryReadingRepository` | 125 | **SIMPLIFY** | De 9 a 5 consultas. Se eliminan `findSoilNearestBefore`, `findSoilNearestAfter`, `findLastWithDifferentRaw`, `findOldest` |
| `IrrigationEventRepository` | 59 | **KEEP** | |
| `NodeRepository` | 16 | **SIMPLIFY** | Se elimina `findByApiKey` |
| `WeatherObservationRepository` | 15 | **KEEP** | |
| `DecisionRecordRepository` | 19 | **POSTPONE** | Vuelve con el modelo |

### `domain/` (10 clases → 6)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `TelemetryReading` | 264 | **KEEP** | Inmutabilidad + builder. Núcleo del sistema |
| `IrrigationEvent` | 170 | **KEEP** | Incluye `close()` con protección de reinicio del contador |
| `Node` | 166 | **SIMPLIFY** | Se elimina `apiKey` |
| `WeatherObservation` | 196 | **KEEP** | |
| `IrrigationEventStatus` | 14 | **KEEP** | |
| `QualityFlag` | 25 | **SIMPLIFY** | De 5 valores a 3: `VALID`, `MISSING`, `SUSPECT` |
| `Granularity` | 39 | **REMOVE** | Solo horaria. Queda una constante `Duration.ofHours(1)` |
| `DecisionRecord` | 125 | **POSTPONE** | Ver decisión D1 |
| `DecisionAction` | 8 | **POSTPONE** | Ídem |
| `DecisionSource` | 17 | **POSTPONE** | Ídem |

### `dto/` (15 clases → 11)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `TelemetryRequest` | 80 | **KEEP** | Contrato del ESP32 |
| `TelemetryResponse` | 21 | **KEEP** | Ídem |
| `NodeDto` | 20 | **SIMPLIFY** | Gana un `from(Node)` estático (reemplaza al mapper) |
| `TelemetryReadingDto` | 24 | **SIMPLIFY** | Ídem |
| `IrrigationEventDto` | 21 | **SIMPLIFY** | Ídem |
| `PageResponse` | 26 | **KEEP** | Pequeño y ya funciona |
| `FeatureVector` | 47 | **SIMPLIFY** | Se alinea 1:1 con las columnas del CSV |
| `SatelliteObservationDto` | 30 | **KEEP** | Contrato con VitiAI |
| `PredictionRequest` | 18 | **KEEP** | |
| `PredictionResponse` | 20 | **KEEP** | |
| `WeatherImportResultDto` | 15 | **KEEP** | |
| `AggregatedTelemetryDto` | 38 | **MERGE** → `HourlyPoint` en `service/` | Deja de ser contrato de API |
| `NodeStatusDto` | 15 | **REMOVE** | El endpoint `/status` se elimina |
| `DecisionRecordDto` | 19 | **POSTPONE** | Con `DecisionRecord` |
| `WeatherDailyDto` | 27 | **REMOVE** | Con `WeatherClient` |

### `mapper/` (4 clases → 0)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `NodeMapper` | 24 | **MERGE** → `NodeDto.from()` | Un bean de Spring para copiar 11 campos no aporta nada |
| `TelemetryMapper` | 56 | **MERGE** → `TelemetryReadingDto.from()` + `TelemetryReading.Builder` | |
| `IrrigationEventMapper` | 24 | **MERGE** → `IrrigationEventDto.from()` | |
| `DecisionRecordMapper` | 23 | **POSTPONE** | |

### `client/` (2 clases → 2)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `SatelliteClient` | 63 | **KEEP** | Ver decisión D4. Construye su propio `RestClient` |
| `WeatherClient` | 63 | **REMOVE** | El Data Miner no expone HTTP. Código preparado para una integración inexistente |
| *(nuevo)* `PredictionClient` | ~70 | desde `PredictionService` | |

### `config/` (12 clases → 2)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `NodeProperties` | 16 | **MERGE** → `VitiAlertProperties` | |
| `QualityProperties` | 30 | **MERGE** → `VitiAlertProperties` | De 8 umbrales a 1 |
| `FeatureProperties` | 16 | **REMOVE** | La tolerancia de lag desaparece con D2 |
| `AggregationProperties` | 17 | **MERGE** → `VitiAlertProperties` | |
| `SatelliteProperties` | 15 | **MERGE** → `VitiAlertProperties` | |
| `WeatherProperties` | 25 | **SIMPLIFY** + **MERGE** | Solo queda `default-source` |
| `MlProperties` | 24 | **MERGE** → `VitiAlertProperties` | |
| `SecurityProperties` | 19 | **REMOVE** | Seguridad fuera de alcance |
| `HttpClientsConfig` | 41 | **MERGE** → los 2 clientes | Cada cliente arma su `RestClient` en su constructor |
| `SecurityConfig` | 62 | **REMOVE** | |
| `ApiKeyFilter` | 57 | **REMOVE** | |
| `OpenApiConfig` | 33 | **KEEP** | 33 líneas que dan un Swagger presentable en la defensa |

**Clase nueva:** `VitiAlertProperties` (~55 LOC) — un `record` con 5 records anidados.

### `exception/` (3 clases → 3)

| Clase | LOC | Acción | Motivo |
|---|---|---|---|
| `GlobalExceptionHandler` | 133 | **SIMPLIFY** | De 8 manejadores a 5 |
| `ApiErrorResponse` | 27 | **KEEP** | |
| `ResourceNotFoundException` | 9 | **KEEP** | |

### `tests/` (9 clases → 6)

| Clase | Casos | Acción | Motivo |
|---|---|---|---|
| `TelemetryIngestionIT` | 8 | **SIMPLIFY** → `TelemetryIngestionTest`, 6 casos | Se quita el test de sensor congelado. Se renombra a `*Test`: Surefire ignora el sufijo `IT` |
| `IrrigationEventServiceTest` | 5 | **KEEP** → `IrrigationServiceTest` | Todos metodológicamente importantes |
| `AggregationServiceTest` | 3 | **KEEP** | Buckets horarios con frecuencia irregular, huecos, validación |
| `DatasetExportServiceTest` | 3 | **SIMPLIFY** → `DatasetServiceTest`, 5 casos | Absorbe los tests de lag de `FeatureServiceTest` |
| `FeatureServiceTest` | 5 | **MERGE** → `DatasetServiceTest` | Los lags ahora viven ahí |
| `WeatherImportServiceTest` | 3 | **SIMPLIFY** → 2 | Se quita el test de alias exóticos |
| `ExternalServicesDownIT` | 4 | **SIMPLIFY** → `ExternalServicesDownTest`, 2 casos | Se quita el test de `WeatherClient`. Se renombra a `*Test` por el mismo motivo |
| `NodeStatusTest` | 4 | **REMOVE** | El endpoint `/status` se elimina |
| `TestSupport` | — | **KEEP** | |

---

## 3. Estructura final propuesta (39 clases · ejecutadas: 40, ver 10.3)

```
com.vitialert.backend
├── VitiAlertApplication                        1
│
├── config/                                     2
│   ├── VitiAlertProperties        (1 record con 5 grupos anidados)
│   └── OpenApiConfig
│
├── controller/                                 4
│   ├── TelemetryController        POST /api/data
│   ├── NodeController             GET  /api/nodes/**
│   ├── DataController             POST /api/weather/import · GET /api/dataset/export · GET /health
│   └── RequestTimes               (utilitario de parseo de fechas)
│
├── service/                                    6
│   ├── TelemetryService           ingesta + calidad + decisión + consultas
│   ├── IrrigationService          máquina de estados del riego
│   ├── AggregationService         serie horaria
│   ├── DatasetService             features sobre la serie horaria + CSV
│   ├── WeatherService             import del CSV del Data Miner
│   └── HourlyPoint                (record de la serie horaria)
│
├── client/                                     2
│   ├── SatelliteClient            VitiAI  (fail-soft, off por defecto)
│   └── PredictionClient           API Python de ML (fail-soft, off por defecto)
│
├── repository/                                 4
│   ├── NodeRepository
│   ├── TelemetryReadingRepository
│   ├── IrrigationEventRepository
│   └── WeatherObservationRepository
│
├── domain/                                     6
│   ├── Node · TelemetryReading · IrrigationEvent · WeatherObservation
│   └── IrrigationEventStatus · QualityFlag
│
├── dto/                                       11
│   ├── TelemetryRequest · TelemetryResponse
│   ├── NodeDto · TelemetryReadingDto · IrrigationEventDto · PageResponse
│   ├── FeatureVector
│   ├── SatelliteObservationDto · PredictionRequest · PredictionResponse
│   └── WeatherImportResultDto
│
└── exception/                                  3
    └── GlobalExceptionHandler · ApiErrorResponse · ResourceNotFoundException
```

## 4. Endpoints finales (9 planificados · 10 ejecutados, ver 10.3)

| Método | Ruta | Nota |
|---|---|---|
| POST | `/api/data` | Sin cambios. Contrato del ESP32 |
| GET | `/api/nodes` | |
| GET | `/api/nodes/{nodeId}/telemetry/latest` | |
| GET | `/api/nodes/{nodeId}/telemetry` | `from`, `to`, `page`, `size` |
| GET | `/api/nodes/{nodeId}/irrigation-events` | `from`, `to`, `page`, `size` |
| GET | `/api/nodes/{nodeId}/features` | **Ver decisión D3** |
| POST | `/api/weather/import` | Antes `/api/admin/weather/import` |
| GET | `/api/dataset/export` | Antes `/api/admin/dataset/export`. Siempre horario |
| GET | `/health` | Antes `/api/health` |

**Eliminados (5):** `/api/nodes/{id}`, `/irrigation-events/current`, `/status`,
`/decisions`, `/aggregations`.

## 5. Configuración final

```yaml
vitialert:
  node:
    auto-register: true
  quality:
    flow-noise-threshold-l-min: 0.2
  dataset:
    max-gap-seconds: 120
    max-range-days: 31
  satellite:
    enabled: false
    base-url: http://localhost:8000
    timeout-ms: 3000
  ml:
    enabled: false
    base-url: http://localhost:8002
    timeout-ms: 1500
```

De 8 clases de properties a **1 record con 5 grupos anidados**.

---

## 6. Decisiones que necesitan tu aprobación

### D1 — `DecisionRecord`: eliminar ahora (opción A)

**Recomendación: opción A, eliminarla.** El argumento es que **hoy no guarda ni un solo
dato nuevo**. Campo por campo:

| Campo de `decision_record` | Qué vale hoy | Dónde ya está |
|---|---|---|
| `decision_local` | el valor del payload | `telemetry_reading.decision_riego_local` |
| `decision_backend` | siempre `null` | — |
| `decision_final` | = `decision_local` | `telemetry_reading.decision_riego_local` |
| `accion` | derivable | `decision_riego_local` vs `valvula_abierta_actual` |
| `motivo` | siempre el mismo texto | — |
| `source` | siempre `LOCAL_FALLBACK` | — |
| `model_version` | siempre `null` | — |
| `decision_timestamp` | = el de la lectura | `telemetry_reading.timestamp_received` |

Es una tabla que duplica una columna. Cuesta 1 INSERT por POST, 1 entidad, 1 repositorio, 1
mapper, 1 DTO, 1 endpoint, 2 enums y ~211 LOC, y a cambio no permite responder **ninguna**
pregunta que `telemetry_reading` no responda ya.

**La trazabilidad no se pierde**, que es lo importante: la decisión del ESP32 en cada
instante sigue estando registrada, fila por fila, en la telemetría.

**Cuándo vuelve.** En el mismo commit que enciende el modelo, porque ahí sí aparece
información nueva (`decision_backend`, `source`, `model_version`) y ahí sí hace falta
comparar las tres decisiones. Reintroducirla es 1 entidad + 1 migración + 1 INSERT: el
trabajo que se ahorra hoy no se pierde, se posterga.

### D2 — Features: calcularlas **solo** sobre la serie horaria

Hoy hay **dos** implementaciones distintas de lags:

1. `FeatureService` — busca el vecino temporal más cercano sobre lecturas crudas, con
   tolerancia proporcional y 2 consultas por lag. La usa el camino caliente y `/features`.
2. `DatasetExportService` — busca el bucket exacto `t − Xh` sobre la serie horaria. La usa
   el export.

**Propuesta: quedarse solo con la 2 y borrar la 1.** Se eliminan ~150 LOC, 2 consultas de
repositorio y 1 clase de configuración.

Además de simplificar, **corrige un riesgo metodológico real**: hoy, el modelo se entrenaría
con features calculadas por el método 2 y, al encenderse, recibiría features calculadas por
el método 1. Eso es *training/serving skew* — el modelo predice peor sin que nada falle, y es
de los bugs más difíciles de detectar. Con una sola implementación, entrenamiento e
inferencia usan exactamente el mismo cálculo por construcción.

**Lo que NO se pierde:** los lags siguen resolviéndose por timestamp real, un hueco sigue
produciendo `null`, y el target `t+24h` sigue buscándose por timestamp exacto. La corrección
central del proyecto queda intacta.

**Consecuencia a tener en cuenta:** cuando el modelo se encienda, la inferencia usará la
última hora **completa**, no el instante exacto. Con humedad de suelo —que se mueve en escala
de horas— es irrelevante. Queda anotado en el README.

### D3 — Conservar `GET /api/nodes/{nodeId}/features`

**Recomendación: conservarlo.** Justificación, como pediste:

- Es exactamente la "consulta explícita" que mencionás en tu punto 10: devuelve el vector de
  features de la última hora completa sin exportar el CSV entero.
- **En la defensa sirve para demostrar en vivo** que los lags se resuelven por timestamp: se
  llama al endpoint y se comparan los valores contra la serie horaria.
- Cuesta ~15 líneas de controller, porque `DatasetService` ya calcula el vector.

Si preferís los 8 endpoints exactos de tu lista, se borra y no se pierde nada esencial —
el test de lags lo sigue cubriendo.

### D4 — `SatelliteClient`: qué hacer con un cliente que nadie llama

**Hallazgo:** hoy `fetchLatest()` no lo invoca ningún código de producción. Solo
`HealthController` consulta su `isEnabled()`, y un test comprueba que devuelve vacío cuando
el servicio está caído.

Dos opciones:

- **(a) Conservarlo como punto de integración, sin cablear** — se mantiene la clase, el DTO y
  el test de fail-soft, y se documenta en el README que la integración está preparada pero
  todavía no consume nada. Es honesto y son ~90 LOC.
- **(b) Cablearlo a un endpoint de sondeo** `GET /api/satellite/{nodeId}` que devuelva lo que
  VitiAI responde. Permite verificar la integración de punta a punta sin esperar a que el
  dataset tenga columnas satelitales. Cuesta ~20 LOC más.

**Recomendación: (b).** Una integración que nunca se ejecutó no está probada de verdad, y el
día que agregues las columnas satelitales vas a querer haber verificado antes que VitiAI
responde lo que esperás. Igual es tu decisión; con (a) el código es menos.

### D5 — Migración: editar `V1` o agregar `V2`

Hay que sacar `node.api_key` y la tabla `decision_record`.

- Si **todavía no creaste la base** (probable: el proyecto nunca llegó a compilar), lo limpio
  es **editar `V1__init_schema.sql`** y dejar una sola migración.
- Si **ya la creaste y tiene datos**, agrego `V2__simplify.sql` con los `DROP`.

**Necesito que me confirmes cuál es el caso.** Por defecto asumo que la base no existe.

### D6 — La decisión de riego se absorbe en `TelemetryService`

Sin `DecisionRecord`, `IrrigationDecisionService` se reduce a esto:

```java
private boolean resolveDecision(TelemetryReading reading) {
    if (predictionClient.isEnabled()) {
        return predictionClient.predict(...)
                .map(PredictionResponse::irrigate)
                .filter(Objects::nonNull)
                .orElse(reading.isLocalIrrigationDecision());
    }
    return reading.isLocalIrrigationDecision();
}
```

Doce líneas no justifican una clase, un record de salida y un servicio inyectado.
**Propuesta: método privado de `TelemetryService`, con un nombre que lo señale como el punto
de extensión.** Cuando el modelo exista y haya que registrar las tres decisiones, vuelve a
salir como clase propia junto con `DecisionRecord`.

---

## 7. Riesgos que introduce la simplificación

| # | Riesgo | Severidad | Mitigación |
|---|---|---|---|
| S1 | Sin `DecisionRecord`, no queda traza explícita de la decisión del backend | Baja hoy | Hoy la decisión **es** la del ESP32 y está en `telemetry_reading`. Vuelve con el modelo (D1) |
| S2 | Features solo sobre la serie horaria: no hay features sub-horarias | Baja | La humedad del suelo se mueve en horas. El dataset ya era horario |
| S3 | Sin detección de sensor congelado, una lectura pegada pasa como `VALID` | Media | Se detecta en el análisis (`df['humedad_suelo'].diff()`), que es donde corresponde. Se documenta |
| S4 | `QualityFlag` con 3 valores pierde granularidad de diagnóstico | Baja | `quality_notes` sigue guardando el detalle en texto |
| S5 | Sin `WeatherClient`, cuando el Data Miner exponga HTTP hay que escribirlo | Muy baja | Son ~60 LOC y el DTO ya está definido por el CSV |
| S6 | Sin seguridad, cualquiera en la red puede postear telemetría | Aceptada | Laboratorio. Documentado explícitamente en el README |
| S7 | Solo granularidad horaria: cambiar a 15 min requiere tocar código | Baja | Queda una constante `Duration`, no un `if` disperso |
| S8 | Import de meteorología menos tolerante a variaciones de formato | Baja | El CSV lo genera tu propio Data Miner |

## 8. Funcionalidades postergadas (no eliminadas)

1. `DecisionRecord` + comparación de las tres decisiones → **vuelve con el modelo**
2. Granularidades de 5 y 15 minutos → vuelven si el análisis las pide
3. Detección de sensor congelado → se hace en Python, sobre el dataset
4. Autenticación (`X-Node-Key`, `X-Admin-Key`) → si el sistema sale del laboratorio
5. `WeatherClient` (pull REST del Data Miner) → cuando el Data Miner exponga HTTP
6. Columnas satelitales en el dataset → etapa siguiente
7. Endpoint `/status` de nodo online/offline → si hace falta monitoreo
8. `BACKEND_RULES` como escalón intermedio de decisión → probablemente nunca

## 9. Verificación al terminar

```bash
mvn clean verify                 # compilar + 26 tests
mvn spring-boot:run              # arrancar contra PostgreSQL
```

- Swagger en `/swagger-ui.html` con los 10 endpoints
- `POST /api/data` con el JSON exacto del ESP32 → `{"abrir_valvula":…,"encender_luz":…}`
- `POST /api/weather/import` con un CSV del Data Miner
- `GET /api/dataset/export` y revisar celdas vacías donde faltan lags
- Con `satellite.enabled=true` apuntando a un puerto cerrado: la telemetría se guarda igual


---

## 10. Desvíos entre el plan y la ejecución

El plan se cumplió casi entero. Estos cuatro puntos salieron distinto y conviene tenerlos
escritos, porque los tres primeros son hallazgos que el plan no podía anticipar.

### 10.1 Las clases de test terminadas en `IT` no se ejecutaban

El primer `mvn clean verify` dio **BUILD SUCCESS con solo 15 de 23 tests**. Surefire ejecuta
`*Test`, `Test*` y `*Tests`; el sufijo `IT` es de Failsafe, que no está configurado. Las dos
clases que más importaban —el contrato del ESP32 y el fail-soft de las integraciones— quedaban
fuera **en silencio**.

Se renombraron a `TelemetryIngestionTest` y `ExternalServicesDownTest`. Es la clase de error
que no se nota: la build pasa en verde y uno cree que está cubierto.

### 10.2 Un bug de carga perezosa que el refactor reintrodujo

Mientras se reconciliaba con el remoto apareció el commit `6eda399` de Francisco Paredes, que
arreglaba un `LazyInitializationException`: con `open-in-view: false` y `node` en
`FetchType.LAZY`, mapear una entidad a DTO en el controller ocurre **después** de que la
transacción cerró.

El refactor lo reintrodujo al pasar el mapeo a factories estáticos invocados desde el
controller. Afectaba a `telemetry/latest`, `telemetry` e `irrigation-events`.

Se corrigió con `@EntityGraph(attributePaths = "node")` en las tres consultas que se
serializan —el mismo enfoque que él había usado— y se agregó `NodeQueriesTest`, **la única
clase de test sin `@Transactional`**. El resto de la suite mantiene la sesión de Hibernate
abierta durante todo el test, y por eso no detectaba el problema.

Esto agrega una clase de test y 3 casos sobre lo planificado (7 clases, 26 casos).

### 10.3 El contrato de VitiAI es por coordenadas, no por nodo

La decisión D4 preveía cablear el `SatelliteClient` a un endpoint de sondeo. Al implementarlo
se confirmó que `viti-alert-ds-api` expone
`GET /api/v1/satellite/features?lat=&lon=&buffer_km=`: se consulta por **coordenadas**, no por
`node_id`. El endpoint del backend resuelve la latitud y longitud desde la entidad `Node` antes
de llamar — que es exactamente para lo que el nodo guarda sus coordenadas, y no para usarlas
como features del modelo.

Eso deja el total en 10 endpoints y no en los 9 que estimaba el plan.

### 10.4 Maven no estaba instalado en la máquina

El `target/` existente lo había generado el Maven embebido de STS4, que no expone comando de
consola. Se descargó una copia portátil de Maven 3.9.11 a `.tools/` (ignorada por git) para
poder compilar sin instalar nada en el sistema.

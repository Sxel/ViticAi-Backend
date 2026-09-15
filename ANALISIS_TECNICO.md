# VitiAlert — Decisiones técnicas

Complemento del README: el README explica **cómo se usa** el backend, este documento explica
**por qué está hecho así**. Pensado para la defensa de tesis y para retomar el trabajo dentro
de unos meses.

Versión 0.2.0 — 40 clases, 3.284 líneas, 10 endpoints, 4 tablas, 26 tests en 7 clases.
`mvn clean verify` verificado en verde: **26/26 tests, BUILD SUCCESS** (14-09-2026, 17 s).

---

## 1. El sistema en una frase

> Recibe datos IoT, los guarda, reconstruye eventos de riego, integra meteorología y
> satélite, construye el dataset y —más adelante— consulta el modelo de Ciencia de Datos.

Las prioridades, en orden: **corrección de los datos**, **trazabilidad**, **dataset
correcto**, **integración sencilla**, **facilidad de explicación**, **poco código**.
Explícitamente NO son prioridades la escalabilidad futura, las abstracciones para escenarios
hipotéticos ni la seguridad empresarial.

---

## 2. Las ocho decisiones que sostienen el diseño

### 2.1 Java orquesta, Python modela

Java nunca carga un `.pkl`. La única vía hacia el modelo es HTTP contra un servicio Python.

*Por qué:* el ecosistema de Ciencia de Datos es Python; un pickle ataría la versión del
modelo a la del backend y obligaría a un puente (JPMML, ONNX) que habría que revalidar.
Separando por HTTP, el modelo se reentrena y redespliega sin tocar Java, y el backend sigue
funcionando con fallback local si la inferencia no está.

| Java | Python |
|---|---|
| Recibir, validar, persistir | Entrenar |
| Reconstruir riegos | Evaluar |
| Construir features | Serializar (`.pkl`) |
| Consultar inferencia, decidir, responder al ESP32 | Inferir |

### 2.2 Los lags se calculan por timestamp real, nunca por posición de fila

**Esta es la decisión metodológica central del proyecto.**

El prototipo Python construía las features con `shift(n)`, que desplaza *n posiciones de
fila*. Con el ESP32 transmitiendo cada ~30 segundos:

| Feature | Lo que el nombre dice | Lo que realmente calculaba |
|---|---|---|
| `lag_1h` | hace 1 hora | hace 30 segundos |
| `lag_24h` | hace 24 horas | hace 12 minutos |
| `media_24h` | media diaria | media de los últimos 12 minutos |

Tres consecuencias:

1. **Las cuatro "lags" eran la misma variable.** Los cuatro valores caían dentro de una
   ventana de 12 minutos: colineales entre sí y con el valor actual.
2. **Las pendientes quedaban mal por dos factores a la vez:** numerador de 1,5 minutos,
   denominador de 3 horas.
3. **Lo grave era el target.** Con `shift(-24)` el modelo no predice la humedad de mañana:
   predice la de dentro de 12 minutos. Y eso *no se ve en las métricas* — predecir 12 minutos
   hacia adelante es casi trivial, así que el R² sería altísimo y el modelo, inútil en campo.
   Ese es el peor escenario posible para una tesis: un resultado que parece bueno, que pasa
   la validación cruzada y que no sirve.

El error además sobrevive a cambiar la frecuencia: aunque el firmware transmitiera una vez
por hora, un corte de tres horas haría que `shift(24)` devolviera un valor de hace 27 horas
**sin ningún aviso**.

**La corrección:** todo se resuelve sobre la serie horaria buscando el bucket cuyo inicio es
exactamente `t − Xh` (o `t + 24h` para el target). Si ese bucket no existe, la celda queda
vacía. Un hueco produce un dato faltante, no un dato corrido.

Protegido por `DatasetServiceTest.elLagCaeEnUnHuecoDeDatosYQuedaNulo`, que construye una
serie con un hueco de dos horas: un desplazamiento por posición lo saltaría en silencio y el
test fallaría.

### 2.3 Un faltante es un faltante, nunca un cero

Aplicado sin excepciones: en la agregación, en las features, en el CSV y en el mensaje al
servicio de inferencia.

*Por qué:* un `0` en `soil_moisture_lag_24h` significa literalmente "hace 24 horas el suelo
estaba completamente seco". Es un valor válido del dominio y el modelo lo interpreta así.
Rellenar faltantes con cero **inyecta observaciones falsas de la condición más extrema
posible**, justo en los momentos en que hubo problemas de datos.

Con `null` / celda vacía: pandas lo lee como `NaN`, `df.isna().sum()` lo muestra, la decisión
de imputar se toma explícitamente en el análisis, y cuánto dato falta se vuelve un resultado
medible de la tesis.

La distinción se mantiene incluso donde es sutil: un bucket informa `waterVolumeUsed = 0`
cuando se pudo calcular el consumo y fue nulo, y `null` cuando no se pudo calcular. Es la
diferencia entre "sé que no regó" y "no sé si regó".

### 2.4 Una sola implementación de features

`DatasetService` es la única clase que calcula features. El CSV de entrenamiento, el endpoint
`/features` y la consulta al modelo usan el mismo código.

*Por qué:* una versión anterior tenía dos implementaciones — vecino temporal sobre lecturas
crudas para el camino caliente, bucket exacto sobre la serie horaria para el export. El
modelo se habría entrenado con una y consultado con la otra: *training/serving skew*, que
hace que el modelo prediga peor sin que nada falle, y es de los bugs más difíciles de
detectar. Con una sola implementación el problema no puede existir.

### 2.5 Agregar antes de exportar

El dataset se construye sobre buckets horarios, no sobre lecturas crudas.

*Por qué:* con frecuencia irregular, cada fila representaría un lapso distinto (una lectura
cada 5 s pesaría lo mismo que una cada 5 min, sesgando el ajuste hacia los períodos de
transmisión rápida), las ventanas móviles no tendrían un número estable de observaciones y el
target `t+24h` no caería nunca en una fila existente. Sobre una grilla regular los tres
problemas desaparecen, y la operación es determinista: dos exports del mismo período dan
exactamente el mismo resultado.

El tiempo de válvula abierta no es una agregación de filas sino una **integración sobre los
intervalos** entre lecturas consecutivas, descartando huecos mayores a 120 s. Si el nodo
estuvo caído media hora con la válvula abierta, no se puede afirmar que regó media hora.

### 2.6 El dato crudo es inmutable

Cada POST genera una fila de `telemetry_reading` que nunca se corrige ni se sobrescribe. Los
eventos de riego y las features son **interpretaciones recalculables** sobre esa base, y por
eso viven en otras tablas o se calculan al vuelo.

`TelemetryReading` no expone setters: se construye con un `Builder` y JPA la hidrata por
reflexión. Es imposible escribir `reading.setHumedadSueloPct(...)` por accidente.

### 2.7 El camino del POST está acotado

Dos consultas y una escritura, todas por índice, y ese número **no crece con el histórico
acumulado**: el sistema se comporta igual el primer día que el año siguiente. El prototipo
Python recalculaba todo el histórico y reescribía un CSV en cada recepción.

```
POST /api/data
  ├─ Bean Validation ............... 0 queries
  ├─ resolver nodo ................. 1 query
  ├─ control de calidad ............ 0 queries
  ├─ guardar lectura ............... 1 INSERT
  ├─ evento de riego ............... 1 query (+1 escritura si hay transición)
  └─ decisión ...................... 0 queries con el modelo apagado
```

Con el modelo apagado no se calculan features ni se hace ninguna llamada de red: el costo de
tener la integración preparada es cero.

### 2.8 Integraciones *fail-soft* y apagadas por defecto

`SatelliteClient` y `PredictionClient` devuelven `Optional.empty()` ante cualquier fallo,
registran `WARN` y nunca lanzan. Timeouts cortos y explícitos.

*Por qué:* **el dato de campo es irrecuperable, la predicción no.** Si VitiAI no responde se
pierde un enriquecimiento; si no se guarda la lectura, se pierde para siempre. Y el
interruptor apagado por defecto garantiza que la maqueta no cambia de comportamiento al
desplegar el backend.

Probado apuntando a puertos cerrados reales (`ExternalServicesDownTest`), no mockeando los
clientes: mockear probaría que el código maneja un `Optional` vacío fabricado por el propio
test, no que la excepción real de conexión se captura donde debe.

---

## 3. Decisiones menores, en una línea cada una

| Decisión | Por qué |
|---|---|
| Sin Lombok | Los `record` absorben el boilerplate de los DTO; evita un *annotation processor* que puede fallar la semana de la entrega |
| `record` para DTO, clases para entidades | JPA no soporta `record`: necesita constructor vacío y escritura por reflexión |
| Flyway única fuente de verdad, `ddl-auto: none` | `update` es no determinista y silencioso; Flyway permite índices parciales que Hibernate no genera |
| H2 en tests, no Testcontainers | Un test que necesita Docker es un test que no se corre |
| `BigDecimal` para agua, `Double` para ambiente | El volumen es un contador acumulado sobre el que se hacen restas; la temperatura está limitada por el sensor |
| UTC y `Instant` en todo | Sin discontinuidades de huso horario; el reloj del ESP32 no es confiable |
| `snake_case` en toda la API | El consumidor es Python; los nombres coinciden de punta a punta |
| Sin interfaces de una implementación | No desacoplan nada y Mockito ya no las necesita |
| Alta automática de nodos | Hace real el criterio "apuntar la maqueta cambiando solo la URL"; en la duda, se guarda el dato |
| Sin seguridad | Ambiente de laboratorio, sin datos personales. Declarado, no olvidado |
| `@EntityGraph(attributePaths = "node")` en las consultas que se serializan | Con `open-in-view: false` y `node` LAZY, mapear a DTO fuera de la transacción lanza `LazyInitializationException`; el graph lo trae con un join y de paso evita el N+1 |

---

## 4. Mapa de clases

```
controller/  4   HTTP ↔ servicios. Cero lógica de negocio
service/     7   Todo el comportamiento del sistema
client/      2   HTTP hacia los servicios Python, fail-soft
repository/  4   Consultas explícitas, acotadas por nodo y ventana temporal
domain/      6   4 entidades + 2 enums. La regla de negocio propia vive en IrrigationEvent.close()
dto/        11   Contratos de entrada/salida
exception/   3   Error uniforme
config/      2   1 record de propiedades + OpenAPI
```

Dos clases concentran lo que importa:

- **`TelemetryService`** — ingesta, calidad, decisión y consultas. El método privado
  `resolveIrrigationDecision` es **el punto de extensión del modelo**: hoy son tres líneas y
  cuando llegue la inferencia es la única que cambia.
- **`DatasetService`** — features y CSV. Es donde vive la corrección metodológica de 2.2.

**`IrrigationEvent.close()`** merece mención aparte: calcula duración, volumen aplicado y
caudal promedio. Si la resta `final − inicial` da negativo, el contador del ESP32 se reinició
durante el riego; se asume que arrancó de cero y el evento queda `CLOSED_WITH_WARNING` con la
observación que explica qué pasó y con qué números. No se descarta el evento: descartarlo
perdería la información de que hubo un riego, y marcarlo deja que el análisis decida.

---

## 5. Riesgos conocidos

La suite se ejecutó completa contra H2: 26 tests en 7 clases, sin fallos ni errores.

| # | Riesgo | Mitigación / estado |
|---|---|---|
| R1 | Maven no está instalado en el sistema; la build usa el Maven portátil de `.tools/` | Funciona con `.tools\apache-maven-3.9.11\bin\mvn.cmd clean verify`. Instalarlo en el sistema lo hace permanente |
| R2 | Los tests no validan las migraciones Flyway (usan H2 con esquema de Hibernate) | El arranque contra PostgreSQL es la prueba, y ocurre una vez |
| R3 | Un riego queda `OPEN` para siempre si el nodo se cae con la válvula abierta | Visible como evento abierto; falta la tarea de cierre automático |
| R4 | El timestamp es el de recepción, no el de medición | Diferencia despreciable en red local; falta `timestamp_sensor` en el firmware |
| R5 | Sin detección de sensor congelado en tiempo real | **Aceptado**: generaba falsos positivos y carga en cada POST. Se hace en Python con `diff()` |
| R6 | Sin autenticación | **Aceptado y declarado**: ambiente de laboratorio |
| R7 | Solo granularidad horaria | Es la que usa la tesis; ampliar es cambiar una constante |
| R8 | VitiAI se consulta pero no se persiste | El punto de integración está probado; faltan las columnas en el dataset |
| R9 | Los tests con `@Transactional` mantienen la sesión abierta y ocultan problemas de carga perezosa | `NodeQueriesTest` corre sin `@Transactional` y cubre los tres endpoints que serializan entidades |

---

## 6. Qué sostiene todo esto

Tres afirmaciones:

1. **El dato crudo es sagrado.** Se guarda siempre, no se sobrescribe nunca, y cuando es
   dudoso se marca en lugar de descartarse.
2. **Un faltante es un faltante.** Aplicado sin excepciones, es lo que separa un dataset
   utilizable de uno que produce métricas engañosas.
3. **La simplicidad es una decisión, no una concesión.** Cada abstracción que no está se
   descartó con un argumento explícito. El sistema es simple a propósito, y por eso se puede
   explicar entero.

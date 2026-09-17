# VitiAlert — actualización y despliegue

Fecha de actualización: 17 de septiembre de 2026.

## Estado actual

| Componente | Estado | URL / ubicación |
|---|---|---|
| Backend Java | Desplegado en Render | <https://vitialert-backend.onrender.com> |
| PostgreSQL | Creado y conectado al backend | `vitialert-db` en Render |
| API satelital VitiAI | Desplegada en Render | <https://viti-alert-ds-api.onrender.com> |
| Data Miner meteorológico | Implementado | `data_miner.py` y `web_scrapping.py` |
| Sincronización diaria | Implementada con GitHub Actions | Workflow `VitiAlert data miner` |
| Sensores IoT | Pendiente de conexión física | ESP32 |
| Modelo de humedad a 24 h | Pendiente de datos IoT reales | Se mantiene desactivado hasta entrenarlo y validarlo |

## Endpoint para los sensores IoT

El ESP32 debe enviar cada lectura mediante:

```http
POST https://vitialert-backend.onrender.com/api/data
Content-Type: application/json
X-API-Key: <API_KEY_CONFIGURADA_EN_RENDER>
```

La clave es el valor de `API_KEY` disponible en:

```text
Render → vitialert-backend → Environment → API_KEY
```

No se debe escribir la clave real en repositorios públicos. En el firmware debe quedar en
un archivo local de secretos o en una constante excluida del control de versiones.

### JSON enviado por el ESP32

```json
{
  "nodo_id": "1",
  "temperatura_ambiente_c": 28.4,
  "humedad_relativa_pct": 45,
  "humedad_suelo_pct": 22,
  "humedad_suelo_raw": 2730,
  "velocidad_viento_kmh": 18.5,
  "caudal_l_min": 7.8,
  "volumen_total_l": 124.6,
  "valvula_abierta_actual": true,
  "decision_riego_local": true
}
```

### Respuesta del backend

```json
{
  "abrir_valvula": true,
  "encender_luz": true
}
```

Mientras el modelo predictivo esté desactivado, el backend conserva la decisión local del
ESP32. Si VitiAI o el futuro modelo no responden, la recepción y persistencia de telemetría
continúan funcionando.

### Ejemplo con `curl`

```bash
curl -X POST "https://vitialert-backend.onrender.com/api/data" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: REEMPLAZAR_POR_LA_CLAVE_DE_RENDER" \
  -d '{
    "nodo_id": "1",
    "temperatura_ambiente_c": 28.4,
    "humedad_relativa_pct": 45,
    "humedad_suelo_pct": 22,
    "humedad_suelo_raw": 2730,
    "velocidad_viento_kmh": 18.5,
    "caudal_l_min": 7.8,
    "volumen_total_l": 124.6,
    "valvula_abierta_actual": true,
    "decision_riego_local": true
  }'
```

## Cambios implementados

### Backend Java

- Se corrigió la carga `LAZY` de `Node` en las consultas de última telemetría e histórico.
- `GET /api/nodes/{nodeId}/telemetry` ya funciona con la sesión Hibernate cerrada.
- Se agregó persistencia de observaciones satelitales GOES y Sentinel.
- Se creó la migración Flyway `V3__satellite_observation.sql`.
- El dataset incorpora:
  - temperatura del tope de nube;
  - variación térmica de nube;
  - fracción nubosa;
  - lluvia satelital estimada;
  - NDVI;
  - NDMI.
- Se agregó `POST /api/satellite/refresh` para actualizar el contexto satelital.
- Los nodos creados automáticamente reciben las coordenadas configuradas para Pocito.
- Los endpoints `POST` están protegidos mediante `X-API-Key`.
- Se agregaron Dockerfile, Blueprint de Render y conexión automática con PostgreSQL.
- Se verificaron 27 pruebas Java sin errores.

### Data Miner

- `web_scrapping.py` dejó de depender de Google Colab, Google Drive y selectores HTML.
- Ahora obtiene meteorología desde la API documentada de Open-Meteo.
- Produce el CSV exacto que acepta `POST /api/weather/import`.
- Calcula temperatura, humedad, viento, precipitación, radiación, ET0 y VPD.
- No genera etiquetas artificiales de granizo.
- `data_miner.py` ahora:
  1. descarga meteorología consolidada y reciente;
  2. la importa al backend;
  3. solicita la captura satelital;
  4. permite descargar el dataset de entrenamiento.

### API VitiAI

- Se fijó Python 3.11 y despliegue mediante Docker para evitar la compilación fallida de
  NumPy, Rasterio y PyProj con Python 3.14.
- Se validaron GOES-19 y Sentinel-2 en producción.
- El prototipo antiguo de granizo quedó fuera del flujo productivo.
- Se verificaron 68 pruebas Python sin errores.

### Automatización

- Se creó `.github/workflows/data-miner.yml` para ejecutar la sincronización diariamente.
- Se actualizaron las acciones de GitHub a versiones compatibles con Node.js 24.
- El repositorio de GitHub necesita estos secretos:

```text
BACKEND_BASE_URL=https://vitialert-backend.onrender.com
VITIALERT_API_KEY=<MISMA_API_KEY_DE_RENDER>
```

## Endpoints útiles

| Método | Endpoint | Función |
|---|---|---|
| POST | `/api/data` | Recibir telemetría y devolver decisión al ESP32 |
| GET | `/api/nodes` | Listar nodos registrados |
| GET | `/api/nodes/{nodeId}/telemetry/latest` | Obtener la última lectura |
| GET | `/api/nodes/{nodeId}/telemetry` | Consultar histórico paginado |
| GET | `/api/nodes/{nodeId}/irrigation-events` | Consultar eventos de riego |
| GET | `/api/nodes/{nodeId}/features` | Inspeccionar el vector horario de variables |
| GET | `/api/nodes/{nodeId}/satellite` | Consultar contexto satelital en vivo |
| POST | `/api/weather/import` | Importar meteorología del Data Miner |
| POST | `/api/satellite/refresh` | Persistir una nueva captura satelital |
| GET | `/api/dataset/export` | Exportar el dataset horario |
| GET | `/health` | Comprobar el estado del backend |

Documentación interactiva:

- Backend: <https://vitialert-backend.onrender.com/swagger-ui.html>
- VitiAI: <https://viti-alert-ds-api.onrender.com/docs>

## Consideración del plan gratuito

El servicio gratuito de Render puede suspenderse por inactividad. La primera petición después
de un período sin uso puede tardar alrededor de un minuto. El firmware debe usar un timeout
amplio y reintentar sin duplicar el contador de volumen ni bloquear la decisión local.

## Pendiente para conectar el ESP32

1. Guardar la URL productiva y la API key en el firmware.
2. Enviar el JSON con los nombres indicados en este documento.
3. Usar HTTPS y verificar el código HTTP de la respuesta.
4. Mantener la lógica local como respaldo si hay timeout o falta de conexión.
5. Reintentar con espera progresiva, sin detener el control local de la válvula.
6. Registrar telemetría real durante varias semanas antes de entrenar el modelo t+24 h.


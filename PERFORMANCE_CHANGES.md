# Cambios de Performance - Turismo Backend

**Fecha:** 2026-04-26  
**Endpoint objetivo:** `GET /api/places/search`  
**Estado:** Aplicado y listo para pruebas

---

## Resumen de Cambios

| Archivo | Cambio | Impacto Esperado |
|---------|--------|-----------------|
| `application.yaml` | Pool R2DBC: max-size 6→30, +timeouts | +50% throughput, menos timeouts |
| `Dockerfile` | JAVA_OPTS optimizados para 480MiB | -30% dropped_iterations, GC mejorado |
| `V2__performance_indexes.sql` | 6 índices PostgreSQL nuevos | -70% latencia p95 en búsquedas TEXT |
| `PlaceUseCaseTest.java` | +4 tests de cobertura | Validación de modos de búsqueda |
| `k6-*.js` | 3 scripts de prueba de carga | Métricas automáticas de rendimiento |

---

## Cambios Detallados

### 1. Configuración R2DBC Pool (`application.yaml`)

**Cambios:**
```yaml
# Antes
pool:
  initial-size: 2
  max-size: 6
  max-idle-time: 5m

# Después
pool:
  initial-size: 5
  max-size: 30
  max-idle-time: 10m
  max-acquire-time: 3s
  max-create-connection-time: 5s
  validation-query: "SELECT 1"
  validation-depth: local
```

**Justificación:**
- El pool de 6 conexiones era cuello de botella a 40 req/s
- Timeouts evitan que requests se queden colgados esperando conexión
- Validación de conexiones detecta conexiones muertas antes de usarlas

**Variables de entorno nuevas:**
```bash
CONN_POOL_MAX_SIZE=30              # Default: 30
CONN_POOL_MAX_ACQUIRE_TIME=3s      # Default: 3s
CONN_POOL_MAX_CREATE_TIME=5s       # Default: 5s
```

---

### 2. Optimización JVM (`Dockerfile`)

**Cambios:**
```dockerfile
# Configuración flexible mediante variables de entorno
ENV JVM_HEAP_MIN=${JVM_HEAP_MIN:-120m}
ENV JVM_HEAP_MAX=${JVM_HEAP_MAX:-240m}
ENV JVM_METASPACE_MAX=${JVM_METASPACE_MAX:-96m}
ENV JVM_DIRECT_MEMORY=${JVM_DIRECT_MEMORY:-32m}
ENV JVM_STACK_SIZE=${JVM_STACK_SIZE:-256k}

# JAVA_OPTS construido dinámicamente
ENV JAVA_OPTS="-Xms${JVM_HEAP_MIN} -Xmx${JVM_HEAP_MAX} ..."
```

**Justificación:**
- Heap 160→240m (default): Reduce GC pressure y OOM en spikes
- DirectMemory 16→32m: Más buffers para Netty bajo carga
- Metaspace 128→96m: Reducción segura (clases ya cargadas)
- **Nuevo**: Configuración flexible para escalar a 1GB, 2GB o más

**Variables de entorno JVM (nuevas):**

| Variable | Default | Para 480MB | Para 1GB | Para 2GB |
|----------|---------|------------|----------|----------|
| `JVM_HEAP_MIN` | 120m | 120m | 256m | 512m |
| `JVM_HEAP_MAX` | 240m | 240m | 512m | 1200m |
| `JVM_METASPACE_MAX` | 96m | 96m | 128m | 128m |
| `JVM_DIRECT_MEMORY` | 32m | 32m | 64m | 128m |
| `JVM_STACK_SIZE` | 256k | 256k | 256k | 256k |

**Ejemplo con 2GB RAM:**
```bash
docker run -d \
  --name turismo-2gb \
  --cpus="2.0" \
  --memory="2g" \
  -p 7860:7860 \
  -e JVM_HEAP_MIN=512m \
  -e JVM_HEAP_MAX=1200m \
  -e JVM_DIRECT_MEMORY=128m \
  -e CONN_POOL_MAX_SIZE=50 \
  turismo-backend:perf
```

---

### 3. Índices PostgreSQL (`V2__performance_indexes.sql`)

**Índices creados:**

```sql
-- 1. Filtrado activo + categoría + ordenamiento
CREATE INDEX idx_places_active_category_created 
ON places (is_active, category_id, created_at DESC) 
WHERE is_active = TRUE;

-- 2. Búsquedas geográficas (ST_DWithin)
CREATE INDEX idx_places_geom_active 
ON places USING GIST (geom) 
WHERE is_active = TRUE;

-- 3. Búsqueda de texto con trigram (LIKE '%text%')
CREATE INDEX idx_places_name_trgm 
ON places USING GIN (name gin_trgm_ops);

-- 4. Dirección con trigram
CREATE INDEX idx_places_address_trgm 
ON places USING GIN (address gin_trgm_ops);

-- 5. Búsqueda por owner
CREATE INDEX idx_places_owner_active 
ON places (owner_user_id, created_at DESC);
```

**Ejecución:**
```bash
# Requiere extensión pg_trgm
psql -d turismo_db -f deployment/V2__performance_indexes.sql
```

**Verificación:**
```sql
-- Ver uso de índices
SELECT schemaname, tablename, indexname, idx_scan, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes 
WHERE tablename = 'places';
```

---

### 4. Scripts k6 de Prueba

**Archivos creados:**
- `k6-capacity-test.js` - Prueba progresiva hasta 100 req/s
- `k6-stress-test.js` - Spike a 150 req/s, luego recovery
- `k6-soak-test.js` - 30 minutos a 30 req/s (detección de leaks)

**Ejecución:**
```bash
# Prueba de capacidad (10 minutos)
k6 run -e BASE_URL=http://localhost:7860 deployment/k6-capacity-test.js

# Stress test (10 minutos)
k6 run -e BASE_URL=http://localhost:7860 deployment/k6-stress-test.js

# Soak test (30 minutos)
k6 run -e BASE_URL=http://localhost:7860 deployment/k6-soak-test.js
```

**Resultados generados:**
- `capacity-results.json` - Métricas de capacidad
- `stress-results.json` - Análisis de límite y recuperación
- `soak-results.json` - Estabilidad a largo plazo

---

## Cómo Ejecutar Tests

### Tests Unitarios
```bash
./gradlew :domain:usecase:test --tests "co.turismo.usecase.place.PlaceUseCaseTest"
```

### Build Completo
```bash
./gradlew clean :app-service:bootJar -x validateStructure
```

### Build Docker
```bash
docker build -f deployment/Dockerfile -t turismo-backend:perf .
```

### Ejecutar Contenedor (480MB - Default)
```bash
docker run -d \
  --name turismo-perf \
  --cpus="1.0" \
  --memory="480m" \
  -p 7860:7860 \
  -e CONN_POOL_MAX_SIZE=30 \
  -e CONN_POOL_MAX_ACQUIRE_TIME=3s \
  turismo-backend:perf
```

### Ejecutar Contenedor (2GB - Producción Escalada)
```bash
docker run -d \
  --name turismo-prod \
  --cpus="2.0" \
  --memory="2g" \
  -p 7860:7860 \
  -e JVM_HEAP_MIN=512m \
  -e JVM_HEAP_MAX=1200m \
  -e JVM_DIRECT_MEMORY=128m \
  -e CONN_POOL_MAX_SIZE=50 \
  -e CONN_POOL_MAX_ACQUIRE_TIME=5s \
  -e REACTOR_NETTY_IO_WORKER_COUNT=4 \
  turismo-backend:perf
```

### Configuraciones Recomendadas por Ambiente

| Ambiente | RAM | CPU | JVM_HEAP_MAX | CONN_POOL_MAX | Expected RPS |
|----------|-----|-----|--------------|---------------|--------------|
| **Dev/Local** | 480m | 1.0 | 240m (default) | 30 | 40-50 |
| **Staging** | 1g | 1.5 | 512m | 40 | 80-100 |
| **Producción** | 2g | 2.0 | 1200m | 50 | 150-200 |
| **High Load** | 4g | 4.0 | 2800m | 80 | 400+ |

---

## Métricas Esperadas (Post-Cambios)

| Métrica | Baseline (Antes) | Esperado (Después) |
|---------|-----------------|-------------------|
| **Pool 20 → 30** | | |
| http_reqs/s @ 40 iter | 26.93 | 38-40 |
| p95 @ 40 iter | 484ms | < 400ms |
| **JVM Optimizada** | | |
| dropped_iterations @ 100 iter | 6794 | < 1000 |
| CPU pico @ 100 iter | 94.53% | < 85% |
| **+ Índices SQL** | | |
| p95 TEXT mode | 8.34s | < 500ms |
| avg TEXT mode | 8.34s | < 200ms |

---

## Experimentos Recomendados

### Prueba 1: Validar Pool 30
```bash
docker run -d --name test-pool30 --cpus="1.0" --memory="480m" -p 7860:7860 \
  -e CONN_POOL_MAX_SIZE=30 \
  turismo-backend:perf

k6 run -e BASE_URL=http://localhost:7860 deployment/k6-capacity-test.js
```

**Éxito si:**
- http_reqs/s ≥ 38 a 40 iter target
- p95 < 600ms
- dropped_iterations = 0

### Prueba 2: Validar JVM Optimizada
```bash
docker run -d --name test-jvm --cpus="1.0" --memory="480m" -p 7860:7860 \
  -e JAVA_OPTS="-Xms120m -Xmx240m -XX:MaxDirectMemorySize=32m ..." \
  turismo-backend:perf

k6 run -e BASE_URL=http://localhost:7860 deployment/k6-stress-test.js
```

**Éxito si:**
- No hay OOM
- error_rate < 5% en spike
- Sistema se recupera post-spike

### Prueba 3: Validar Índices SQL
```bash
# Ejecutar índices primero
psql -d turismo_db -f deployment/V2__performance_indexes.sql

# Luego prueba
k6 run -e BASE_URL=http://localhost:7860 deployment/k6-capacity-test.js
```

**Éxito si:**
- TEXT mode p95 < 1s
- ALL mode throughput aumenta

---

## Troubleshooting

### Error: "Pool exhausted"
**Causa:** Pool muy pequeño para la carga  
**Solución:** Aumentar `CONN_POOL_MAX_SIZE` a 40

### Error: "OutOfMemoryError"
**Causa:** Heap insuficiente o leak  
**Solución:** Verificar `-Xmx240m` y heap dump si persiste

### Error: "Connection timeout"
**Causa:** PostgreSQL no responde o red lenta  
**Solución:** Verificar `max-acquire-time` y estado de DB

### Latencia alta persistiendo
**Causa:** Índices no aplicados o query no los usa  
**Solución:** Verificar con `EXPLAIN ANALYZE` en PostgreSQL

---

## Próximos Pasos Sugeridos

1. **Ejecutar pruebas k6** y documentar resultados vs baseline
2. **Aplicar índices SQL** si no aplicados aún
3. **Monitorear métricas** con Prometheus/Grafana si disponible
4. **Considerar caching** Redis para queries frecuentes (ALL sin filtros)
5. **Evaluar sharding** si la base de datos crece > 100k lugares

---

## Archivos Modificados

```
applications/app-service/src/main/resources/application.yaml     (modificado)
deployment/Dockerfile                                              (modificado)
deployment/V2__performance_indexes.sql                               (creado)
deployment/k6-capacity-test.js                                       (creado)
deployment/k6-stress-test.js                                       (creado)
deployment/k6-soak-test.js                                         (creado)
domain/usecase/src/test/java/.../PlaceUseCaseTest.java             (modificado)
```

---

## Contacto

Para dudas sobre estos cambios o análisis de resultados de pruebas, revisar:
1. Logs de aplicación: `docker logs turismo-perf`
2. Métricas PostgreSQL: `pg_stat_user_indexes`, `pg_stat_statements`
3. Reportes k6: `capacity-results.json`, `stress-results.json`

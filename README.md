# Proyecto Base Implementando Clean Architecture

## Antes de Iniciar

Empezaremos por explicar los diferentes componentes del proyectos y partiremos de los componentes externos, continuando con los componentes core de negocio (dominio) y por último el inicio y configuración de la aplicación.

Lee el artículo [Clean Architecture — Aislando los detalles](https://medium.com/bancolombia-tech/clean-architecture-aislando-los-detalles-4f9530f35d7a)

# Arquitectura

![Clean Architecture](https://miro.medium.com/max/1400/1*ZdlHz8B0-qu9Y-QO3AXR_w.png)

## Domain

Es el módulo más interno de la arquitectura, pertenece a la capa del dominio y encapsula la lógica y reglas del negocio mediante modelos y entidades del dominio.

## Usecases

Este módulo gradle perteneciente a la capa del dominio, implementa los casos de uso del sistema, define lógica de aplicación y reacciona a las invocaciones desde el módulo de entry points, orquestando los flujos hacia el módulo de entities.

## Infrastructure

### Helpers

En el apartado de helpers tendremos utilidades generales para los Driven Adapters y Entry Points.

Estas utilidades no están arraigadas a objetos concretos, se realiza el uso de generics para modelar comportamientos
genéricos de los diferentes objetos de persistencia que puedan existir, este tipo de implementaciones se realizan
basadas en el patrón de diseño [Unit of Work y Repository](https://medium.com/@krzychukosobudzki/repository-design-pattern-bc490b256006)

Estas clases no puede existir solas y debe heredarse su compartimiento en los **Driven Adapters**

### Driven Adapters

Los driven adapter representan implementaciones externas a nuestro sistema, como lo son conexiones a servicios rest,
soap, bases de datos, lectura de archivos planos, y en concreto cualquier origen y fuente de datos con la que debamos
interactuar.

### Entry Points

Los entry points representan los puntos de entrada de la aplicación o el inicio de los flujos de negocio.

## Application

Este módulo es el más externo de la arquitectura, es el encargado de ensamblar los distintos módulos, resolver las dependencias y crear los beans de los casos de use (UseCases) de forma automática, inyectando en éstos instancias concretas de las dependencias declaradas. Además inicia la aplicación (es el único módulo del proyecto donde encontraremos la función “public static void main(String[] args)”.

**Los beans de los casos de uso se disponibilizan automaticamente gracias a un '@ComponentScan' ubicado en esta capa.**

---

# Caché de Búsqueda de Lugares (Places Search Cache)

El sistema implementa una capa de caché con Redis para mejorar el rendimiento del endpoint `GET /api/places/search`, reduciendo la presión sobre PostgreSQL y aumentando la capacidad de respuesta del backend.

## Patrón Cache-Aside

```
Request → Redis
  si existe → responde rápido sin consultar DB (CACHE HIT)
  si no existe → consulta DB → guarda en Redis → responde (CACHE MISS)
```

## Configuración

Las siguientes variables de entorno controlan el comportamiento del caché:

| Variable | Descripción | Default |
|----------|-------------|---------|
| `CACHE_PLACES_ENABLED` | Habilitar/deshabilitar caché | `true` |
| `CACHE_PLACES_SEARCH_TTL_SECONDS` | TTL en segundos para resultados de búsqueda | `60` |
| `CACHE_PLACES_KEY_PREFIX` | Prefijo para las keys de Redis | `places` |

## Estructura de Keys en Redis

Las keys son determinísticas y dependen de los parámetros del request:

```
places:search:mode={mode}:page={page}:size={size}
places:search:mode=text:q={query}:page=0:size=10
places:search:mode=all:categoryId=5:lat=2.536:lng=-75.276:radius=5000:page=0:size=20
```

### Reglas de Generación de Keys:
- Los valores se normalizan (minúsculas, sin espacios)
- Las coordenadas tienen 6 decimales de precisión
- Los parámetros nulos no se incluyen
- La paginación siempre está presente

## Invalidación de Caché

El caché se invalida automáticamente cuando ocurren operaciones de escritura:

- `POST /api/places` (crear lugar) → Invalida `places:search:*`
- `PATCH /api/places/{id}` (actualizar lugar) → Invalida `places:search:*`
- `DELETE /api/places/{id}` (eliminar lugar) → Invalida `places:search:*`
- `POST /api/places/{id}/verify` (verificar lugar) → Invalida `places:search:*`
- `POST /api/places/{id}/active` (activar/desactivar lugar) → Invalida `places:search:*`

**Nota:** La invalidación ocurre de forma asíncrona y no bloquea la operación principal. Si Redis falla durante la invalidación, la operación de escritura sigue siendo exitosa.

## Manejo de Errores

El sistema está diseñado para ser resiliente ante fallos de Redis:

- **Redis caído en lectura:** El endpoint consulta directamente a PostgreSQL
- **Redis caído en escritura:** La operación principal sigue funcionando
- **Redis caído en invalidación:** No afecta la operación de escritura

## Logs de Caché

Busca estos logs para verificar el comportamiento del caché:

```
CACHE HIT key=places:search:mode=all:page=0:size=10
CACHE MISS key=places:search:mode=text:q=parque:page=0:size=10
CACHE SET key=places:search:mode=all:page=0:size=10 ttl=60 places=15
CACHE INVALIDATE pattern=places:search:*
CACHE ERROR operation={operation} key={key} message={error}
```

## Testing del Caché

Para verificar que el caché está funcionando:

1. **Cache Miss:** Primera solicitud debe mostrar `CACHE MISS` y consultar DB
2. **Cache Hit:** Segunda solicitud idéntica debe mostrar `CACHE HIT` sin consultar DB
3. **TTL:** Esperar 60 segundos y verificar que el caché expira (`CACHE MISS`)
4. **Invalidación:** Crear/actualizar un lugar y verificar invalidación (`CACHE INVALIDATE`)

## Riesgos y Consideraciones

- **Datos temporalemente viejos:** Los resultados de búsqueda pueden estar desactualizados hasta 60 segundos (TTL configurable)
- **Consistencia eventual:** Después de una operación de escritura, el caché antiguo puede persistir brevemente hasta que se complete la invalidación
- **Memoria de Redis:** Monitorear el uso de memoria de Redis en producción, especialmente si hay muchas combinaciones de parámetros de búsqueda únicas

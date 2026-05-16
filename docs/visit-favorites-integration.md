# Visitas y Favoritos - Entrega Backend

## Objetivo implementado

1. Límite de visitas por usuario/sitio cada 24 horas.
2. Endpoint para consultar los sitios más visitados por el usuario autenticado.
3. Endpoints para gestionar favoritos del usuario (listar, agregar, eliminar).

## Regla de negocio nueva (24h)

- En `check-in` (`POST /api/pruebas/places/{id}/checkin`) ahora se valida:
  - Si el usuario ya tiene una visita `confirmed` para ese `place` en las últimas 24 horas, no se crea una nueva visita.
- Respuesta esperada en ese caso:
  - HTTP `409`
  - `message`: `Ya registraste visita en este sitio en las últimas 24 horas`

## Endpoints actualizados y nuevos

Base URL ejemplo:
`https://turismo-back-uv7n.onrender.com`

### 1) Check-in (actualizado con regla 24h)

- `POST /api/pruebas/places/{id}/checkin`
- Auth: `Bearer` recomendado (usuario autenticado)
- Body:

```json
{
  "lat": 2.945754,
  "lng": -75.245503,
  "accuracy_m": 15,
  "device_id": "mobile-123",
  "meta": {
    "appVersion": "mobile-app",
    "source": "place-detail"
  }
}
```

`meta` también acepta string JSON escapado.

### 2) Confirmación de visita (sin cambios de contrato)

- `PATCH /api/pruebas/visits/{visitId}/confirm`
- Auth: `Bearer` obligatorio
- Body:

```json
{
  "lat": 2.945754,
  "lng": -75.245503,
  "accuracy_m": 15
}
```

### 3) Mis lugares más visitados (nuevo)

- `GET /api/pruebas/users/me/visits/top?limit=10`
- Auth: `Bearer` obligatorio
- Response `200`:

```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "place_id": 101,
      "name": "Parque Central",
      "visits": 12
    }
  ]
}
```

### 4) Listar mis favoritos (nuevo)

- `GET /api/pruebas/users/me/favorites?limit=20&offset=0`
- Auth: `Bearer` obligatorio
- Response `200`:

```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "place": {
        "id": 101,
        "name": "Parque Central",
        "lat": 2.9457,
        "lng": -75.2455,
        "address": "Calle 1",
        "description": "Lugar turístico",
        "categoryId": 3,
        "imageUrls": []
      },
      "favorited_at": "2026-05-16T09:30:00Z"
    }
  ]
}
```

### 5) Agregar favorito (nuevo)

- `POST /api/pruebas/users/me/favorites/{placeId}`
- Auth: `Bearer` obligatorio
- Sin body.
- Response `200`:

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "message": "Favorito agregado"
  }
}
```

### 6) Eliminar favorito (nuevo)

- `DELETE /api/pruebas/users/me/favorites/{placeId}`
- Auth: `Bearer` obligatorio
- Sin body.
- Response `200`:

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "message": "Favorito eliminado"
  }
}
```

## SQL requerido

Archivo de migración:

- `deployment/V4__visit_limits_and_favorites.sql`

Incluye:

1. Tabla `user_place_favorites`.
2. Índice para paginación/listado por usuario.
3. Índices para acelerar validación de visitas confirmadas en ventana de 24h.

## Notas para frontend

1. Mantener flujo:
   - `nearby` -> `checkin` -> esperar permanencia -> `confirm`.
2. Mostrar mensaje backend en errores `409` para regla 24h.
3. Usar los nuevos endpoints de favoritos para UI de “guardar/desguardar”.

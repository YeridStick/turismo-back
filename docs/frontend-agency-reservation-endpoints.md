# Informe Frontend: Agencia y Reservacion

Base URL: `/api`

Todas las respuestas usan el envelope:

```json
{
  "status": 200,
  "message": "OK",
  "data": {}
}
```

Para endpoints protegidos enviar:

```http
Authorization: Bearer <jwt>
Content-Type: application/json
```

## Reglas Clave

- Roles para agencia: `AGENCY` o `ADMIN`.
- Un usuario autenticado puede crear solicitudes de reservacion.
- El cliente puede editar o eliminar una solicitud solo durante los primeros 2 minutos despues de creada.
- La agencia solo ve solicitudes cuando ya pasaron 2 minutos desde `createdAt`.
- Si `contactPreference = EMAIL`, backend intenta enviar correo al cliente si el correo esta verificado.
- Si `contactPreference = IN_APP`, el contacto debe manejarse por mensajeria interna.
- El chat se cierra cuando la solicitud queda en `confirmed`, `rejected` o `cancelled`.
- La agencia confirma pagos gestionados por fuera de la app cambiando el estado a `confirmed`.

## Estados De Reservacion

| Estado | Uso en frontend |
| --- | --- |
| `requested` | Solicitud recibida. Editable/eliminable por cliente solo primeros 2 minutos. |
| `contacted` | Agencia ya contacto o empezo gestion. |
| `awaiting_payment` | Agencia envio instrucciones o proceso de pago externo. |
| `confirmed` | Agencia confirmo pago/reserva. Cierra chat. |
| `rejected` | Agencia rechaza solicitud. Cierra chat. |
| `cancelled` | Solicitud cancelada. Cierra chat. |

Transiciones permitidas para agencia:

```text
requested -> contacted | rejected | cancelled
contacted -> awaiting_payment | rejected | cancelled
awaiting_payment -> confirmed | rejected | cancelled
```

## Modelo ReservationResponse

```json
{
  "id": "reserva-uuid",
  "tourPackageId": 6,
  "agencyId": 2,
  "packageTitle": "Paquete Huila",
  "totalAmount": 250000,
  "currency": "COP",
  "startDate": "2026-07-20",
  "endDate": "2026-07-22",
  "travelers": 2,
  "customerEmail": "cliente@example.com",
  "customerPhone": "3000000000",
  "contactPreference": "EMAIL",
  "message": "Deseo confirmar transporte",
  "consentAccepted": true,
  "consentVersion": "2026-07-04",
  "status": "requested",
  "paymentProvider": "agency_managed",
  "paymentStatus": "pending",
  "paymentId": null,
  "paidAt": null,
  "agencyNotes": null,
  "contactedAt": null,
  "confirmedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-07-05T15:40:13-05:00"
}
```

## Endpoints De Reservacion Cliente

### Crear solicitud

`POST /api/reservations`

Auth: usuario autenticado.

Request:

```json
{
  "tourPackageId": 6,
  "startDate": "2026-07-20",
  "endDate": "2026-07-22",
  "travelers": 2,
  "customerPhone": "3000000000",
  "contactPreference": "EMAIL",
  "message": "Deseo confirmar si el paquete incluye transporte",
  "consentAccepted": true,
  "consentVersion": "2026-07-04"
}
```

Notas:

- `contactPreference` solo acepta `EMAIL` o `IN_APP`.
- `endDate` es opcional; si no se envia, backend la calcula con la duracion del paquete.
- `customerPhone` es opcional y no se considera canal confiable.
- Respuesta `201`, `data = ReservationResponse`.

### Listar mis solicitudes

`GET /api/reservations/me?page=0&size=20`

Auth: usuario autenticado.

Query:

| Param | Default | Max | Nota |
| --- | ---: | ---: | --- |
| `page` | `0` | - | Pagina base cero. |
| `size` | `20` | `50` | Cantidad por pagina. |

Respuesta `200`, `data = ReservationResponse[]`.

### Ver detalle de mi solicitud

`GET /api/reservations/{reservationId}`

Auth: usuario autenticado propietario de la solicitud.

Respuesta `200`, `data = ReservationResponse`.

### Editar mi solicitud

`PATCH /api/reservations/{reservationId}`

Auth: usuario autenticado propietario.

Disponible solo durante los primeros 2 minutos despues de `createdAt`.

Request:

```json
{
  "startDate": "2026-07-21",
  "endDate": "2026-07-23",
  "travelers": 3,
  "customerPhone": "3000000000",
  "contactPreference": "IN_APP",
  "message": "Cambio de fecha solicitado"
}
```

Notas:

- No permite cambiar `tourPackageId`.
- Respuesta `200`, `data = ReservationResponse`.
- Si ya pasaron 2 minutos: `409`.

### Eliminar mi solicitud

`DELETE /api/reservations/{reservationId}`

Auth: usuario autenticado propietario.

Disponible solo durante los primeros 2 minutos despues de `createdAt`.

Respuesta:

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "deleted": true
  }
}
```

Si ya pasaron 2 minutos: `409`.

## Endpoints De Reservacion Agencia

Para panel con agencia seleccionada usar los endpoints con `{agencyId}`. Los endpoints `/me` se mantienen para el caso simple donde el token pertenece a una sola agencia.

### Listar solicitudes de mi agencia

`GET /api/agencies/me/reservations?status=requested&page=0&size=20`

Auth: `AGENCY` o `ADMIN`.

Query:

| Param | Default | Max | Nota |
| --- | ---: | ---: | --- |
| `status` | null | - | Opcional. Filtra por estado. |
| `page` | `0` | - | Pagina base cero. |
| `size` | `20` | `50` | Cantidad por pagina. |

Notas:

- Solo devuelve solicitudes con mas de 2 minutos de creadas.
- Respuesta `200`, `data = ReservationResponse[]`.

### Ver detalle de solicitud de mi agencia

`GET /api/agencies/me/reservations/{reservationId}`

Auth: `AGENCY` o `ADMIN`.

Notas:

- Solo devuelve si la solicitud pertenece a una agencia vinculada al usuario autenticado.
- Solo devuelve si ya pasaron 2 minutos desde `createdAt`.

Respuesta `200`, `data = ReservationResponse`.

### Actualizar estado de solicitud

`PATCH /api/agencies/me/reservations/{reservationId}/status`

Auth: `AGENCY` o `ADMIN`.

Request:

```json
{
  "status": "awaiting_payment",
  "notes": "Se envio al cliente el proceso de pago por el canal autorizado"
}
```

Estados aceptados:

```text
contacted
awaiting_payment
confirmed
rejected
cancelled
```

Notas para UI:

- Usar `contacted` cuando la agencia toma la solicitud.
- Usar `awaiting_payment` cuando la agencia ya envio instrucciones/proceso de pago.
- Usar `confirmed` cuando la agencia valido el pago por su cuenta. Backend marca `paymentProvider = agency_managed`, `paymentStatus = verified_by_agency` y llena `paidAt`.
- Usar `rejected` o `cancelled` para cerrar sin confirmar.
- `confirmed`, `rejected`, `cancelled` cierran el chat.
- Transicion invalida responde `409`.

Respuesta `200`, `data = ReservationResponse`.

### Listar solicitudes por agencia seleccionada

`GET /api/agencies/{agencyId}/reservations?status=requested&page=0&size=20`

Auth: `AGENCY` o `ADMIN`.

Reglas:

- `ADMIN` puede consultar cualquier `agencyId`.
- `AGENCY` solo puede consultar si su email esta vinculado a ese `agencyId`.
- Solo devuelve solicitudes con mas de 2 minutos de creadas.

Respuesta `200`, `data = ReservationResponse[]`.

### Ver detalle por agencia seleccionada

`GET /api/agencies/{agencyId}/reservations/{reservationId}`

Auth: `AGENCY` o `ADMIN`.

Respuesta `200`, `data = ReservationResponse`.

### Actualizar estado por agencia seleccionada

`PATCH /api/agencies/{agencyId}/reservations/{reservationId}/status`

Auth: `AGENCY` o `ADMIN`.

Request:

```json
{
  "status": "awaiting_payment",
  "notes": "Se enviaron las instrucciones de pago al cliente."
}
```

Respuesta `200`, `data = ReservationResponse`.

## Mensajeria Interna

El chat guarda mensajes por REST, pero la app puede recibir avisos en vivo con el stream SSE de notificaciones. La recomendacion es:

1. Mantener abierto `GET /api/notifications/stream`.
2. Cuando llegue evento `RESERVATION_MESSAGE`, volver a consultar los mensajes de esa solicitud.
3. Cuando llegue evento `RESERVATION_STATUS_CHANGED`, volver a consultar el detalle de esa solicitud.

Modelo `ReservationMessageResponse`:

```json
{
  "id": 10,
  "reservationId": "reserva-uuid",
  "senderEmail": "usuario@example.com",
  "senderType": "CUSTOMER",
  "message": "Hola, quiero confirmar el proceso de pago.",
  "createdAt": "2026-07-05T15:40:13-05:00"
}
```

`senderType` puede ser:

```text
CUSTOMER
AGENCY
```

### Cliente: listar mensajes

`GET /api/reservations/{reservationId}/messages?page=0&size=50`

Auth: usuario autenticado propietario.

Query:

| Param | Default | Max |
| --- | ---: | ---: |
| `page` | `0` | - |
| `size` | `50` | `100` |

Respuesta `200`, `data = ReservationMessageResponse[]`.

### Cliente: enviar mensaje

`POST /api/reservations/{reservationId}/messages`

Auth: usuario autenticado propietario.

Request:

```json
{
  "message": "Hola, quiero confirmar el proceso de pago."
}
```

Notas:

- `message` obligatorio, maximo 2000 caracteres.
- Si la solicitud esta `confirmed`, `rejected` o `cancelled`, responde `409`.
- Respuesta `201`, `data = ReservationMessageResponse`.

### Agencia: listar mensajes

`GET /api/agencies/me/reservations/{reservationId}/messages?page=0&size=50`

Auth: `AGENCY` o `ADMIN`.

Respuesta `200`, `data = ReservationMessageResponse[]`.

### Agencia seleccionada: listar mensajes

`GET /api/agencies/{agencyId}/reservations/{reservationId}/messages?page=0&size=50`

Auth: `AGENCY` o `ADMIN`.

Respuesta `200`, `data = ReservationMessageResponse[]`.

### Agencia: enviar mensaje

`POST /api/agencies/me/reservations/{reservationId}/messages`

Auth: `AGENCY` o `ADMIN`.

Request:

```json
{
  "message": "Te compartimos las instrucciones para realizar el pago."
}
```

Notas:

- `message` obligatorio, maximo 2000 caracteres.
- Si la solicitud esta `confirmed`, `rejected` o `cancelled`, responde `409`.
- Respuesta `201`, `data = ReservationMessageResponse`.

### Agencia seleccionada: enviar mensaje

`POST /api/agencies/{agencyId}/reservations/{reservationId}/messages`

Auth: `AGENCY` o `ADMIN`.

Request:

```json
{
  "message": "Te compartimos las instrucciones para realizar el pago."
}
```

Notas:

- `ADMIN` puede enviar en cualquier agencia.
- `AGENCY` solo puede enviar si esta vinculado al `agencyId`.
- Si la solicitud esta `confirmed`, `rejected` o `cancelled`, responde `409`.
- Respuesta `201`, `data = ReservationMessageResponse`.

## Endpoints De Agencia

### Crear agencia

`POST /api/agencies`

Auth: `AGENCY` o `ADMIN`.

Request:

```json
{
  "name": "Turismo Huila",
  "description": "Agencia local",
  "phone": "3000000000",
  "email": "agencia@example.com",
  "website": "https://agencia.example.com",
  "logoUrl": "https://cdn.example.com/logo.png"
}
```

Respuesta `201`, `data = Agency`.

### Asociar usuario a agencia

`POST /api/agencies/users`

Auth: `AGENCY` o `ADMIN`.

Request:

```json
{
  "emailAgencia": "agencia@example.com",
  "email": "usuario@agencia.com"
}
```

Respuesta `200`, `data = Agency`.

### Listar agencias publicas

`GET /api/agencies?limit=20&offset=0`

Auth: publico.

Respuesta `200`, `data = Agency[]`.

### Buscar agencias

`GET /api/agencies/search?q=huila&limit=20&offset=0`

Auth: publico.

Respuesta `200`, `data = Agency[]`.

### Agencias por usuario

`GET /api/agencies/by-user?email=usuario@example.com`

Auth: `AGENCY` o `ADMIN`.

Respuesta `200`, `data = Agency[]`.

### Mis agencias

`GET /api/agencies/my`

Auth: usuario autenticado.

Respuesta `200`, `data = Agency[]`.

### Paquetes por agencia

`GET /api/agencies/{id}/packages?limit=20&offset=0`

Auth: publico.

Respuesta `200`, `data = TourPackage[]`.

### Usuarios vinculados a agencia

`GET /api/agencies/{id}/users`

Auth: publico.

Respuesta:

```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "id": 5,
      "email": "usuario@agencia.com"
    }
  ]
}
```

### Actualizar usuario vinculado a agencia

`PATCH /api/agencies/{id}/users/{userId}`

Auth: `AGENCY` o `ADMIN`.

Request:

```json
{
  "email": "corregido@agencia.com"
}
```

Respuesta `200`, `data = "Usuario actualizado en la agencia"`.

### Eliminar usuario vinculado a agencia

`DELETE /api/agencies/{id}/users/{userId}`

Auth: `AGENCY` o `ADMIN`.

Respuesta `204`, sin body.

## Notificaciones En Tiempo Real

Las notificaciones sirven para avisar cambios sin recargar la aplicacion. No reemplazan el endpoint de mensajes ni el detalle de solicitud; indican que hubo un cambio y entregan `reservationId` y `agencyId` para refrescar la pantalla correcta.

Modelo `NotificationResponse`:

```json
{
  "id": 15,
  "recipientEmail": "cliente@example.com",
  "type": "RESERVATION_MESSAGE",
  "title": "Nueva respuesta de la agencia",
  "message": "La agencia respondió tu solicitud reserva-uuid",
  "reservationId": "reserva-uuid",
  "agencyId": 2,
  "read": false,
  "createdAt": "2026-07-05T17:17:15-05:00"
}
```

Tipos actuales:

| Tipo | Cuándo llega | Acción recomendada en frontend |
| --- | --- | --- |
| `RESERVATION_MESSAGE` | Agencia o cliente envió mensaje. | Refrescar mensajes de `reservationId` y mostrar badge/toast. |
| `RESERVATION_STATUS_CHANGED` | Agencia/admin cambió estado. | Refrescar detalle/listado de la solicitud. |

### Stream SSE

`GET /api/notifications/stream`

Auth: usuario autenticado.

Respuesta: `text/event-stream`.

Ejemplo frontend:

```js
const source = new EventSource(`${API_BASE_URL}/api/notifications/stream`, {
  withCredentials: true
});

source.addEventListener('RESERVATION_MESSAGE', (event) => {
  const notification = JSON.parse(event.data);
  // refrescar chat notification.reservationId
});

source.addEventListener('RESERVATION_STATUS_CHANGED', (event) => {
  const notification = JSON.parse(event.data);
  // refrescar detalle/listado notification.reservationId
});
```

Nota: si el frontend no puede enviar `Authorization` con `EventSource` nativo, usar una libreria SSE con headers o conectar usando el mecanismo de auth soportado por la app.

### Listar notificaciones

`GET /api/notifications?unreadOnly=false&page=0&size=30`

Auth: usuario autenticado.

Respuesta `200`, `data = NotificationResponse[]`.

### Marcar una notificación como leída

`PATCH /api/notifications/{notificationId}/read`

Auth: usuario autenticado dueño de la notificación.

Respuesta `200`, `data = NotificationResponse`.

### Marcar todas como leídas

`PATCH /api/notifications/read-all`

Auth: usuario autenticado.

Respuesta:

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "updated": 3
  }
}
```

### Dashboard de agencia

`GET /api/agencies/dashboard?from=2026-07-01&to=2026-07-31&limit=10`

Auth: `AGENCY` o `ADMIN`.

Query:

| Param | Default | Nota |
| --- | --- | --- |
| `email` | email autenticado | Solo `ADMIN` puede consultar otro email. |
| `from` | `to - 30 dias` | Formato `yyyy-MM-dd`. |
| `to` | fecha actual backend | Formato `yyyy-MM-dd`. |
| `limit` | `10` | Maximo `50`. |

Respuesta `200`, `data = AgencyDashboard[]`.

Forma de cada item:

```json
{
  "agency": {},
  "packages": [],
  "topPackages": [
    {
      "packageId": 101,
      "title": "Paquete San Agustin",
      "sold": 24,
      "revenue": 12000000
    }
  ],
  "topPlaces": [],
  "salesSummary": {
    "totalSold": 120,
    "totalRevenue": 48000000
  }
}
```

### Actualizar agencia

`PATCH /api/agencies/{id}`

Auth: `AGENCY` o `ADMIN`.

Reglas:

- `ADMIN` puede editar cualquier agencia.
- `AGENCY` solo puede editar agencias a las que esta vinculado.

Request:

```json
{
  "name": "Turismo Huila Actualizada",
  "description": "Nueva descripcion",
  "phone": "3000000001",
  "email": "agencia@example.com",
  "website": "https://agencia.example.com",
  "logoUrl": "https://cdn.example.com/logo.png"
}
```

Respuesta `200`, `data = Agency`.

### Eliminar agencia

`DELETE /api/agencies/{id}`

Auth: `AGENCY` o `ADMIN`.

Reglas:

- `ADMIN` puede eliminar cualquier agencia.
- `AGENCY` solo puede eliminar agencias a las que esta vinculado.

Respuesta `204`, sin body.

## Sugerencia De Flujo UI

1. Cliente crea solicitud desde detalle de paquete con `POST /api/reservations`.
2. Mostrar pantalla de exito con contador de 2 minutos para editar/eliminar.
3. Si pasan 2 minutos, ocultar acciones de editar/eliminar y mostrar estado `requested`.
4. Agencia consulta `GET /api/agencies/{agencyId}/reservations?status=requested` cuando el panel tiene agencia seleccionada.
5. Agencia abre detalle, envia mensaje interno o cambia estado a `contacted`.
6. Si el pago lo gestiona la agencia, cambia a `awaiting_payment` y usa chat para instrucciones.
7. Cuando valide pago, agencia cambia a `confirmed`; frontend debe bloquear input del chat.
8. Si agencia cambia a `rejected` o `cancelled`, frontend tambien debe bloquear input del chat.

## Codigos De Error Esperados

| Codigo | Caso comun |
| ---: | --- |
| `400` | Body invalido, fechas invalidas, `contactPreference` invalido, `message` vacio. |
| `401` | Falta token o token invalido. |
| `403` | Rol insuficiente o agencia no vinculada al usuario. |
| `404` | Solicitud, paquete o agencia no encontrada. |
| `409` | Ventana de 2 minutos expirada, transicion invalida o chat cerrado. |

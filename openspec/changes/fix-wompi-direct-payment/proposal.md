## Why

El flujo de pago directo con Wompi persiste eventos antes de procesarlos, pero los reintentos de un evento que falló quedan bloqueados por la deduplicación. Además, el método llamado `appendSystemMessageOnce` inserta mensajes sin una garantía de idempotencia. Esto puede dejar una reserva pagada sin efectos completos o duplicar mensajes y requiere una corrección antes de confiar en los webhooks como fuente de confirmación.

## What Changes

- Corregir el procesamiento idempotente de webhooks Wompi, permitiendo reintentar eventos persistidos con error y evitando reprocesar eventos ya completados.
- Confirmar la reserva únicamente cuando el estado del proveedor sea aprobado y aplicar el estado de pago correspondiente para estados pendientes, rechazados, expirados o de error.
- Registrar un único mensaje de sistema de pago exitoso por reserva/transacción y dejar el chat cerrado cuando la reserva quede confirmada.
- Hacer visibles los errores parciales del webhook mediante estado de evento, registro de error y respuesta HTTP no exitosa cuando el procesamiento no termine.
- Mantener los contratos existentes de checkout, consulta de estado y webhook salvo los códigos de error necesarios para reflejar fallos reales.

## Capabilities

### New Capabilities

- `wompi-direct-payment`: Contrato del flujo de checkout, webhook, estados de pago, confirmación de reserva, mensaje de sistema y cierre del chat.

### Modified Capabilities

- Ninguna. No existen especificaciones principales previas en `openspec/specs/`.

## Impact

- Casos de uso y puertos de pago/reserva en `domain/usecase` y `domain/model`.
- Adaptadores R2DBC y migración PostgreSQL de `payment_events`, `payment_transactions` y mensajes de reserva.
- Handler y rutas WebFlux del webhook Wompi.
- Pruebas unitarias/reactivas de pago y pruebas de integración de persistencia.
- No incluye cambios de infraestructura Terraform ni inicialización de `turismo-infra`.

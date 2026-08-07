## 1. Diagnóstico y persistencia idempotente

- [x] 1.1 Confirmar con pruebas el ciclo actual de `payment_events`, incluidos eventos procesados, fallidos y duplicados.
- [x] 1.2 Ajustar el modelo/puerto/adaptador de eventos para distinguir evento ya procesado de evento persistido con error y permitir reintentos seguros.
- [x] 1.3 Añadir o ajustar restricciones PostgreSQL para la identidad única del evento y documentar la migración sin perder historial.

## 2. Aplicación de estados y efectos de reserva

- [x] 2.1 Implementar la transición idempotente de transacción y reserva para aprobado, pendiente/procesando, rechazado/error/voided y expirado.
- [x] 2.2 Hacer idempotente la inserción del mensaje de pago exitoso mediante una garantía persistente y conservar el cierre del chat en `confirmed`.
- [x] 2.3 Propagar y registrar errores parciales; marcar el evento procesado solo después de completar todos los efectos requeridos.

## 3. Contratos y pruebas

- [x] 3.1 Verificar que checkout y consulta de estado mantienen autorización por propietario/agencia y que el webhook mantiene validación de firma y referencia.
- [x] 3.2 Añadir pruebas unitarias/reactivas para pago aprobado, mensaje exitoso, chat cerrado, webhook repetido, pendiente/rechazado y errores parciales con reintento.
- [x] 3.3 Añadir pruebas de adaptador/migración para concurrencia e idempotencia en PostgreSQL y ejecutar la suite Gradle afectada.

## 4. Validación y entrega

- [x] 4.1 Comparar los contratos implementados con todos los escenarios de `specs/wompi-direct-payment/spec.md`.

## Purpose

Define the observable, secure and idempotent behavior required to use Wompi direct payment as the authority for confirming a reservation and closing its conversation.

## ADDED Requirements

### Requirement: Approved Wompi payments confirm the reservation
The system SHALL apply an approved Wompi payment only to the reservation identified by the transaction reference, set its payment status to `paid`, retain the provider transaction identifier and change a reservation in `awaiting_payment` to `confirmed`.

#### Scenario: Pago aprobado confirma la reserva
- **WHEN** a valid Wompi webhook for a known checkout reports provider status `APPROVED`
- **THEN** the transaction is marked `paid`, the reservation is marked `confirmed` and payment timestamps and provider identifiers are persisted

### Requirement: Successful payment is recorded in the reservation conversation
The system SHALL record one system message indicating successful Wompi payment for the affected reservation and SHALL prevent repeated delivery of the same payment outcome from creating another equivalent message.

#### Scenario: Se registra el mensaje de pago exitoso
- **WHEN** the first approved payment event is applied successfully
- **THEN** the reservation conversation contains one system message stating that payment was received and the reservation was confirmed

#### Scenario: El chat queda cerrado
- **WHEN** the approved payment changes the reservation status to `confirmed`
- **THEN** customer and agency message attempts are rejected as a closed chat while existing messages remain queryable

### Requirement: Wompi webhook processing is idempotent and retryable
The system SHALL deduplicate a webhook by the provider event identity or documented fallback identity, SHALL not repeat reservation or message effects for a processed event, and SHALL allow a later delivery to retry an event that was persisted with processing failure.

#### Scenario: Un webhook repetido no duplica efectos
- **WHEN** the same valid webhook is delivered more than once after successful processing
- **THEN** subsequent deliveries return the idempotent result without changing the reservation again or inserting another payment message

#### Scenario: Un error parcial no queda oculto
- **WHEN** the event is persisted but applying the transaction, reservation or message fails
- **THEN** the event remains unprocessed with its error recorded, the webhook response is non-successful, and a later delivery can retry it

### Requirement: Non-approved provider states do not confirm a reservation
The system SHALL map pending or processing provider states to non-final payment states, map declined, error, voided and expired outcomes to non-paid states, and SHALL never confirm a reservation for any of those outcomes.

#### Scenario: Un pago pendiente no confirma la reserva
- **WHEN** a valid Wompi webhook reports a pending or processing status
- **THEN** the transaction and reservation retain a non-final payment state and the reservation is not confirmed

#### Scenario: Un pago rechazado no confirma la reserva
- **WHEN** a valid Wompi webhook reports `DECLINED`, `ERROR`, `VOIDED` or `EXPIRED`
- **THEN** the reservation remains unconfirmed, the corresponding failed or expired state is persisted, and no success message is created

### Requirement: Webhook and checkout contracts preserve authorization and validation
The system SHALL validate the Wompi signature, reference, known transaction, amount/currency relationship and reservation ownership established by the current checkout flow; customer checkout and status endpoints SHALL remain limited to the authenticated reservation owner, while the public webhook SHALL expose only the existing acknowledgment/error contract.

#### Scenario: Webhook inválido no produce efectos
- **WHEN** the signature is invalid, the reference is absent or the transaction cannot be resolved
- **THEN** the request is rejected, no reservation is confirmed and no payment-success message is inserted

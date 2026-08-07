## Context

The current flow is implemented as a reactive hexagonal path: `PaymentHandler` receives the webhook, `PaymentUseCase` validates and applies it, R2DBC adapters persist `payment_events` and `payment_transactions`, and `ReservationRepositoryAdapter` updates the reservation. `payment_events` has uniqueness constraints, but the current `saveIfAbsent` path does not distinguish a previously processed event from a previously failed event. The success-message helper also performs an unconditional insert. Reservation status `confirmed` is already treated as a closed chat by `ReservationMessageUseCase`.

## Goals / Non-Goals

**Goals:**

- Make event processing retryable after a partial failure and idempotent after success.
- Make reservation/payment/message effects consistent with approved versus non-approved provider states.
- Preserve the existing WebFlux endpoints and payment state vocabulary unless a required error contract is explicitly documented.
- Add deterministic tests for duplicate delivery, partial failure, and chat closure.

**Non-Goals:**

- Replacing Wompi checkout or changing the provider integration protocol.
- Introducing a new message service or a separate chat lifecycle model.
- Changing agency-managed payment behavior.

## Decisions

- **Event lifecycle is explicit and retryable.** Add repository/use-case behavior that reads the persisted event state: a processed event is a no-op; an unprocessed event with an error is eligible for retry. Preserve the unique event identity constraints. An in-memory deduplication cache is rejected because it is not safe across instances or restarts.
- **Success message idempotency is database-backed.** Identify the success message by reservation, payment outcome and/or provider transaction using a constrained repository operation or an equivalent transactional check. A read-then-insert without a uniqueness guarantee is rejected because concurrent webhooks can race.
- **Reservation transition remains conditional.** Keep confirmation conditional on `awaiting_payment`; an already confirmed reservation may accept a harmless repeat of the same paid outcome but must not regress to a non-paid state. Cross-status conflicts must be surfaced rather than silently overwritten.
- **Partial effects are observable.** Mark event processing complete only after transaction, reservation and message effects succeed. Persist the failure reason and return an error from the handler when processing is incomplete; do not acknowledge a failed application as a successful webhook.
- **Reactive execution is preserved.** Use R2DBC/reactive composition and database constraints; no blocking calls, sleeps, or imperative transaction wrappers that block Netty event-loop threads.

## Risks / Trade-offs

- [Risk] A reservation update may succeed before message insertion fails → retain the event as failed, make retry idempotent at the reservation and message boundaries, and emit structured logs/metrics for reconciliation.
- [Risk] Provider retries can arrive concurrently → enforce uniqueness in PostgreSQL and make each effect conditional/idempotent.
- [Risk] Returning non-2xx causes Wompi to retry → this is intentional for recoverable partial failures; invalid payloads must be logged with a safe reason and rejected without side effects.

## Migration Plan

1. Add or adjust the database uniqueness/idempotency support and repository methods.
2. Deploy use-case and handler changes, then run existing and new reactive tests.
3. Reconcile any historical `payment_events` with `processed = false` before enabling production retries.
4. Roll back application code if necessary; retain additive schema constraints and use a forward migration to relax them only with explicit approval.

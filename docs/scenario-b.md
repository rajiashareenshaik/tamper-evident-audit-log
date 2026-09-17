# Scenario B

## Objective

Scenario B extends the audit service with retention, structured redaction, and bulk export.

## Retention

Older records will be archivable through metadata rather than modifying the immutable event itself.

Verification must continue to work when records are legitimately archived.

## Structured Redaction

Sensitive fields that may require later removal will be identified at ingestion time.

The immutable audit record will keep a cryptographic commitment to the original value.

The readable value will be stored separately in encrypted form.

Redaction will remove access to the readable value without modifying the original event hash.

## Bulk Export

The service will support exporting events by actor ID or resource ID.

The export will include enough integrity metadata to verify that the exported content has not changed after export.

The initial design will use a signed manifest for bundle verification.

## Definition of Done

Retention does not break verification.

Redaction does not change the original chain hash.

Redacted values are no longer returned.

Export bundles can be independently verified.

Modified export bundles fail verification.

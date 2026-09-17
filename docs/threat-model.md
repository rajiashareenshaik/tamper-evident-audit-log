# Threat Model

## Purpose

This document defines the main integrity and security risks considered in the prototype.

## Threats Considered

Direct modification of an audit event in the database

Direct deletion of a historical event

Deletion of the most recent event

Modification of stored hash values

Concurrent writes creating an invalid chain

Accidental application-level updates

Sensitive data being logged or exposed

Modification of exported audit data

## Current Controls

Append-only application API

Cryptographic hash chain

Stored chain head

Database transactions

Row-level locking for chain-head updates

Validation of event input

No update or delete API for audit events

Structured verification endpoint

Planned encryption and HMAC-based commitments for redactable values

Planned digital signatures for export bundles

## Important Limitation

A database administrator with unrestricted access could potentially rewrite all audit records, recalculate all hashes, and update the stored chain head.

The current prototype detects partial or unauthorized changes to stored history, but it does not provide an external trust anchor.

A production version should periodically publish or store signed chain checkpoints outside the primary database.

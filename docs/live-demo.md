# Live Demo Plan

The live demo should show the system working from a clean and repeatable starting point.

## Demo Sequence

Start PostgreSQL and the application

Check the health endpoint

Create the first audit event

Create additional audit events

Query by actor ID

Query by resource ID

Query by event type

Demonstrate pagination

Run full-chain verification

Confirm the chain is valid

Modify an existing record directly in PostgreSQL

Run verification again

Show the first detected inconsistency

Restore or reset the environment

Demonstrate retention

Demonstrate redaction

Verify that redaction does not break the chain

Create an export bundle

Verify the export bundle

Modify the exported bundle

Show signature verification failure

Demonstrate the compliance reporting endpoint

## Live Change Preparation

The code should be organized so a small requirement change can be made without touching unrelated areas.

Likely changes may include:

adding a filter

adding a new event field

adding correlation ID support

changing event ordering

adding a tenant field

adding another compliance field

supporting verification from a specific sequence

# Scenario C

## Original Product Requirement

Regulators need to be able to audit access to client account data.

## Initial Assessment

The requirement is not specific enough to implement directly.

Before implementation, the following questions need to be clarified:

What counts as access

Whether denied access attempts must be captured

Which actors are in scope

Which systems are in scope

What account identifier may be stored

Whether access purpose is required

Which data categories were viewed

How long the records must be retained

Who can query or export the reports

Whether regulator-specific report formats are required

## Working Assumption

For the prototype, the system will record successful and denied access attempts to client account data.

The event will include the actor, account identifier, action, outcome, source application, request identifier, and data categories where available.

Actual client account data will not be stored in the compliance event.

## Scope Boundary

The prototype will not include a regulator-facing user interface, legal interpretation of retention periods, enterprise identity integration, or regulator-specific report templates unless those requirements are explicitly added.

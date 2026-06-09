# PDR-004 Documentation Audit

## Problem & Context

- Documentation degrades over time: declarations duplicate, conflict, drift, or violate their own templates.
- Degraded documentation misleads readers and AI agents, which act on stated rules as if they were current and correct.

## Decision

- Audit MUST exclude symbolic links from analysis
- Audit MUST output `documentation-audit.toml`

### `documentation-audit.toml`

- `[[issue]]`
  - `id` — unique, stable identifier
  - `type` MUST be either of:
    - `redundancy` — duplicated declarations
    - `alignment` — conflicting declarations
    - `cohesion` — mixing concerns inside a single document
    - `consistency` — same term used with different meanings
    - `correctness` — non-adherence to rules and templates
    - `style` — formatting, typos, and similar surface defects
  - `locations` MUST be an array of location references
  - `message` MUST state what the issue is and why it qualifies

## Rationale

- A closed set of types makes findings triageable and comparable across audits.
- Separating redundancy, alignment, cohesion, consistency, correctness, and style isolates distinct failure modes, each with a different kind of fix.
- A fixed TOML schema makes findings machine-readable, deduplicable by `id`, and trackable to resolution.

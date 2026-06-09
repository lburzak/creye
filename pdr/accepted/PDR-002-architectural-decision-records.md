# PDR-002 Architectural Decision Records

## Problem & Context
- Architectural direction is difficult, and sometimes impossible, to infer from code alone.
- Incomplete architectural context is especially harmful for AI-assisted development, where agents can make locally plausible changes that conflict with the intended long-term direction.
- Many architectural decisions are discussed but never committed to a durable, discoverable record.
- Important architectural context often remains only in team members' heads, private notes, chat threads, or meeting memories.
- Some implementation tasks reveal the need for complex refactoring that exceeds the task scope. It is hidden, if not documented immediately.
- Without a documented decision, future work can repeat the same discussion, miss previous reasoning, or implement changes that conflict with the intended architecture.
## Decision
Architectural Decision Record (ADR)

- MUST be indexed for coding tasks
- MUST be followed, if its status is Accepted
- MUST NOT be followed, if its status is Rejected or Deprecated
- ID MUST be within 0...999 range, padded with zeros to maintain order

- Title
  - MUST be Title Case
  - MUST be short
  - MUST be declarative
  - MUST name the problem domain the record decides — the question or topic under decision — not the chosen answer, mechanism, or implementation detail
    - Test: the title states *what is being decided*, not *what was decided*. A broad topic noun (e.g. `Persistence`, `Configuration`) is correct because it names the decision area; the body holds the answer
  - Examples (name the problem domain — correct)
    - Persistence
    - HTTP Networking
    - Testing Convention
    - Relational Data Persistence
  - Counter-examples (name the chosen answer — wrong)
    - `PostgreSQL Storage` instead of `Persistence`
    - `Retrofit Client` instead of `HTTP Networking`
    - `JUnit5 Setup` instead of `Testing Convention`

- Status
  - MUST be either of: Draft, Accepted, Rejected, Deprecated
  - MUST be Draft while work-in-progress, actively discussed, not ready to commit to
  - MUST be Accepted when the team commits to the decision as current architectural direction
  - MUST be Rejected when the team decides not to commit to the proposed decision because it is not currently considered practical, beneficial, feasible, or optimal
  - MUST be Deprecated when it was previously Accepted but is no longer considered practical, beneficial, feasible, or optimal, or when it is superseded by another ADR

- Document
  - Name MUST match format `ADR-{{id}}-{{title}}.md`
    - `ADR-` prefix MUST be uppercase literal
    - `{{title}}` MUST be kebab-case
  - Path MUST be a subdirectory of `adr` indicating its status e.g. `adr/accepted`
    - MUST be Markdown
  - MUST contain in order
    - Header `# ADR-{{ID}} {{Title}}`
    - **Superseded by** line, if status is **Deprecated** and superseded by another ADR
    - **Problem & Context** section
      - MUST be a bullet point list for clarity
      - MUST describe a problem that needs to be resolved by the ADR and a context in which it surfaced
    - **Constraints** section
      - MUST be minimal
      - MUST be omitted if no relevant constraints exist
      - MUST be a numbered list for easy referencing
      - MUST be in RFC 2119 format
      - Any deliberation between alternate decision and the actual decision rationale MUST be grounded in it and clearly refer to it
    - **Decision** section
      - MUST list rules that must be followed to comply with the decision
      - SHOULD feature clear imperatives like RFC 2119
      - MAY be omitted, if status is **Draft**
    - **Rationale** section
      - MAY be omitted, if status is **Draft**
    - **Rejection reason** section, if status is **Rejected**
  - MAY contain **Notes** section
  - MUST NOT contain **Status** as it is inferred from document path

## Rationale
- Code and documentation explain what the system does. ADRs explain why the system is shaped that way.
- Formal ADRs make architectural decisions accessible to both people and AI agents.
- Durable ADRs reduce repeated discussion and improve the quality of future implementation decisions.
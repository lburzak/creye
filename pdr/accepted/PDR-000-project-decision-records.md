# PDR-000 Project Decision Records

## Problem & Context
- Formalization of workflows and procedures
	- Reduces mental effort to understand them
	- Reduces errors while applying them
	- Enforces constant improvement of them, by providing the basis to build upon
## Decision
Project Decision Record (PDR)
- MUST be indexed for management and planning tasks
- MUST be followed, if its status is Accepted
- MUST NOT be followed, if its status is Rejected or Deprecated
- ID MUST be within 0...999 range, padded with zeros to maintain order

- Title
  - MUST be Title Case
  - MUST be short
  - MUST describe problem, workflow, or procedure, not implementation detail
  - MUST be declarative
  - Examples
    - Release Procedure
    - Sprint Planning
    - Code Review Workflow
    - AI Assisted Development Context

- Status
  - MUST be either of: Draft, Accepted, Rejected, Deprecated
  - MUST be Draft while work-in-progress, actively discussed, not ready to commit to
  - MUST be Accepted when the team commits to the decision as current project operating direction
  - MUST be Rejected when the team decides not to commit to the proposed decision because it is not currently considered practical, beneficial, feasible, or optimal
  - MUST be Deprecated when it was previously Accepted but is no longer considered practical, beneficial, feasible, or optimal, or when it is superseded by another PDR

- Document
  - Name MUST match format `pdr-{{id}}-{{title}}.md`
    - Name MUST be kebab-case
  - Path MUST be a subdirectory of `pdr` indicating its status e.g. `pdr/accepted`
    - MUST be Markdown
  - MUST contain in order
    - Header `# PDR-{{ID}} {{Title}}`
    - **Superseded by** line, if status is **Deprecated** and superseded by another PDR
    - **Problem & Context** section
      - MUST be a bullet point list for clarity
      - MUST describe a workflow, procedure, coordination, or project operating problem that needs to be resolved by the PDR and the context in which it surfaced
    - **Constraints** section
      - MUST be a numbered list for easy referencing
      - MUST be in RFC 2119 format
      - Any deliberation between alternate decision and the actual decision rationale MUST be grounded in it and clearly refer to it
    - **Decision** section
      - MUST list rules that must be followed to comply with the decision
      - Rule MUST be written in imperatives like RFC 2119
    - **Rationale** section
    - **Rejection reason** section, if status is **Rejected**
  - MAY contain **Notes** section
  - MUST NOT contain **Status** as it is inferred from document path

## Rationale
- Formal PDRs make project decisions accessible to both people and AI agents.
- PDRs provide a composable model of project configuration by separating durable procedural decisions into small, indexed records.
- Embedding decisions in context makes them valuable for future improvements, because later work can reuse the recorded reasoning instead of rediscovering it.
- Durable PDRs reduce repeated discussion and improve the quality of future management, planning, and implementation decisions.

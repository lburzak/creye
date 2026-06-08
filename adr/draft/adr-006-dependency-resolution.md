# ADR-006 Dependency Resolution

## Problem & Context
- The graph must show inbound, outbound, and internal dependencies relative to changed symbols.
- The campaign does not require full semantic parity with IntelliJ dependency analyzer behavior.
- Dependency resolution must include dependencies among changed symbols across different files.
- Kotlin references may be unresolved because of generated sources, incomplete project state, indexing, or unsupported language constructs.
## Constraints
1. The implementation MUST prefer IntelliJ Platform and Kotlin symbol inspection APIs where available.
2. The implementation MUST classify dependency edges as inbound, outbound, or internal relative to changed symbols.
3. The implementation MUST include dependencies among changed symbols across different files.
4. The implementation MUST tolerate unresolved references without failing the whole analysis.
5. The implementation MUST expose diagnostics for unresolved references and unsupported dependency cases.
## Decision
- TBD through targeted discussion.
## Rationale
- TBD through targeted discussion.

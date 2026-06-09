# ADR-011 Configuration

## Problem & Context
- The MVP must compare one configured git branch against the current working directory.
- Branch configuration can live in an action prompt, project setting, persisted plugin state, or another IDE mechanism.
- The selected branch must be clear before analysis starts.
- Configuration mistakes should fail early with useful diagnostics rather than producing an empty or misleading graph.
## Constraints
1. The implementation MUST support configuring exactly one comparison branch for the MVP.
2. The implementation MUST make the selected branch visible before analysis runs.
3. The implementation MUST validate that the configured branch exists.
4. The implementation MUST stop analysis when branch configuration is missing or invalid.
5. The implementation SHOULD avoid broad configuration surfaces not required by the MVP.

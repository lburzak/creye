# ADR-010 Diagnostics

## Problem & Context
- The campaign requires enough diagnostic information to explain missing or unresolved symbols.
- Missing dependencies can come from git comparison, project modeling, symbol extraction, dependency resolution, graph construction, or rendering.
## Constraints
1. The implementation MUST expose diagnostic information for missing or unresolved symbols (realizing CAMPAIGN constraint 5).
2. Diagnostics MUST distinguish git, project model, symbol extraction, dependency resolution, graph construction, and rendering problems.

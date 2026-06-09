# ADR-010 Diagnostics And Validation

## Problem & Context
- The campaign requires enough diagnostic information to explain missing or unresolved symbols during MVP validation.
- The campaign requires a representative Kotlin project and exact commands or manual IDE steps before completion.
- Missing dependencies can come from git comparison, project modeling, symbol extraction, dependency resolution, graph construction, or rendering.
- Without explicit validation criteria, the MVP can look complete while hiding unresolved or partial analysis behavior.
## Constraints
1. The implementation MUST expose diagnostic information for missing or unresolved symbols.
2. Diagnostics MUST distinguish git, project model, symbol extraction, dependency resolution, graph construction, and rendering problems.
3. MVP validation MUST include changes in at least two Kotlin files.
4. MVP validation MUST verify changed symbols, internal dependencies, external dependencies, cohesion dependencies, collapse aggregation, and class expansion.
5. The validation project and exact commands or manual IDE steps MUST be documented before campaign completion.

# ADR-013 Analysis Validation

## Problem & Context
- Validation requires a representative Kotlin project and exact commands or manual IDE steps.
- Without explicit validation criteria, the analysis can appear correct while hiding unresolved or partial behavior.
## Constraints
1. Validation MUST include changes in at least two Kotlin files.
2. Validation MUST verify changed symbols, internal dependencies, external dependencies, cohesion dependencies, collapse aggregation, and class expansion.
3. The validation project and exact commands or manual IDE steps MUST be documented.

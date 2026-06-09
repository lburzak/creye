# ADR-013 MVP Validation

## Problem & Context
- The campaign requires a representative Kotlin project and exact commands or manual IDE steps before completion.
- Without explicit validation criteria, the MVP can look complete while hiding unresolved or partial analysis behavior.
## Constraints
1. MVP validation MUST include changes in at least two Kotlin files.
2. MVP validation MUST verify changed symbols, internal dependencies, external dependencies, cohesion dependencies, collapse aggregation, and class expansion.
3. The validation project and exact commands or manual IDE steps MUST be documented before campaign completion.

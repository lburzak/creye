# ADR-001 IntelliJ Plugin Architecture

## Problem & Context
- The campaign requires an IntelliJ plugin editor panel that displays Kotlin symbol dependency graphs.
- The plugin needs a clear entry point for launching analysis from the IDE.
- IDE integration, analysis orchestration, graph modeling, and graph rendering have different responsibilities and failure modes.
- Without explicit boundaries, early MVP code can couple IntelliJ action handling to git comparison, Kotlin PSI traversal, dependency analysis, and rendering.
## Constraints
1. The implementation MUST use IntelliJ Platform APIs for plugin actions, project access, background execution, and editor integration where practical.
2. The implementation MUST keep analysis orchestration separate from UI rendering.
3. The implementation MUST keep graph model construction independent from the editor panel.
4. The implementation MUST support cancellation for long-running analysis.
5. The implementation SHOULD keep plugin lifecycle code thin enough that analysis behavior can be tested independently.
## Decision
- TBD through targeted discussion.
## Rationale
- TBD through targeted discussion.

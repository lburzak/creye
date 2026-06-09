# ADR-002 Kotlin Project Model

## Problem & Context
- The graph hierarchy must span Gradle modules, packages, files, classes, and symbols.
- Kotlin files may belong to different IntelliJ modules, Gradle source sets, and package declarations.
- The project model needs enough structural accuracy to make the graph understandable on a representative Kotlin Gradle project.
- Perfect handling of generated sources, multiplatform source sets, or unresolved project states is not required.
## Constraints
1. The implementation MUST prefer IntelliJ Platform APIs for project structure.
2. The implementation MUST prefer Kotlin PSI or Kotlin symbol inspection APIs for Kotlin declarations.
3. The implementation MUST represent module, package, file, class, function, and property hierarchy where available.
4. The implementation MUST tolerate unresolved or partially indexed project states.
5. The implementation MUST expose diagnostics when ownership of a file or symbol cannot be determined.

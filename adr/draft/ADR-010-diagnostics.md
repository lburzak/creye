# ADR-010 Diagnostics

## Problem & Context
- The system must provide enough diagnostic information to explain missing or unresolved symbols.
- Missing dependencies can come from git comparison, project modeling, symbol extraction, dependency resolution, graph construction, or rendering.
## Constraints
1. The implementation MUST expose diagnostic information for missing or unresolved symbols.
2. Diagnostics MUST distinguish git, project model, symbol extraction, dependency resolution, graph construction, and rendering problems.

## Decision
- This record owns the diagnostic *type* and the *source taxonomy*. ADR-007 owns diagnostic *attachment* (which graph element a diagnostic hangs on and its identity key). ADR-010 MUST consume the ADR-007 attachment rather than redefine it.
- A diagnostic MUST be a value of the shape `{ source, severity, message, location?, attachment? }`, collected into one cross-cutting set spanning all pipeline stages. This set MUST be the single source of truth, projected both onto the graph (ADR-007) and into the panel.
- `source` MUST be a closed enumeration of six values mapped 1:1 to the producing pipeline-stage ADRs: git (ADR-003), project model (ADR-002), changed symbol detection (ADR-004), dependency resolution (ADR-006), graph construction (ADR-007), and rendering (ADR-009). The enumeration MUST use each stage ADR's own vocabulary; the symbol source MUST be named after ADR-004 "changed symbol detection", not "symbol extraction".
- Node-identity (ADR-005) failures MUST NOT define a seventh source. They MUST surface under whichever stage encountered them — dependency resolution or graph construction.
- The rendering source MUST produce diagnostics only for render or layout failures that have no domain graph element. Diagnostics that merely display upstream conditions, or that trace to a domain edge key (including collapsed or aggregated edges per ADR-008), MUST NOT be counted as rendering-produced. Rendering-produced diagnostics MUST attach at graph level per ADR-007.
- `severity` MUST be required and MUST be one of `error`, `warning`, `info`. The producing stage MUST assign severity at diagnostic creation. Severity MUST NOT control pipeline flow; whether a condition halts analysis is a separate concern.
- A diagnostic carries up to two navigation targets: `attachment` (the ADR-007 attachment key, resolving to the graph element) and `location` (`file:line`, resolving to source code). Both MUST be optional. The producing stage SHOULD populate at least one. The panel MUST degrade to a text-only row when neither resolves.
- Diagnostics MUST be presented through a view in the graph tool window (the ADR-009 render surface), styled as a tabs-and-filters panel over the cross-cutting set. The panel MUST NOT be implemented as IntelliJ-native `ProblemsView` integration.

## Rationale
- Splitting type/sources (ADR-010) from attachment (ADR-007) keeps the decided, critical ADR-007 closed while letting this medium-priority record fill the undefined diagnostic type. Folding either way would reopen ADR-007 or create two sources of truth for attachment.
- A closed six-value source enum makes the panel tabs fixed and exhaustive, and aligning names to each stage ADR's vocabulary prevents a third competing term for symbol diagnostics.
- Folding ADR-005 identity failures into the stage that hit them avoids a source with no clean pipeline home.
- Narrowing rendering to a producer only for element-less failures keeps it from double-counting upstream diagnostics while still giving layout failures a home; ADR-007's graph-level attachment already covers them.
- Required three-level severity is load-bearing for the tabs-and-filters direction, and the sources differ in weight (a git comparison failure is not an expected unresolved reference). Keeping severity out of control flow stops it from quietly becoming a halt signal.
- Optional dual navigation targets fit the availability differences across sources: unresolved references carry both, render failures carry neither node nor location, git failures carry at most a file.
- Hosting the panel in the graph tool window keeps graph-node navigation native; IntelliJ `ProblemsView` navigates to PSI/`file:line` and cannot target custom graph nodes, which are the primary navigation target.

## Notes
Working direction retained: an IntelliJ Problems-tool-style panel (tabs + filters) backed by a single cross-cutting diagnostic model.

A later additive mirror of file-located diagnostics into IntelliJ `ProblemsView` is not precluded, but the authoritative panel remains the graph-tool-window view.

Rejected alternatives, kept so they are not relitigated:
- **Folding the diagnostic type into ADR-007, or attachment into ADR-010.** Rejected: the first reopens a decided critical ADR for a medium concern; the second duplicates an already-decided attachment rule and creates two sources of truth.
- **Open-ended `source` string or a seventh node-identity source.** Rejected: an open set breaks the fixed, exhaustive panel tabs, and ADR-005 failures have no standalone pipeline stage — they occur inside resolution or construction.
- **Rendering as a general diagnostic producer or a display-only surface.** Rejected: as a general producer it double-counts upstream diagnostics; as display-only it has nowhere to record element-less layout failures.
- **Optional severity, or severity driving pipeline flow.** Rejected: optional severity leaves the panel unfilterable; coupling severity to flow conflates a human-facing grade with a control-flow halt decision.
- **Mandatory code `file:line` or mandatory graph attachment.** Rejected: a required code location kills render-failure and git-failure diagnostics; a required graph attachment kills truly global diagnostics with no element.
- **IntelliJ-native `ProblemsView` integration.** Rejected: `ProblemsView` navigates to PSI/files and cannot select custom graph nodes, losing the primary graph-node navigation target.

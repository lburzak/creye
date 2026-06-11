# Milestone: change-detection

Compare one configured git branch against the current working directory and locate changed Kotlin declarations, per ADR-003 (git change comparison), ADR-004 (changed symbol detection), ADR-005 (node identity), and ADR-010 (diagnostics).

## Definition of Ready

- ADR-003, ADR-004, ADR-005, ADR-010 MUST be accepted (they are).
- Milestone `plugin-foundation` MUST be done (it is): plugin shell, layered packages, headlessly invocable orchestration service.

## Definition of Done

- The git comparison stage MUST normalize the merge base of `HEAD` and a given branch against the working directory (staged, unstaged, and untracked changes included) into changed-file records per ADR-003: path, previous path, file state, Kotlin classification, baseline/current content, two-sided hunk ranges.
- The changed symbol detection stage MUST map changed Kotlin file records to changed declarations per ADR-004: own-range rule, file node as root declaration, symmetric baseline/current PSI detection, change kinds, contextual classification, declaration → file → diagnostic fallback ladder.
- Detected declarations MUST carry ADR-005 structural-path identity.
- Comparison and detection failures MUST surface as ADR-010 diagnostics under the `git` and `changed symbol detection` sources respectively.
- Domain types MUST remain free of IntelliJ, Kotlin-PSI, and git4idea imports (ADR-001 layering).
- `GraphAnalysisService` MUST expose change detection headlessly, taking the branch reference as input (full ADR-011 UI deferred to `graph-rendering`).
- `./gradlew test` and `./gradlew buildPlugin` MUST pass.

## Tasks

- `change-domain-contracts` — domain value types (`domain/change`, `domain/identity`, `domain/diagnostics`) per [contracts.md](contracts.md), plus Gradle/plugin.xml additions (Git4Idea dependency, platform test framework).
- `git-working-tree-comparison` — ADR-003 stage: git4idea command execution, merge base, name-status with rename detection, untracked-file union, pure `-U0` diff parser, content loading, git diagnostics; parser unit tests.
- `changed-symbol-detection` — ADR-004 stage: own-range detection over baseline and current PSI built from raw content, change kinds, contextual classification, fallback ladder, ADR-005 identity minting; fixture tests.
- `analysis-pipeline-wiring` — compose stages in `GraphAnalysisService.detectChanges(branch)`: save-documents pre-step, read-action discipline, diagnostic aggregation, cancellation pass-through; headless non-git-project test.

## Challenge

Idempotent verification of achievement:

- [ ] `./gradlew test` passes, run includes `UnifiedDiffParserTest`, `OwnRangesTest`, `ChangedSymbolDetectorTest`.
- [ ] Detector tests cover: added file, deleted file, modified function body, signature edit, import-only edit (file-level change, not diagnostic), contextual containing class, clean rename, dirty rename, deleted declarations enumerated individually, malformed-source file-level fallback.
- [ ] `./gradlew buildPlugin` succeeds.
- [ ] `grep -rE "import (com\.intellij|org\.jetbrains\.kotlin|git4idea)" src/main/kotlin/pl/lukaszburzak/creye/domain/` returns nothing.
- [ ] Headless test passes: `detectChanges` on a non-git project yields a `git`-source error diagnostic and an empty changed-file set.
- [ ] Manual (deferred until a rendering surface exists): `./gradlew runIde` against a sample Kotlin git repo with staged, unstaged, untracked, deleted, and renamed `.kt` files; `detectChanges("<branch>")` output lists expected declarations and kinds.

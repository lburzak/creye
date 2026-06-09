# PDR-003 Bug Tracking

## Problem & Context
- Fixing bugs immediately during implementation
	- Increases mental load due to context switching
	- Leads to wasting time for issues that become obsolete as implementation progresses
- Same bugs often reappear later, either exactly in the same form, or as similar bugs. It is beneficial to maintain an archive of historical bugs.
- A workflow, that allows for incrementally building knowledge on the issue ensures steady progress to resolution
## Decision
- Whenever a regression or unintended behavior is encountered or reported, user MUST be prompted to file a bug report
- Initial report MUST be quick and simple
- Initial report prompt MUST be as minimal and undisruptive as possible
- After fixing, report remains accessible within `bugs/fixed` directory for future reference
### Directory structure
- `bugs`
	- `{{bug-slug}}.toml`
		- `expected`, `actual`, `environment`
			- MUST be present
		- `reproduction-steps`
			- MUST be present when known at report filing
			- MUST NOT be present if uncertain
		- `encountered-in-commit`
			- MUST NOT be present when `introduced-in-commit` is present
		- `introduced-in-commit`
			- MUST be commit hash
			- MUST be determined on-demand
			- MAY be resolved by `git bisect`
		- `fixed-in-commit`
			- MUST be present only when fixed
		- `stacktrace`
			- MUST be multiline string
		- `[[hypothesis]]`
			- `clues`
				- MUST contain a list of information hinting hypothesis validity
			- `validation-steps`
				- MUST prove that hypothesis is accurate
			- `probability`
				- MUST be integer within 1-5 range
			- `fix`
				- MAY be deferred until hypothesis is confirmed
## Rationale
- Deferring bug fixes into explicit reports preserves implementation flow while still ensuring regressions and unintended behavior are not lost.
- A lightweight initial report keeps the cost of capturing a bug lower than the cost of remembering, rediscovering, or re-explaining it later.
- Keeping fixed bug reports creates a searchable archive of past failures, making recurring or similar issues easier to recognize and resolve.
- Separating `encountered-in-commit`, `introduced-in-commit`, and `fixed-in-commit` supports incremental investigation without requiring full root-cause knowledge at report time.
- Recording hypotheses, clues, validation steps, probability, and fixes turns debugging into a durable reasoning process instead of an ephemeral investigation.
- Requiring reproduction details only when known avoids forcing uncertain information into the report while still encouraging precise evidence when available.

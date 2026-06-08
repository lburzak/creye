# PDR-001 Task Management

## Decision
### Workflow
#### Workload decomposition
- Campaign MUST declare a non-empty set of Milestones
- Campaign MAY declare 1 Milestone if its complexity justifies it
- A set of Milestones MUST exhaustively capture high-level campaign execution plan
- Milestone declaration MUST NOT redeclare or be redundant to Campaign, as they exist on a different planes
	- Campaign corresponds to goals, requirements and constraints
	- Milestone corresponds to execution progress, implementation path and plan
- Milestone MUST indicate substantial progress within Campaign
- Achieving Milestone SHOULD deliver some intermediate value on it's own, allowing for assessing the outcome and potentially calibrating campaign or its milestones
- Milestones SHOULD be independent from each other, and provide value in isolation
- Milestone MUST define a checklist indicating it's achievement - a Challenge
	- MUST define instructions, that allow idempotent verification of achievement achievement or progress
	- SHOULD be automatic or semi-automatic, if it's feasible and sensible
	- Verification MAY be manual
- Multiple Milestones sets SHOULD be considered, allowing for taking optimal route
- Milestone MAY depend on other Milestone as prerequisite
- Task MUST NOT depend on other tasks (orchestration in milestones)
- Task MUST contribute to a Milestone
#### Deferred materialization
- Task MUST remain ephemeral until progress started, remaining declared only within Milestone tasks list
- Milestone MUST remain ephemeral until progress started, remaining declared only within Campaign milestones list
#### Dematerialization
- Tasks and milestones files MUST be removed as soon as they are completed or achieved
- Before task or milestone is dematerialized, its produced artifacts MUST be represented in their corresponding source of truth
##### Resources promotion
- Resources MUST be integrated into upper level workspace before dematerialization
	- Integration MUST NOT be limited to moving files
	- Integration MUST include normalization, restructuring, rephrasing etc. to maintain low redundancy, high clarity and relevance
#### Rerouting
- Priority on reusing in-progress tasks/milestones
### Directory structure - Markdown
- `campaigns`
	- `{{campaign-slug}}
		- `CAMPAIGN.md
		- `milestones`
			- `{{milestone-slug}}`
				- `MILESTONE.md`
				- `tasks`
					- `{{task-slug}}`
						- `TASK.md`
#### TASK.md
- `## Definition of Ready`, `## Definition of Done`
	- MUST list set of imperatives (RFC 2119) that determine task completion
- `## Checklist`
	- MUST be plaint points checkbox list
	- MUST be composed on-demand
#### MILESTONE.toml
- `## Definition of Ready`, `## Definition of Done`
	- MUST list set of imperatives (RFC 2119) that determine task completion
- `## Tasks`
	- MUST map task slug to its role within milestone achievement
#### Resources
- MUST be located within the workspace of task/milestone/campaign it belongs to
- Name MUST be based on the role it plays within the task/milestone/campaign completion
#### Drafts
- Name MUST be suffixed with .draft
- Name MUST be prefixed with original file name
- Name MUST be dash `-` separated

## Rationale
- Campaigns ensure that all work is done within grand context, with certain concise strategy in mind, while remaining focused on achieving the goal. Prevents unfruitful wins and empty iterations
- Milestones enforce high-level divide-and-conquer and ensures that workflow is iterative, allowing for adjusting the implementation route within quick feedback loop. Serves as task orchestration and synchronization ground
- Tasks enforce low-level divide-and-conquer, enabling progress tracking, reducing mental effort by scoping workload into specific areas and responsibilities. Allows for focusing agentic context with precise task definitions and relevant resources
- Deferred materialization allows for rerouting execution while retaining as much progress as possible
- Dematerialization ensures continuous integration of workload, preventing distributed knowledge sources introducing conflicting, redundant or incomplete informations
- Drafts allow for deliberation over impact and consequences of changes. When committed, the difference between previous decisions and draft, allow for crafting adjustments to the execution route
- Milestones servers as a rolling backlog, that is very flexible
- Mutable progress tracking is fragile to execution route changes and regressions. Immutable, idempotent challenges of milestones are superior in that matter

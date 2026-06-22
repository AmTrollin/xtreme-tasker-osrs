# Xtreme Tasker Release Notes

## 06.21.2026 - v2.0


### Progress and Sync

- Task completion flow was tightened so normal completion happens from the `Current` tab or through sync review.
- Added an `Undo` action for the most recent current-task completion.
- Sync no longer auto-completes tasks. `Sync CAs` and `Sync CLOGs+ADs` stage found completions for review before task state changes.
- Review panels now support selecting individual found tasks to update.
- Added mismatch review flows for tasks marked complete in Xtreme Tasker but not found by sync.
- Collection Log mismatch rows can be reviewed, synced again, ignored, or marked incomplete where allowed.
- Added guardrails for repeated and sequential sync mismatches so earlier sequence steps cannot be marked incomplete while later completed steps remain selected.
- Improved repeated-instance syncing and review panel formatting.

### Collection Log Tasks

- Collection Log tasks now emphasize total obtained progress and current-task progress where applicable.
- Single-item Collection Log tasks render the item icon directly.
- Collection Log requirement sections were moved above prereqs in task details.
- Eligible Collection Log items now use clearer hover text and checked-off obtained states.
- Collection Log item cache can update from supported drop/chat messages and from opened Collection Log pages.
- Added review popups for Collection Log sync review rows.
- Expanded Ancient page displays with individual icons and numbered badges.
- Added extra sync messaging for Ancient page style requirements.
- Updated Medallion of the Deep handling.
- Added batching/caching optimizations so opening Collection Log pages causes less lag.

### Task Details and Prereqs

- Added visual prereq icons and optimized prereq rendering.
- Completed prereqs are crossed out in compact view.
- Cleaned up prereq text and reduced redundant metal-tier prereqs.
- Task detail headers were made more consistent.
- Completed task titles now use a green completed treatment.
- The current-task complete button now gets a green border/glow when completion criteria are met.
- Added initial task tip support.
- Fixed a transparent-image null exception.

### Task List and Review UI

- Fixed compact-view and full-view scrolling.
- Cleaned up Current tab Collection Log layout.
- Improved sync review panel layout and task-instance completion display.
- Cleaned up keyboard navigation popup copy and hover tips.
- Formatted the `See New Tasks` button.
- Shortened roll-source names in config.
- Added skip-task support, including helper text for the skipped-task counter.

### Task Data Updates

- Refactored and cleaned up sequential task families, including metal boots, MTA wand, Tempor Tantrum, Jubbly Jive, and Gwyneth Glide.
- Fixed ordering/sequencing for Tempor Tantrum, Jubbly, Gwyneth, MTA wand, and metal boot style tasks.
- Added Shellbane gryphon tasks.
- Cleaned up MTA wand task names.
- Updated `Get a sea treasure`.
- Improved level-99 skillcape style tasks.
- Fixed Giants' Foundry task sync/counting behavior.

### Stability, Performance, and Testing

- Added caches and rendering optimizations for task lists, prereqs, Collection Log previews, item images, and sync review displays.
- Fixed the issue where Xtreme Tasker and Collection Log Master being open at the same time could interfere with each other.
- Added regression tests for persistence, sync, Collection Log mismatches, widget monitoring, prerequisite tracking, and task data.
- Added `.gitignore` coverage for JVM crash log files.

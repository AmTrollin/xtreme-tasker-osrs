# Xtreme Tasker Release Notes

## v2.1.1 — 07.02.2026

### Hotfix

- Fixed stale/incorrect Collection Log obtained states causing active CLOG tasks to appear complete.
- Current task timer now continues until the user explicitly presses Complete, except for tasks already complete before roll.
- Tightened Collection Log chat detection and added sync reconciliation for items later reported as unobtained.

## v2.1 — 06.30.2026

### Sync & Task Completion
- Collection Log syncing now occurs automatically when opening the in-game Collection Log
- Added timer support for tasks already completed when rolled

### UI & Quality of Life
- Added a new Compact View
- Various UI and QoL Improvements 

### Bug Fixes
- Fixed sequential Collection Log task progression


## v2.0 — 06.25.2026

### Sync & Task Completion
- Combined Combat Achievement, Collection Log, and Achievement Diary syncing into a single action
- Syncs now require review before applying task completion changes
- Added **Undo** for the most recently completed current task
- Improved syncing for sequential and repeatable tasks
- Added **Mark Incomplete** (✕) in task descriptions

### UI & Performance
- New horizontal layout with improved space utilization
- Added prerequisite, Collection Log, and Achievement Diary icons
- Improved overall responsiveness and reduced lag

### Tasks
- Added Shellbane Gryphon tasks
- Manual task completion is now limited to task rolls and syncs
- Achievement Diaries now have their own task category
- Moved **Get 1 unique from the Hueycoatl** from **Hard** to **Medium**

### Bug Fixes
- Fixed a potential RuneLite crash when used alongside Collection Log Master
- Fixed syncing and tracking for Tea Flask, Karamja Diary, and Giants' Foundry tasks
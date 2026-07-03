<div id="user-content-toc">
  <ul style="list-style: none;">
    <summary>
      <h1>Xtreme Tasker Release Notes</h1>
    </summary>
  </ul>
</div>


## v2.1.1 — 07.03.2026

### Reduce Codebase Size
- Various refactors to reduce codebase size and improve code organization (no user-facing impact)

### Bug Fixes
- Tightened chat message parsing for detecting Collection Log drops in-game
- Fixed task timer so it only stops when the Mark complete button is explicitly pressed
- Removed the current task from sync review display


## v2.1.0 — 06.30.2026

### Sync & Task Completion
- Collection Log syncing now occurs automatically when opening the in-game Collection Log
- Added timer support for tasks already completed when rolled

### UI & Quality of Life
- Added a new Compact View
- Various UI and QoL Improvements 

### Bug Fixes
- Fixed sequential Collection Log task progression


## v2.0.0 — 06.25.2026

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
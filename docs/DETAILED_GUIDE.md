# Xtreme Tasker Detailed Guide

This guide goes deeper than the main README and covers task updates, manual sync, repeated tasks, save backups, and recovery behavior.

For the shorter user guide, see the [main README](../README.md).

## Overview

Xtreme Tasker is a RuneLite plugin for playing Old School RuneScape with a progressive random task list built from Combat Achievements, Collection Log goals, and Achievement Diaries.

The plugin adds an in-game overlay for rolling tasks, tracking completion, viewing tier progress, filtering the full task list, and syncing progress RuneLite can already see.

## Overlay Tabs

The overlay has three main tabs:

- `Current` shows your active task, tier progress, task details, prereqs, wiki access, and roll/complete actions.
- `Tasks` shows the full task list for browsing, filtering, sorting, and manually editing completion.
- `Help` contains rules, FAQ links, README access, and manual sync actions.

Both the `Current` and `Tasks` tabs include a `[Keyboard hints]` control at the bottom of the panel. Click it to show the available keyboard shortcuts for that tab.

## Task List

The Tasks tab includes:

- Search by task text.
- Source filters for all tasks, or any combination of Combat Achievement, Collection Log, and Achievement Diary tasks.
- Status filters for all, incomplete, and complete tasks.
- Tier scope filtering for the current tier or all tiers.
- Sorting by status, tier, completion date, and time spent where applicable.
- Empty-state messages when your filters return no rows.

Rows use compact status indicators:

- Empty circle: incomplete.
- Amber partial indicator: some repeated task instances are complete.
- Green completed indicator: complete.
- `NEW`: task was newly added by a task data update.

Source filters are multi-select. Selecting all three source filters normalizes back to `All`.

## Repeated Tasks

Some tasks can appear multiple times because they can be rolled more than once. Each roll still counts as its own task for tier progress and completion percentage.

By default, repeated tasks are condensed into one row with a `#/#` progress indicator. In the task details popup, you can:

- Mark one additional instance complete.
- Remove completion from a specific completed instance.
- View each completed instance's completion date and time spent when available.
- Mark all instances complete.
- Mark all instances incomplete after confirming the reset.

You can switch between condensed and separate repeated-task rows from the Tasks tab with `Condense repeated tasks` / `Separate repeated tasks`. The same preference is also available in plugin config.

When sorting by completion date or time spent, repeated task instances are shown separately so each instance can appear in the correct sorted position.

## Task Sources

Tasks come from the bundled `tasks.json` file and are grouped by tier.

Combat Achievement tasks use OSRS Combat Achievement data. Collection Log tasks use Collection Log entries. Achievement Diary tasks use in-game achievement diary completion state. A few Collection Log-style progression tasks also use related account checks, such as level-99 skill requirements, where a task definition needs them.

Grandmaster Combat Achievement tasks are currently grouped into the Master tier for Xtreme Tasker progression.

## Task Data Updates

Xtreme Tasker's bundled task list is loaded automatically on startup and checked again when account state loads after login. There is no manual task list reload button.

When a task pack update adds tasks, Xtreme Tasker shows an in-game chat message and tracks those task IDs for the current session. Use the Tasks tab's `See New Tasks` control to toggle between newly added tasks and the normal task list.

Task list updates do not affect account progress.

## Manual Progress Sync

Xtreme Tasker does not play the game for you or complete actions automatically. Sync buttons only mark plugin tasks complete when RuneLite can already see the relevant account state.

Completions marked by you are shown as marked in task details; completions added by `Sync CAs` or `Sync CLOGs+ADs` are shown as synced.

### Combat Achievements

`Sync CAs` checks completed Combat Achievements and marks matching Xtreme Tasker tasks complete.

### Collection Logs and Achievement Diaries

`Sync CLOGs+ADs` checks Collection Log items that RuneLite has cached and Achievement Diaries RuneLite can already see. After earning new Collection Log items, open your Collection Log in-game so RuneLite can refresh them before syncing. Achievement Diaries do not need the Collection Log cache.

Achievement Diary tasks show their diary region and tier in task details and sync from in-game diary completion state.

Some Collection Log tasks also use verification data such as level-99 skill counts.

Repeated counted Collection Log tasks show total obtained progress in task details. When the displayed task is also your current task and needs more than one item, the same line includes current task progress, such as `total obtained: 14 | current task: 2/5`.

For repeated counted Collection Log tasks, obtained items already applied to earlier completions are dimmed and crossed out. Obtained items not yet applied are shown in gold.

### Review Notes

If a manual sync finds tasks that are marked complete in Xtreme Tasker but not detected in game after sync, the `Help` > `Sync` section shows a review note with a timestamp. This note persists across sessions until you review it, ignore it, or a future sync replaces it.

Use `Review` to open the mismatch list. From there you can select individual tasks, select all tasks, apply the changes, and confirm before anything is marked incomplete. Combat Achievement review rows can be clicked for a compact requirement popup. Repeated Collection Log rows can show the in-game count that sync saw.

Use `Ignore` when you do not want to change anything. Ignoring only clears the current note; if a future sync still finds mismatches, the note will appear again.

For Collection Log reviews, item mismatch checks depend on RuneLite's cached Collection Log data. Cached items carry across sessions, but newly earned log items may not appear until you open the Collection Log in game and sync again. Achievement Diary reviews use diary completion state.

## Progress Saves and Backups

Progress is saved locally through RuneLite's config system and stored per character. The save key combines RuneLite's account hash with the character's display name, so characters that share a launcher profile or account hash do not overwrite each other.

Saved progress includes:

- Scoped account key, account hash, and last known display name.
- Human readable save time and epoch timestamp.
- Manually completed task IDs.
- Synced completed task IDs.
- Current task ID.
- Completion timestamps.
- Time spent on tasks and repeated task instances.
- Last seen task pack data.
- Retired task IDs that no longer exist in the bundled task pack.

The plugin keeps three rolling local backups before overwriting recovery-relevant save data. Backup keys are stored per scoped account key:

- `xtremetasker.state_<accountHash>_<characterNameKey>_backup_1`
- `xtremetasker.state_<accountHash>_<characterNameKey>_backup_2`
- `xtremetasker.state_<accountHash>_<characterNameKey>_backup_3`

If the primary save is missing or corrupt, Xtreme Tasker tries to restore from the newest valid backup and shows an in-game chat message when recovery happens.

Xtreme Tasker also refuses suspicious saves that look like they would wipe or roll back a large number of completed tasks, while still allowing intentional manual incomplete actions.

## Account Switching

Progress is keyed by RuneLite's account hash plus the logged-in character name. Xtreme Tasker waits for that scoped account key to stabilize before loading state, then refuses saves when the loaded account key and active account key do not match.

This protects account switching, world hopping, and relaunches from accidentally mixing in-memory progress between accounts.

## Recovering Progress Manually

RuneLite profile config files usually live under:

```text
~/.runelite/profiles2/
```

Search for Xtreme Tasker saves with:

```bash
grep -R "xtremetasker.state_" ~/.runelite/profiles2
```

Before editing these files, close RuneLite and make a copy of the profile `.properties` file.

Useful JSON fields for recovery:

- `accountDisplayName`
- `accountKey`
- `savedAtLocalTime`
- `currentTaskId`
- `manualCompletedTaskIds`
- `syncedCompletedTaskIds`
- `retiredTaskIds`

## Configuration

Plugin config includes:

- `Show task HUD`: show or hide the small current-task HUD.
- `Roll source`: roll all tasks, only Combat Achievement tasks, or only Collection Log and Achievement Diary tasks.
- `Condense repeated tasks`: show repeated task rolls as one grouped row with per-instance completion controls.

## Rules

Xtreme Tasker follows the official Tasker ruleset and adds Xtreme Tasker-specific guidance for the expanded task list.

The in-game Help tab links to:

- TaskerFAQ for official Tasker rules.
- The main README for quick Xtreme Tasker notes.

### Boss Combat Training Allowance

For any task requiring that you kill a boss with a suggested skills section on the boss strategy page of the OSRS Wiki, you are allowed to train your combat skills to those suggested levels.

Training must be done through Slayer, with any Slayer master or masters of your choosing.

## Privacy

Xtreme Tasker stores progress locally in RuneLite config. It does not send account progress, task progress, or gameplay data to an external service.

Sync features use account state already visible to RuneLite, such as Combat Achievement data and cached Collection Log entries. They do not upload that data anywhere.

The plugin can open external links when you click wiki, FAQ, or README buttons.

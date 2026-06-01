# Xtreme Tasker Detailed Guide

This guide covers the parts that need more explanation: task updates, manual sync, repeated tasks, save backups, and recovery behavior.

For the shorter user guide, see the [main README](../README.md).

## Overview

Xtreme Tasker is a RuneLite plugin for playing Old School RuneScape with a progressive random task list built from Combat Achievements and Collection Log goals.

The plugin adds an in-game overlay for rolling tasks, tracking completion, viewing tier progress, filtering the full task list, and syncing progress RuneLite can already see.

## Overlay Tabs

The overlay has three main tabs:

- `Current` shows your active task, tier progress, task details, prereqs, wiki access, and roll/complete actions.
- `Tasks` shows the full task list for browsing, filtering, sorting, and manually editing completion.
- `Help` contains rules, FAQ links, README access, and manual sync actions.

Both the `Current` and `Tasks` tabs include a `[Keyboard hints]` control at the bottom of the panel. Click it to show the available keyboard shortcuts for that tab.

## Task List

The Tasks tab supports:

- Search by task text.
- Source filters for all tasks, Combat Achievements, and Collection Log tasks.
- Status filters for all, incomplete, and complete tasks.
- Tier scope filtering for the current tier or all tiers.
- Sorting by source, tier, completion date, and time spent where applicable.
- Empty-state messages that show active filters when no rows match.

Rows use compact status indicators:

- Empty circle: incomplete.
- Amber partial indicator: some repeated task instances are complete.
- Green completed indicator: complete.
- `NEW`: task was newly added by a task data update.

## Repeated Tasks

Some tasks can appear multiple times in the task list because they can be rolled more than once. These still count as individual tasks for tier progress and completion percentage.

By default, repeated tasks are condensed into one row with a `#/#` progress indicator. In the task details popup, you can:

- Mark one additional instance complete.
- Remove completion from a specific completed instance.
- View each completed instance's completion date and time spent when available.
- Mark all instances complete.
- Mark all instances incomplete after confirming the reset.

You can switch between condensed and separate repeated-task rows from the Tasks tab using the `Condense repeated tasks` / `Separate repeated tasks` action. The same preference is also available in plugin config.

When sorting by completion date or time spent, repeated task instances are shown separately so each instance can appear in the correct sorted position.

## Task Sources

Tasks come from the bundled `tasks.json` file and are grouped by tier.

Combat Achievement tasks use OSRS Combat Achievement data. Collection Log tasks use Collection Log entries and a few related account checks, such as achievement diary or skill requirements.

Grandmaster Combat Achievement tasks are currently grouped into the Master tier for Xtreme Tasker progression.

## Task Data Updates

Xtreme Tasker's bundled task list is loaded automatically on startup and checked again when account state loads after login. There is no manual task-list reload button.

When a task pack update adds tasks, Xtreme Tasker shows an in-game chat message and tracks those task IDs for the current session. Use `See New Tasks` in the Tasks tab to switch between newly added tasks and the normal task list.

Task-list updates do not affect account progress.

## Manual Progress Sync

Xtreme Tasker does not play the game for you. Sync buttons only mark plugin tasks complete when RuneLite can already see the relevant account state.

Completions marked by you are shown as marked in task details; completions added by `Sync CAs` or `Sync CLOGs` are shown as synced.

### Combat Achievements

`Sync CAs` checks completed Combat Achievements and marks matching Xtreme Tasker tasks complete.

### Collection Log

`Sync CLOGs` checks Collection Log items that RuneLite has cached. After earning new Collection Log items, open your Collection Log in-game so RuneLite can refresh them before syncing.

Some Collection Log tasks use extra verification data, such as achievement diary completion or level-99 skill counts.

### Review Notes

If a manual sync finds tasks that are marked complete in Xtreme Tasker but not detected in game after sync, the `Help` > `Sync` section shows a review note with a timestamp. This note persists across sessions until you review it, ignore it, or a future sync replaces it.

Use `Review` to open the mismatch list. From there you can select individual tasks, select all tasks, apply the changes, and confirm before anything is marked incomplete. Combat Achievement task names can be clicked to show a compact requirement popup. For repeated Collection Log tasks, hover text shows the in-game count that sync saw.

Use `Ignore` when you do not want to change anything. Ignoring only clears the current note; if a future sync still finds mismatches, the note will appear again.

For Collection Log reviews, the mismatch check depends on RuneLite's cached Collection Log data. Cached items carry across sessions, but newly earned log items may not appear until you open the Collection Log in game and sync again.

### Achievement Diaries

Some Collection Log-style tasks are verified through achievement diary state. Karamja diary completion uses explicit cumulative thresholds because Karamja is the legacy diary and its RuneLite varbits behave differently than the newer diaries.

## Progress Saves and Backups

Progress is saved locally through RuneLite's config system and is stored per account hash.

Saved progress includes:

- Account hash and last known display name.
- Human-readable save time and epoch timestamp.
- Manually completed task IDs.
- Synced completed task IDs.
- Current task ID.
- Completion timestamps.
- Time spent on tasks and repeated task instances.
- Last seen task pack data.
- Retired task IDs that no longer exist in the bundled task pack.

The plugin keeps three rolling local backups of progress before overwriting recovery-relevant save data. Backup keys are stored per account:

- `xtremetasker.state_<accountHash>_backup_1`
- `xtremetasker.state_<accountHash>_backup_2`
- `xtremetasker.state_<accountHash>_backup_3`

If the primary save is missing or corrupt, Xtreme Tasker tries to restore from the newest valid backup and shows an in-game chat message when recovery happens.

Important progress changes are flushed to RuneLite's profile file immediately so data is safer when closing from an IDE or after an abrupt shutdown. Time-spent-only updates may still be buffered.

Xtreme Tasker also refuses suspicious saves that look like they would wipe or roll back a large number of completed tasks, while still allowing intentional manual incomplete actions.

## Account Switching

Progress is keyed by RuneLite's account hash for the logged-in account. Xtreme Tasker waits for the account hash to stabilize before loading state for that account, then refuses saves when the loaded account key and active account key do not match.

This helps prevent account switching, world hopping, and relaunches from mixing in-memory progress between accounts.

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
- `Roll source`: roll all tasks, only Combat Achievement tasks, or only Collection Log tasks.
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

The plugin only opens external links when you click wiki, FAQ, or README buttons.

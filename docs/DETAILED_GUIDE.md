# Xtreme Tasker Detailed Guide

This guide goes deeper than the main README and covers task updates, progress sync, repeated tasks, save backups, and recovery behavior.

For the shorter user guide, see the [main README](../README.md). For recent changes, see the [Release Notes](RELEASE_NOTES.md).

## Table of Contents

- [Overview](#overview)
- [Overlay Tabs](#overlay-tabs)
- [Browsing Tasks](#browsing-tasks)
- [Repeated Tasks](#repeated-tasks)
- [Task Types](#task-types)
- [Task Pack Updates](#task-pack-updates)
- [Progress Sync](#progress-sync)
- [Saves and Backups](#saves-and-backups)
- [Account Switching](#account-switching)
- [Manual Recovery](#manual-recovery)
- [Configuration](#configuration)
- [Rules](#rules)
- [Privacy](#privacy)

## Overview

Xtreme Tasker is a RuneLite plugin for playing Old School RuneScape with a progressive random task list built from Combat Achievements, Collection Log goals, and Achievement Diaries.

The plugin adds an in game overlay for rolling tasks, tracking completion, viewing tier progress, filtering the full task list, and syncing progress RuneLite can already see.

Want to see the plugin in action? Check out [@amtrollin](https://www.youtube.com/@AmTrollin/playlists)'s YouTube series, [Xtreme Tasker](https://www.youtube.com/playlist?list=PLyKVvPO_c8ffVO0H73Kxnnwz5J6DZir4H).

## Overlay Tabs

The overlay has three main tabs:

- `Current` shows your active task, tier progress, task details, prereqs, wiki access, and context actions such as rolling, marking the current task complete, skipping when enabled, and undoing a recent completion.
- `Tasks` shows the full task list for browsing, filtering, sorting, and reviewing task details.
- `Help` contains rules, FAQ links, README access, and progress sync actions.

Both the `Current` and `Tasks` tabs include a `[Keyboard hints]` control at the bottom of the panel. Click it to show the available keyboard shortcuts for that tab.

The overlay can switch between full view and compact view from the corner toggle. Compact view keeps the current task, timer, wiki link, and scrollable task details in a smaller panel.

## Browsing Tasks

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

By default, repeated tasks are condensed into one row with a `#/#` progress indicator. The task details popup shows completed instance count and completion history when available.

You can switch between condensed and separate repeated-task rows from the Tasks tab with `Condense repeated tasks` / `Separate repeated tasks`. The same preference is also available in plugin config.

When sorting by completion date or time spent, repeated task instances are shown separately so each instance can appear in the correct sorted position.

## Task Types

Tasks come from the bundled `tasks.json` file and are grouped by tier.

Combat Achievement tasks use OSRS Combat Achievement data. Collection Log tasks use Collection Log entries. Achievement Diary tasks use in game achievement diary completion state. A few Collection Log-style progression tasks also use related account checks, such as level-99 skill requirements, where a task definition needs them.

Grandmaster Combat Achievement tasks are currently grouped into the Master tier for Xtreme Tasker progression.

## Task Pack Updates

Xtreme Tasker's bundled task list is loaded automatically on startup and checked again when account state loads after login. There is no task list reload button.

When a task pack update adds tasks, Xtreme Tasker shows an in game chat message and tracks those task IDs for the current session. Use the Tasks tab's `See New Tasks` control to toggle between newly added tasks and the normal task list.

Task list updates do not affect account progress.

## Progress Sync

Xtreme Tasker does not play the game for you or complete actions automatically. Sync buttons only stage plugin task updates when RuneLite can already see the relevant account state. You review and apply those staged updates before tasks are marked complete.

Completions marked from the `Current` tab are shown as marked in task details. Completions applied from sync review are shown as synced.

### Combat Achievements

`Sync CAs` checks completed Combat Achievements and stages matching Xtreme Tasker tasks for review.

### Collection Logs and Achievement Diaries

`Sync CLOGs+ADs` checks Collection Log items Xtreme Tasker/RuneLite has cached, Achievement Diaries RuneLite can already see, and supported skill-count requirements such as level-99 skillcape-style tasks. Achievement Diaries do not need the Collection Log cache.

> [!IMPORTANT]
> Collection Log syncing can only use items RuneLite has seen.
> After obtaining new Collection Log items, open the relevant Collection Log page in game before running `Sync CLOGs+ADs`.

The plugin also listens for supported Collection Log chat messages, including new collection-log item messages and relevant received-item messages while the Collection Log is open. Collection Log scans are batched so opening CLOG pages does not repeatedly rebuild sync state for every item slot.

Achievement Diary tasks show their diary region, task master, and prereqs. They sync from in game diary completion state rather than Collection Log item drops.

Some Collection Log-style tasks also use verification data such as level-99 skill counts.

Repeated counted Collection Log tasks show total obtained progress in task details. When the displayed task is also your current task and needs more than one item, the same line includes current task progress, such as `total obtained: 14 | current task: 2/5`.

For counted Collection Log tasks, task details show progress against the required count and check off obtained eligible items.

### New Completion Reviews

If sync finds new completions, the `Help` > `Sync` section shows an update review. Use `Update tasks` to choose which staged completions to apply, or ignore the review if you do not want to change anything.

### Mismatch Reviews

If sync finds tasks that are marked complete in Xtreme Tasker but not detected in game after sync, the `Help` > `Sync` section shows a mismatch review note with a timestamp. This note persists across sessions until you review it, ignore it, or a future sync replaces it.

Completed Combat Achievement and Achievement Diary tasks are also checked quietly when you open their task details. If game state does not match the plugin state, task details can show mismatch actions without requiring a manual sync first. Collection Log mismatch checks still depend on cached Collection Log data.

Use `Review` to open the mismatch list. From there you can select individual tasks, select all tasks, apply the changes, and confirm before anything is marked incomplete. Combat Achievement review rows can be clicked for a compact requirement popup. Repeated Collection Log rows can show the in game count that sync saw. Sequential Collection Log tasks are guarded so an earlier step cannot be marked incomplete while a later completed step remains selected.

For Collection Log reviews, item mismatch checks depend on RuneLite's cached Collection Log data. Achievement Diary reviews use diary completion state.

### Ignoring Reviews

Use `Ignore` when you do not want to change anything. Ignoring only clears the current note; if a future sync still finds mismatches, the note will appear again.

## Saves and Backups

Progress is saved locally per character and backed up automatically.

### What Gets Saved

- Completed tasks.
- Current task.
- Completion history.
- Time spent on tasks.
- Collection Log sync data.
- Sync review state.
- Skipped task count and undo state.

### Advanced Save Details

Progress is stored in JSON state files under `~/.runelite/xtreme-tasker-states/` and mirrored into RuneLite config for compatibility. The save key combines RuneLite's account hash with the character's display name, so characters that share a launcher profile or account hash do not overwrite each other.

Saved state includes:

- Scoped account key, account hash, and last known display name.
- Human readable save time and epoch timestamp.
- Manually completed task IDs.
- Synced completed task IDs.
- Current task ID.
- Undoable completed task ID and skipped task count.
- Current task Collection Log baseline when needed for counted CLOG progress.
- Completion timestamps.
- Time spent on tasks and repeated task instances.
- Last sync results, staged sync completion candidates, and sync mismatch review state.
- Cached Collection Log item IDs and acquisition order.
- Last seen task pack data.
- Retired task IDs that no longer exist in the bundled task pack.

The plugin keeps three rolling local backups before overwriting recovery-relevant save data. Backup files are stored beside the primary JSON state file:

- `<accountKey>.json.backup_1`
- `<accountKey>.json.backup_2`
- `<accountKey>.json.backup_3`

Mirrored backup keys are also written to RuneLite config for compatibility:

- `xtremetasker.state_<accountHash>_<characterNameKey>_backup_1`
- `xtremetasker.state_<accountHash>_<characterNameKey>_backup_2`
- `xtremetasker.state_<accountHash>_<characterNameKey>_backup_3`

If the primary save is missing or corrupt, Xtreme Tasker tries to restore from the newest valid backup and shows an in game chat message when recovery happens.

Xtreme Tasker also refuses suspicious saves that look like they would wipe or roll back a large number of completed tasks.

## Account Switching

Progress is keyed by RuneLite's account hash plus the logged-in character name. Xtreme Tasker waits for that scoped account key to stabilize before loading state, then refuses saves when the loaded account key and active account key do not match.

This protects account switching, world hopping, and relaunches from accidentally mixing in-memory progress between accounts.

## Manual Recovery

Most users will never need this section.

Only use these steps if your save file becomes corrupted or support/release notes specifically direct you here.

Primary Xtreme Tasker state files usually live under:

```text
~/.runelite/xtreme-tasker-states/
```

Mirrored RuneLite profile config files usually live under:

```text
~/.runelite/profiles2/
```

Search primary state files with:

```bash
ls ~/.runelite/xtreme-tasker-states
```

Search mirrored config saves with:

```bash
grep -R "xtremetasker.state_" ~/.runelite/profiles2
```

Before editing these files, close RuneLite and make a copy of the JSON state file or profile `.properties` file.

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
- `Enable task skipping`: show a Skip button on the Current tab. Skipping rerolls your current task and increments your skipped task count.

Compact/full overlay view is controlled from the overlay itself rather than plugin config.

## Rules

Xtreme Tasker follows the official Tasker ruleset and adds Xtreme Tasker-specific guidance for the expanded task list.

The in game Help tab links to:

- TaskerFAQ for official Tasker rules.
- The main README for quick Xtreme Tasker notes.

### Boss Combat Training Allowance

For any task requiring that you kill a boss with a suggested skills section on the boss strategy page of the OSRS Wiki, you are allowed to train your combat skills to those suggested levels.

Training must be done through Slayer, with any Slayer master or masters of your choosing.

## Privacy

Xtreme Tasker stores progress locally in JSON state files under your RuneLite folder and mirrors progress into RuneLite config for compatibility. It does not send account progress, task progress, or gameplay data to an external service.

Sync features use account state already visible to RuneLite, such as Combat Achievement data, cached Collection Log entries, and Achievement Diary state. They do not upload that data anywhere.

The plugin itself can open external links when you click wiki, FAQ, or README buttons. This documentation also links to the @amtrollin YouTube channel and Xtreme Tasker playlist.

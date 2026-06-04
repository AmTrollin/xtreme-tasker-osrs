#!/usr/bin/env python3
import json
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path


def decode_properties_value(value):
    return (
        value.replace("\\:", ":")
        .replace("\\!", "!")
        .replace("\\=", "=")
        .replace("\\#", "#")
        .replace("\\ ", " ")
    )


def local_time(epoch_millis):
    if not epoch_millis:
        return "none"
    return datetime.fromtimestamp(epoch_millis / 1000, timezone.utc).astimezone().isoformat()


def scan_runelite_saves():
    runelite_dir = Path.home() / ".runelite"
    rows = []

    for path in runelite_dir.rglob("*.properties"):
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except OSError:
            continue

        for line_no, line in enumerate(lines, 1):
            if not line.startswith("xtremetasker.state_") or "=" not in line:
                continue

            key, raw_value = line.split("=", 1)
            try:
                state = json.loads(decode_properties_value(raw_value))
            except Exception as exc:
                rows.append(
                    {
                        "account": "PARSE ERROR",
                        "accountKey": "unknown",
                        "suffix": key,
                        "error": str(exc),
                        "file": f"{path}:{line_no}",
                    }
                )
                continue

            manual = set(state.get("manualCompletedTaskIds") or [])
            synced = set(state.get("syncedCompletedTaskIds") or [])
            retired = set(state.get("retiredTaskIds") or [])
            completed = manual | synced | retired

            timestamps = {}
            timestamps.update(state.get("manualCompletionTimestamps") or {})
            timestamps.update(state.get("syncedCompletionTimestamps") or {})
            latest_completion = max(timestamps.values()) if timestamps else 0

            account_key = (
                state.get("accountKey")
                or key.removeprefix("xtremetasker.state_").split("_backup_", 1)[0]
            )
            suffix = key.split(account_key, 1)[-1] if account_key in key else key

            rows.append(
                {
                    "account": state.get("accountDisplayName") or "(unknown)",
                    "accountKey": account_key,
                    "suffix": suffix or "(primary)",
                    "saved": state.get("savedAtLocalTime") or "unknown",
                    "savedEpoch": state.get("savedAtEpochMillis") or 0,
                    "completed": len(completed),
                    "manual": len(manual),
                    "synced": len(synced),
                    "retired": len(retired),
                    "latestCompletion": latest_completion,
                    "current": state.get("currentTaskId") or "none",
                    "file": f"{path}:{line_no}",
                }
            )

    return rows


def main():
    rows = scan_runelite_saves()
    if not rows:
        print("No Xtreme Tasker saves found under ~/.runelite")
        return

    parse_errors = [row for row in rows if row.get("error")]
    rows = [row for row in rows if not row.get("error")]

    by_account = defaultdict(list)
    for row in rows:
        by_account[(row["account"], row["accountKey"])].append(row)

    for (account, account_key), account_rows in sorted(by_account.items()):
        print(f"\nACCOUNT {account} ({account_key})")
        for row in sorted(account_rows, key=lambda item: item["suffix"]):
            print(
                f"  {row['suffix']:10} completed={row['completed']} "
                f"manual={row['manual']} synced={row['synced']} retired={row['retired']} "
                f"saved={row['saved']} latestCompletion={local_time(row['latestCompletion'])}"
            )
            print(f"             current={row['current']}")
            print(f"             file={row['file']}")

        best_progress = max(
            account_rows,
            key=lambda item: (item["completed"], item["latestCompletion"], item["savedEpoch"]),
        )
        newest = max(account_rows, key=lambda item: item["savedEpoch"])
        print(
            f"  BEST_BY_PROGRESS: {best_progress['suffix']} "
            f"completed={best_progress['completed']} saved={best_progress['saved']}"
        )
        print(
            f"  NEWEST_SAVE:      {newest['suffix']} "
            f"completed={newest['completed']} saved={newest['saved']}"
        )

    if parse_errors:
        print("\nPARSE ERRORS")
        for row in parse_errors:
            print(f"  {row['suffix']} {row['file']}: {row['error']}")


if __name__ == "__main__":
    main()

---
name: android-studio-diagnostics-analysis
description: Analyzes Android Studio diagnostic logs and thread dumps to identify UI freezes, lock contention, and system configuration issues. Use this when a user provides a DiagnosticsReport or attaches a bug report with thread dumps to a Buganizer issue.
---

# Diagnostics Analysis Skill

This skill provides tools to analyze Android Studio diagnostic zip files (often attached to Buganizer issues as `DiagnosticsReport*.zip`). It helps identify the root cause of UI freezes by analyzing the Event Dispatch Thread (EDT) and identifying background threads holding critical IntelliJ Platform locks.

## Prerequisites

- Python 3.8+
- The `zipfile` module (built-in) is used to analyze zip reports directly.

## Available scripts

- **`scripts/analyze_diagnostics.py`** — Parses `SystemInfo.log` and `Thread Dumps/` to produce a Markdown report. Supports `--json` output.

## Workflow

1. **Obtain Diagnostic File:**
   If the diagnostic file (e.g., `DiagnosticsReport*.zip`) is not already in your workspace, ask the user to provide it or its local path. Do NOT attempt to use `bugged` or other automated tools to download it from Buganizer.

2. **Run Analysis:**
   Invoke the analysis script directly on the diagnostic zip file or an extracted folder:
   ```bash
   python3 scripts/analyze_diagnostics.py DiagnosticsReport-XXXX.zip
   ```

3. **Interpret Results:**
   Review the generated report.
   - **Correlate Timestamps:** Compare the timestamps of the captured freezes with the time the issue was reported by the user. Diagnostic reports often contain multiple dumps from unrelated events.
   - **Check Relevance:** Verify if the stack traces in the freeze match the component or action described in the bug report (e.g., if the user reported a hang in "Image Asset Studio", look for related classes in the EDT stack).
   - **Analyze Blockers:** If the EDT is blocked, check the "Potential Lock-Holding Background Threads" section to find which thread holds critical platform locks.

## Correlating Freezes with Issues

It is critical to distinguish between the primary issue and secondary, unrelated freezes.
- **Time Match:** A freeze that happened hours before the reported issue is likely irrelevant.
- **Context Match:** A freeze in `git4idea` is likely unrelated to a hang in the "Project Structure" dialog.
- **Duration:** Longer freezes (e.g., > 5s) are more likely to be perceived by the user as "hangs".

## References

- [IntelliJ Platform Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html) — Essential for understanding Read/Write locks and the EDT.
- [Oracle: Using Thread Dumps](https://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/geninfo/diagnos/using_threaddumps.html) — General guide on interpreting JVM thread dumps and identifying deadlocks.
- [Diagnostics Report Format](references/diagnostic_format.md) — Internal documentation on the structure of Android Studio diagnostic reports.
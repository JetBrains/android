# Android Studio Diagnostics Analysis

## Overview
Android Studio diagnostic reports (typically `DiagnosticsReport*.zip`) are essential for debugging IDE freezes, crashes, and performance issues. This document describes the structure of these reports and how to extract actionable information.

## Report Structure
When extracted, a typical diagnostics report contains:

- **Logs/**
  - `idea.log`: The main application log. Contains stack traces for exceptions, IDE startup info, and plugin loading events.
- **System Info/**
  - `SystemInfo.log`: Contains version information for Android Studio, OS, Java, Gradle, and Android Gradle Plugin (AGP).
- **Thread Dumps/**
  - This directory contains subdirectories for specific events, notably **UI freezes**.
  - Folder format: `threadDumps-freeze-<timestamp>-<version>-<reason>-<duration>`
  - Inside each folder is a `threadDump-<timestamp>.txt` file.

## Key Information to Extract
1. **IDE Version**: Found in `SystemInfo.log`. Useful for correlating with known bugs in specific releases.
2. **EDT Stack Trace**: In freeze thread dumps, the `AWT-EventQueue-0` (Event Dispatch Thread) is the most critical. Its stack trace reveals what was blocking the UI.
3. **Background Lock Holders**: If the EDT is idle or waiting, check other threads for IntelliJ lock acquisitions (e.g., `runReadAction`, `runWriteAction`).
4. **Deadlocks**: Search for "Found one Java-level deadlock" in thread dump files.

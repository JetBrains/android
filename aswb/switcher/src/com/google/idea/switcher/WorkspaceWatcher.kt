/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.switcher

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink
import java.nio.file.Path

/** A lightweight handle to a running background file watcher. */
interface WorkspaceWatcher {
  /** Checks if the watcher is still active and operational. */
  fun isOperational(): Boolean

  /** Configures the active set of physical paths to watch. */
  fun setWatchRoots(recursivePaths: List<Path>, flatPaths: List<Path>)

  /** Shuts down the watcher, unregistering subscriptions and releasing resources. */
  fun shutdown()
}

/** An Extension Point factory interface for environment-specific watchers (e.g. CitC, Cog). */
interface WorkspaceWatcherProvider {
  companion object {
    val EP_NAME = ExtensionPointName.create<WorkspaceWatcherProvider>("com.google.idea.switcher.workspaceWatcherProvider")
  }

  /** Attempts to create and start a watcher for the given physical target. Returns a [WorkspaceWatcher] handle if supported, or null. */
  fun createWatcher(physicalRoot: Path, sink: FileWatcherNotificationSink): WorkspaceWatcher?

  /**
   * Attempts to build an optimized diffing operation between two physical targets. Returns a function accepting the watched physical paths
   * and a sink to stream the delta, or null.
   */
  fun createDiffOperation(
    oldPhysicalRoot: Path,
    newPhysicalRoot: Path,
  ): ((watchedPhysicalPaths: Set<Path>, sink: FileWatcherNotificationSink) -> Unit)?
}

/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.autosync;

import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;

/**
 * Provides support for detecting when sync-sensitive files have changed, and both updating the sync
 * status and optionally kicking off a partial sync.
 */
public interface AutoSyncProvider {

  ExtensionPointName<AutoSyncProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AutoSyncProvider");

  String AUTO_SYNC_TITLE = "Automatic Sync";

  String AUTO_SYNC_REASON = "AutoSync";

  /**
   * Returns true if changes to this file are expected to impact the results of a project sync.
   *
   * <p>This is run on the event thread in response to every file write, so should be very quick
   * (approximate results are fine).
   */
  boolean isSyncSensitiveFile(Project project, VirtualFile file);

  /**
   * Returns {@link BlazeSyncParams} corresponding to an automatic sync in response to changes in
   * the given file. This will be combined with sync params from all available providers, and may be
   * batched across multiple modified files.
   *
   * <p>Returns null if this provider doesn't recommend automatically syncing.
   */
  @Nullable
  BlazeSyncParams getAutoSyncParamsForFile(Project project, VirtualFile modifiedFile);
}

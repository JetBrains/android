/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

/** Tracks and manages project target sync status for the purposes of automatic syncing. */
public interface ProjectTargetManager {

  /** A per-target / per-source sync status. */
  enum SyncStatus {
    UNSYNCED,
    RESYNCING, // previously synced; is currently being resynced
    IN_PROGRESS, // never previously synced; is currently being synced
    STALE, // previously synced, but stale
    SYNCED,
  }
}

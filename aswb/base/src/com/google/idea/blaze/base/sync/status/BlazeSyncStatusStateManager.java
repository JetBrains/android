/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.status;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/** Manages storage for persistent state used by implementations of {@link BlazeSyncStatus}. */
@State(name = "BlazeSyncStatusState", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
public class BlazeSyncStatusStateManager
    implements PersistentStateComponent<BlazeSyncStatusStateManager.BlazeSyncStatusState> {

  private volatile BlazeSyncStatusState myState = new BlazeSyncStatusState();

  public static BlazeSyncStatusStateManager getInstance(Project project) {
    return project.getService(BlazeSyncStatusStateManager.class);
  }

  public boolean isDirty() {
    return myState.dirty;
  }

  public void setDirty(boolean dirty) {
    myState.dirty = dirty;
  }

  public boolean lastSyncFailed() {
    return myState.lastSyncFailed;
  }

  public void setLastSyncFailed(boolean lastSyncFailed) {
    myState.lastSyncFailed = lastSyncFailed;
  }

  @Nullable
  @Override
  public BlazeSyncStatusState getState() {
    return myState;
  }

  @Override
  public void loadState(BlazeSyncStatusState state) {
    myState = state;
  }

  static class BlazeSyncStatusState {
    /** has a BUILD file changed since the last sync started */
    public volatile boolean dirty = false;

    public volatile boolean lastSyncFailed = false;
  }
}

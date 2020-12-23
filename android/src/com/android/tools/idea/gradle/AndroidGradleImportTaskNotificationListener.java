/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle;

import static com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.LISTENER_KEY;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * {@link AndroidGradleImportTaskNotificationListener} Listens for Gradle project import start/end events to apply android specific changes.
 */
public class AndroidGradleImportTaskNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {

  @NotNull private static final Key<Boolean> STARTED_FROM_GRADLE_PLUGIN = new Key<>("gradle.sync.started.from.gradle.plugin");

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    if (GradleConstants.SYSTEM_ID.getId().equals(id.getProjectSystemId().getId())
        && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      Project project = id.findProject();
      if (project == null) return;

      setExternalSystemTaskId(project, id);
      boolean movedToStartedState = promoteGradleSyncStateToStartedIfNeeded(project);
      project.putUserData(STARTED_FROM_GRADLE_PLUGIN, Boolean.valueOf(movedToStartedState));
    }
  }

  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId id) {
    if (GradleConstants.SYSTEM_ID.getId().equals(id.getProjectSystemId().getId())
        && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      // notify project sync successfully completed if needed
    }
  }

  @Override
  public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
    if (GradleConstants.SYSTEM_ID.getId().equals(id.getProjectSystemId().getId())
        && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      Project project = id.findProject();
      if (project == null) return;

      if (project.getUserData(STARTED_FROM_GRADLE_PLUGIN) == Boolean.TRUE) {
        // Sync was started from IDEA, and we are responsible to deliver `syncFailed` event to the GradleSyncState.
        // FIXME-ank4: in 4.2 gradle sync is reworked, and this hack should be removed
        //noinspection ConstantConditions
        assert LISTENER_KEY != null: "compiler will help us to not forget to revisit this piece of code: LISTENER_KEY is removed in 4.2";
        String msg = e.getMessage();
        GradleSyncState.getInstance(project).syncFailed(isNotEmpty(msg) ? msg : e.getClass().getCanonicalName(), e, null);
      } // else syncFailed will be invoked by ProjectSetUpTask#onFailure
    }
  }

  private static void setExternalSystemTaskId(@NotNull Project project, @NotNull ExternalSystemTaskId taskId) {
    // set current ExternalTaskId to GradleSyncState
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    syncState.setExternalSystemTaskId(taskId);
  }

  private static boolean promoteGradleSyncStateToStartedIfNeeded(@NotNull Project project) {
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (!syncState.isSyncInProgress()) {
      return syncState.syncStarted(new GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_UNKNOWN));
    }
    return false;
  }
}

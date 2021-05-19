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

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * {@link AndroidGradleImportTaskNotificationListener} Listens for Gradle project import start/end events to apply android specific changes.
 */
public class AndroidGradleImportTaskNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    if (GradleConstants.SYSTEM_ID.getId().equals(id.getProjectSystemId().getId())
        && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      setExternalSystemTaskId(id);
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
      // notify project sync failed  if needed
      // e.g. Projects.notifyProjectSyncCompleted(project, false);
    }
  }

  private static void setExternalSystemTaskId(@NotNull ExternalSystemTaskId taskId) {
    // set current ExternalTaskId to GradleSyncState
    Project project = taskId.findProject();
    if (project != null) {
      GradleSyncState syncState = GradleSyncState.getInstance(project);
      syncState.setExternalSystemTaskId(taskId);
    }
  }
}

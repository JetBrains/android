/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure;

import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings;
import static com.android.tools.idea.gradle.util.Projects.populate;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter.NULL_OBJECT;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

public class FastGradleSync {
  @NotNull private final GradleProjectResolver myProjectResolver = new GradleProjectResolver();

  @NotNull
  public Callback requestProjectSync(@NotNull Project project) {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
    messages.removeAllMessages();

    Callback callback = new Callback();
    GradleExecutionSettings settings = getGradleExecutionSettings(project);
    ExternalSystemTaskId id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, project);
    String projectPath = project.getBasePath();
    assert projectPath != null;

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        DataNode<ProjectData> projectDataNode = myProjectResolver.resolveProjectInfo(id, projectPath, false, settings, NULL_OBJECT);
        assert projectDataNode != null;
        invokeAndWaitIfNeeded((ThrowableRunnable)() -> populate(project, projectDataNode, false, false));
        callback.setDone();
      }
      catch (Throwable e) {
        callback.setRejected(e);
      }
    });
    return callback;
  }

  public static class Callback extends ActionCallback {
    @Nullable private Throwable myFailure;

    void setRejected(@Nullable Throwable failure) {
      myFailure = failure;
      myError = failure != null ? failure.getMessage() : null;
      setRejected();
    }

    @Nullable
    public Throwable getFailure() {
      return myFailure;
    }
  }
}

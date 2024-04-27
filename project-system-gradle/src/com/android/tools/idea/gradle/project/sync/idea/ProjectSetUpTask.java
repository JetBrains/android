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
package com.android.tools.idea.gradle.project.sync.idea;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ProjectSetUpTask implements ExternalProjectRefreshCallback {
  @NotNull private final Project myProject;
  @Nullable private final GradleSyncListener mySyncListener;

  ProjectSetUpTask(@NotNull Project project,
                   @Nullable GradleSyncListener syncListener) {
    myProject = project;
    mySyncListener = syncListener;
  }

  @WorkerThread
  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId taskId,
                        @Nullable DataNode<ProjectData> projectInfo) {
    assert projectInfo != null;
    doPopulateProject(projectInfo);
  }

  @WorkerThread
  private void doPopulateProject(@NotNull DataNode<ProjectData> projectInfo) {
    IdeaSyncPopulateProjectTask task = new IdeaSyncPopulateProjectTask(myProject);
    task.populateProject(projectInfo, mySyncListener);
  }

  @Override
  public void onFailure(@NotNull ExternalSystemTaskId externalTaskId, @NotNull String errorMessage, @Nullable String errorDetails) {
    // Make sure the failure was not because sync is already running, if so, then return
    // See b/75005810
    if (errorMessage.contains(ExternalSystemBundle.message("error.resolve.already.running", ""))) {
      return;
    }

    // Initialize the "Gradle Sync" tool window, otherwise any sync errors will not be displayed to the user.
    invokeAndWaitIfNeeded((Runnable)() -> {
      if (!myProject.isDisposed()) { // http://b/71799046
        ensureToolWindowContentInitialized(myProject, GRADLE_SYSTEM_ID);
      }
    });

    String exceptionMessage =
      (errorDetails == null || errorMessage.contains(errorDetails)) ? errorMessage : errorMessage + "\n" + errorDetails;
    String messageWithGuide = ExternalSystemBundle.message("error.resolve.with.reason", exceptionMessage);
    if (mySyncListener != null) {
      mySyncListener.syncFailed(myProject, messageWithGuide);
    }
  }
}

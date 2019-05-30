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

import com.android.tools.idea.gradle.project.AndroidGradleProjectComponent;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.project.sync.idea.ProjectFinder.unregisterAsNewProject;
import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.GRADLE_EXECUTION_ERRORS;
import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.finishFailedSync;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleProjects.open;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

class ProjectSetUpTask implements ExternalProjectRefreshCallback {
  @NotNull private final Project myProject;
  @NotNull private final PostSyncProjectSetup.Request mySetupRequest;

  @Nullable private final GradleSyncListener mySyncListener;

  ProjectSetUpTask(@NotNull Project project,
                   @NotNull PostSyncProjectSetup.Request setupRequest,
                   @Nullable GradleSyncListener syncListener) {
    myProject = project;
    mySetupRequest = setupRequest;
    mySyncListener = syncListener;
  }

  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId taskId,
                        @Nullable DataNode<ProjectData> projectInfo) {
    assert projectInfo != null;
    unregisterAsNewProject(myProject);

    if (mySyncListener != null) {
      mySyncListener.setupStarted(myProject);
    }
    GradleSyncState.getInstance(myProject).setupStarted();
    boolean importedProject = GradleProjectInfo.getInstance(myProject).isImportedProject();
    doPopulateProject(projectInfo, taskId);

    Runnable runnable = () -> {
      boolean isTest = ApplicationManager.getApplication().isUnitTestMode();
      if (!isTest || !GradleProjectImporter.ourSkipSetupFromTest) {
        if (importedProject) {
          open(myProject);
        }
        if (!isTest) {
          CommandProcessor.getInstance().runUndoTransparentAction(() -> myProject.save());
        }
      }

      if (importedProject) {
        // We need to do this because AndroidGradleProjectComponent#projectOpened is being called when the project is created, instead
        // of when the project is opened. When 'projectOpened' is called, the project is not fully configured, and it does not look
        // like it is Gradle-based, resulting in listeners (e.g. modules added events) not being registered. Here we force the
        // listeners to be registered.
        AndroidGradleProjectComponent projectComponent = AndroidGradleProjectComponent.getInstance(myProject);
        projectComponent.configureGradleProject();
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      TransactionGuard.getInstance().submitTransactionLater(myProject, runnable);
    }
  }

  private void doPopulateProject(@NotNull DataNode<ProjectData> projectInfo, @NotNull ExternalSystemTaskId taskId) {
    IdeaSyncPopulateProjectTask task = new IdeaSyncPopulateProjectTask(myProject);
    task.populateProject(projectInfo, taskId, mySetupRequest, mySyncListener);
  }

  @Override
  public void onFailure(@NotNull ExternalSystemTaskId externalTaskId, @NotNull String errorMessage, @Nullable String errorDetails) {
    // Make sure the failure was not because sync is already running, if so, then return
    // See b/75005810
    if (errorMessage.contains(ExternalSystemBundle.message("error.resolve.already.running", ""))) {
      return;
    }
    unregisterAsNewProject(myProject);

    // Initialize the "Gradle Sync" tool window, otherwise any sync errors will not be displayed to the user.
    invokeAndWaitIfNeeded((Runnable)() -> {
      if (!myProject.isDisposed()) { // http://b/71799046
        ensureToolWindowContentInitialized(myProject, GRADLE_SYSTEM_ID);
      }
    });

    String newMessage = ExternalSystemBundle.message("error.resolve.with.reason", errorMessage);
    // Remove cache data to force a sync next time the project is open. This is necessary when checking MD5s is not enough. For example,
    // when sync failed because the SDK being used by the project was accidentally removed in the SDK Manager. The state of the project did
    // not change, and if we don't force a sync, the project will use the cached state and it would look like there are no errors.
    ProjectBuildFileChecksums.removeFrom(myProject);
    // To ensure the errorDetails are logged by GradleSyncState, create a runtime exception.
    GradleSyncState.getInstance(myProject).syncFailed(newMessage, new RuntimeException(errorDetails), mySyncListener);
    GradleSyncMessages.getInstance(myProject).report(new SyncMessage(GRADLE_EXECUTION_ERRORS, MessageType.ERROR, errorMessage));
    finishFailedSync(externalTaskId, myProject);
  }
}

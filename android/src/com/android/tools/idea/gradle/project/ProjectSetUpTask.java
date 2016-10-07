/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.GradleSyncState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.Projects.open;
import static com.android.tools.idea.gradle.util.Projects.populate;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

class ProjectSetUpTask implements ExternalProjectRefreshCallback {
  private static final Logger LOG = Logger.getInstance(ProjectSetUpTask.class);

  @NotNull private final Project myProject;
  private final boolean myProjectIsNew;
  private final boolean mySelectModulesToImport;
  private final boolean mySyncSkipped;
  @Nullable private final GradleSyncListener mySyncListener;

  ProjectSetUpTask(@NotNull Project project,
                   boolean projectIsNew,
                   boolean selectModulesToImport,
                   boolean syncSkipped,
                   @Nullable GradleSyncListener syncListener) {
    myProject = project;
    myProjectIsNew = projectIsNew;
    mySelectModulesToImport = selectModulesToImport;
    mySyncSkipped = syncSkipped;
    mySyncListener = syncListener;
  }

  @Override
  public void onSuccess(@Nullable final DataNode<ProjectData> projectInfo) {
    assert projectInfo != null;

    populateProject(projectInfo);

    Runnable runnable = () -> {
      boolean isTest = ApplicationManager.getApplication().isUnitTestMode();
      if (!isTest || !GradleProjectImporter.ourSkipSetupFromTest) {
        if (myProjectIsNew) {
          open(myProject);
        }
        if (!isTest) {
          myProject.save();
        }
      }

      if (myProjectIsNew) {
        // We need to do this because AndroidGradleProjectComponent#projectOpened is being called when the project is created, instead
        // of when the project is opened. When 'projectOpened' is called, the project is not fully configured, and it does not look
        // like it is Gradle-based, resulting in listeners (e.g. modules added events) not being registered. Here we force the
        // listeners to be registered.
        AndroidGradleProjectComponent projectComponent = AndroidGradleProjectComponent.getInstance(myProject);
        projectComponent.configureGradleProject();
      }
      if (mySyncListener != null) {
        if (mySyncSkipped) {
          mySyncListener.syncSkipped(myProject);
        }
        else {
          mySyncListener.syncSucceeded(myProject);
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      invokeLaterIfNeeded(runnable);
    }
  }

  private void populateProject(@NotNull final DataNode<ProjectData> projectInfo) {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> populate(myProject, projectInfo, mySelectModulesToImport, true));
  }

  @Override
  public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    // Initialize the "Gradle Sync" tool window, otherwise any sync errors will not be displayed to the user.
    invokeAndWaitIfNeeded(() -> ensureToolWindowContentInitialized(myProject, GRADLE_SYSTEM_ID));

    if (errorDetails != null) {
      LOG.warn(errorDetails);
    }
    String newMessage = ExternalSystemBundle.message("error.resolve.with.reason", errorMessage);
    LOG.info(newMessage);

    // Remove cache data to force a sync next time the project is open. This is necessary when checking MD5s is not enough. For example,
    // when sync failed because the SDK being used by the project was accidentally removed in the SDK Manager. The state of the project did
    // not change, and if we don't force a sync, the project will use the cached state and it would look like there are no errors.
    GradleProjectSyncData.removeFrom(myProject);
    GradleSyncState.getInstance(myProject).syncFailed(newMessage);

    if (mySyncListener != null) {
      mySyncListener.syncFailed(myProject, newMessage);
    }
  }
}

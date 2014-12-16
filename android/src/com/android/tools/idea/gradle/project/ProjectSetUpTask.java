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
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll;

class ProjectSetUpTask implements ExternalProjectRefreshCallback {
  private static final Logger LOG = Logger.getInstance(ProjectSetUpTask.class);

  @NotNull private final Project myProject;
  private final boolean myProjectIsNew;
  @Nullable private final GradleSyncListener mySyncListener;

  ProjectSetUpTask(@NotNull Project project, boolean projectIsNew, @Nullable GradleSyncListener syncListener) {
    myProject = project;
    myProjectIsNew = projectIsNew;
    mySyncListener = syncListener;
  }

  @Override
  public void onSuccess(@Nullable final DataNode<ProjectData> projectInfo) {
    assert projectInfo != null;

    populateProject(projectInfo);

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        boolean isTest = ApplicationManager.getApplication().isUnitTestMode();
        if (!isTest || !GradleProjectImporter.ourSkipSetupFromTest) {
          if (myProjectIsNew) {
            Projects.open(myProject);
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
          AndroidGradleProjectComponent projectComponent = ServiceManager.getService(myProject, AndroidGradleProjectComponent.class);
          projectComponent.configureGradleProject(false);
        }
        if (mySyncListener != null) {
          mySyncListener.syncSucceeded(myProject);
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      UIUtil.invokeLaterIfNeeded(runnable);
    }
  }

  private void populateProject(@NotNull final DataNode<ProjectData> projectInfo) {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        final Collection<DataNode<ModuleData>> modules = findAll(projectInfo, ProjectKeys.MODULE);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                if (!myProject.isDisposed()) {
                  ProjectRootManagerEx.getInstanceEx(myProject).mergeRootsChangesDuring(new Runnable() {
                    @Override
                    public void run() {
                      ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
                      dataManager.importData(ProjectKeys.MODULE, modules, myProject, true /* synchronous */);
                    }
                  });
                }
              }
            });
            // We need to call this method here, otherwise the IDE will think the project is not a Gradle project and it won't generate
            // sources for it. This happens on new projects.
            PostProjectSetupTasksExecutor.getInstance(myProject).onProjectSyncCompletion();
          }
        });
      }
    });
  }

  @Override
  public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    if (errorDetails != null) {
      LOG.warn(errorDetails);
    }
    String newMessage = ExternalSystemBundle.message("error.resolve.with.reason", errorMessage);
    LOG.info(newMessage);

    GradleSyncState.getInstance(myProject).syncFailed(newMessage);

    if (mySyncListener != null) {
      mySyncListener.syncFailed(myProject, newMessage);
    }
  }
}

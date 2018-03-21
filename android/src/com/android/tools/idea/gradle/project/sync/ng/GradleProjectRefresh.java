/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.scheduleExternalViewStructureUpdate;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

/**
 * Refresh gradle project.
 */
public class GradleProjectRefresh {
  @NotNull private final Project myProject;
  @NotNull private final SyncExecutor mySyncExecutor;
  @NotNull private final SyncExecutionCallback.Factory myCallbackFactory;
  @NotNull private final ProjectSetup.Factory mySetupFactory;

  public GradleProjectRefresh(@NotNull Project project) {
    this(project, new SyncExecutor(project), new SyncExecutionCallback.Factory(), new ProjectSetup.Factory());
  }

  @VisibleForTesting
  GradleProjectRefresh(@NotNull Project project,
                       @NotNull SyncExecutor syncExecutor,
                       @NotNull SyncExecutionCallback.Factory callbackFactory,
                       @NotNull ProjectSetup.Factory setupFactory) {
    myProject = project;
    mySyncExecutor = syncExecutor;
    myCallbackFactory = callbackFactory;
    mySetupFactory = setupFactory;
  }

  public void refresh() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      refresh(new EmptyProgressIndicator());
      return;
    }
    Task task = new Task.Backgroundable(myProject, "Refresh Project", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        refresh(indicator);
      }
    };
    ApplicationManager.getApplication().invokeLater(task::queue, ModalityState.defaultModalityState());
  }

  private void refresh(@NotNull ProgressIndicator indicator) {
    SyncExecutionCallback callback = myCallbackFactory.create();

    callback.doWhenDone((() -> {
      SyncProjectModels models = callback.getModels();
      if (models != null) {
        ProjectSetup projectSetup = mySetupFactory.create(myProject);
        projectSetup.setUpProject(models, indicator);
        projectSetup.commit();
        scheduleExternalViewStructureUpdate(myProject, SYSTEM_ID);
      }
    }));
    mySyncExecutor.syncProject(indicator, callback);
  }
}

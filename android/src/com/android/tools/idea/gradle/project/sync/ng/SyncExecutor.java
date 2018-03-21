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

import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.ng.GradleSyncProgress.notifyProgress;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static org.gradle.tooling.GradleConnector.newCancellationTokenSource;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

class SyncExecutor {
  @NotNull private final Project myProject;
  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final SyncErrorHandlerManager myErrorHandlerManager;
  @NotNull private final ExtraGradleSyncModelsManager myExtraModelsManager;

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  SyncExecutor(@NotNull Project project) {
    this(project, ExtraGradleSyncModelsManager.getInstance(), new CommandLineArgs(true /* apply Java library plugin */),
         new SyncErrorHandlerManager(project));
  }

  @VisibleForTesting
  SyncExecutor(@NotNull Project project,
               @NotNull ExtraGradleSyncModelsManager extraModelsManager,
               @NotNull CommandLineArgs commandLineArgs,
               @NotNull SyncErrorHandlerManager errorHandlerManager) {
    myProject = project;
    myCommandLineArgs = commandLineArgs;
    myErrorHandlerManager = errorHandlerManager;
    myExtraModelsManager = extraModelsManager;
  }

  void syncProject(@NotNull ProgressIndicator indicator, @NotNull SyncExecutionCallback callback) {
    if (myProject.isDisposed()) {
      callback.reject(String.format("Project '%1$s' is already disposed", myProject.getName()));
    }

    // TODO: Handle sync cancellation.

    GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(myProject);
    Function<ProjectConnection, Void> syncFunction = connection -> {
      SyncAction syncAction = new SyncAction(myExtraModelsManager.getAndroidModelTypes(), myExtraModelsManager.getJavaModelTypes());
      BuildActionExecuter<SyncProjectModels> executor = connection.action(syncAction);

      List<String> commandLineArgs = myCommandLineArgs.get(myProject);

      // We try to avoid passing JVM arguments, to share Gradle daemons between Gradle sync and Gradle build.
      // If JVM arguments from Gradle sync are different than the ones from Gradle build, Gradle won't reuse daemons. This is bad because
      // daemons are expensive (memory-wise) and slow to start.
      ExternalSystemTaskId id = createId(myProject);
      prepare(executor, id, executionSettings, new GradleSyncNotificationListener(indicator), Collections.emptyList() /* JVM args */,
              commandLineArgs, connection);

      CancellationTokenSource cancellationTokenSource = newCancellationTokenSource();
      executor.withCancellationToken(cancellationTokenSource.token());

      try {
        SyncProjectModels models = executor.run();
        callback.setDone(models);
      }
      catch (RuntimeException e) {
        myErrorHandlerManager.handleError(e);
        callback.setRejected(e);
      }
      return null;
    };

    try {
      myHelper.execute(getBaseDirPath(myProject).getPath(), executionSettings, syncFunction);
    }
    catch (Throwable e) {
      callback.setRejected(e);
    }
  }

  @NotNull
  private static ExternalSystemTaskId createId(@NotNull Project project) {
    return ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, project);
  }

  @VisibleForTesting
  static class GradleSyncNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
    @NotNull private final ProgressIndicator myIndicator;

    GradleSyncNotificationListener(@NotNull ProgressIndicator indicator) {
      myIndicator = indicator;
    }

    @Override
    public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
      notifyProgress(myIndicator, event.getDescription());
    }
  }
}

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

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewGradleSync implements GradleSync {
  @NotNull private final Project myProject;
  @NotNull private final SyncExecutor mySyncExecutor;
  @NotNull private final SyncResultHandler myResultHandler;
  @NotNull private final SyncExecutionCallback.Factory myCallbackFactory;

  public static boolean isEnabled() {
    return isOptionVisible() && GradleExperimentalSettings.getInstance().USE_NEW_GRADLE_SYNC;
  }

  public static boolean isLevel4Model() {
    return isEnabled();
  }

  public static boolean isOptionVisible() {
    return SystemProperties.getBooleanProperty("show.new.sync.preference", false);
  }

  public NewGradleSync(@NotNull Project project) {
    this(project, new SyncExecutor(project), new SyncResultHandler(project), new SyncExecutionCallback.Factory());
  }

  @VisibleForTesting
  NewGradleSync(@NotNull Project project,
                @NotNull SyncExecutor syncExecutor,
                @NotNull SyncResultHandler resultHandler,
                @NotNull SyncExecutionCallback.Factory callbackFactory) {
    myProject = project;
    mySyncExecutor = syncExecutor;
    myResultHandler = resultHandler;
    myCallbackFactory = callbackFactory;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      sync(listener, new EmptyProgressIndicator(), request.isNewOrImportedProject());
      return;
    }
    Task task = createSyncTask(request, listener);
    ApplicationManager.getApplication().invokeLater(task::queue, ModalityState.defaultModalityState());
  }

  @VisibleForTesting
  @NotNull
  Task createSyncTask(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    String title = "Gradle Sync"; // TODO show Gradle feedback

    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    boolean isNewProject = request.isNewOrImportedProject();
    switch (executionMode) {
      case MODAL_SYNC:
        return new Task.Modal(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(listener, indicator, isNewProject);
          }
        };
      case IN_BACKGROUND_ASYNC:
        return new Task.Backgroundable(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(listener, indicator, isNewProject);
          }
        };
      default:
        throw new IllegalArgumentException(executionMode + " is not a supported execution mode");
    }
  }

  private void sync(@Nullable GradleSyncListener syncListener, @NotNull ProgressIndicator indicator, boolean isNewProject) {
    SyncExecutionCallback callback = myCallbackFactory.create();
    // @formatter:off
    callback.doWhenDone(() -> myResultHandler.onSyncFinished(callback, indicator, syncListener, isNewProject))
            .doWhenRejected(() -> myResultHandler.onSyncFailed(callback, syncListener));
    // @formatter:on
    mySyncExecutor.syncProject(indicator, callback);
  }
}

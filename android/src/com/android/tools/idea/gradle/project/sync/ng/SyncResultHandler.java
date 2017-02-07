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

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;

class SyncResultHandler {
  @NotNull private final Project myProject;
  @NotNull private final GradleSyncState mySyncState;
  @NotNull private final ProjectSetup.Factory myProjectSetupFactory;
  @NotNull private final PostSyncProjectSetup myPostSyncProjectSetup;

  SyncResultHandler(@NotNull Project project) {
    this(project, GradleSyncState.getInstance(project), new ProjectSetup.Factory(), PostSyncProjectSetup.getInstance(project));
  }

  @VisibleForTesting
  SyncResultHandler(@NotNull Project project,
                    @NotNull GradleSyncState syncState,
                    @NotNull ProjectSetup.Factory projectSetupFactory,
                    @NotNull PostSyncProjectSetup postSyncProjectSetup) {
    myProject = project;
    mySyncState = syncState;
    myProjectSetupFactory = projectSetupFactory;
    myPostSyncProjectSetup = postSyncProjectSetup;
  }

  void onSyncFinished(@NotNull SyncExecutionCallback callback,
                      @NotNull ProgressIndicator indicator,
                      @Nullable GradleSyncListener syncListener) {
    SyncAction.ProjectModels models = callback.getModels();
    if (models != null) {
      try {
        setUpProject(myProject, models, indicator, syncListener);
      }
      catch (Throwable e) {
        notifyAndLogSyncError(nullToUnknownErrorCause(getRootCauseMessage(e)), e, syncListener);
      }
    }
    else {
      // SyncAction.ProjectModels should not be null. Something went wrong.
      notifyAndLogSyncError("Gradle did not return any project models", null /* no exception */, syncListener);
    }
  }

  private void setUpProject(@NotNull Project project,
                            @NotNull SyncAction.ProjectModels models,
                            @NotNull ProgressIndicator indicator,
                            @Nullable GradleSyncListener syncListener) {
    try {
      if (syncListener != null) {
        syncListener.setupStarted(project);
      }
      mySyncState.setupStarted();

      ProjectSetup projectSetup = myProjectSetupFactory.create(project);
      projectSetup.setUpProject(models, indicator);
      projectSetup.commit( /* synchronous */);

      if (syncListener != null) {
        syncListener.syncSucceeded(project);
      }

      PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
      myPostSyncProjectSetup.setUpProject(request, indicator);
    }
    catch (Throwable e) {
      notifyAndLogSyncError(nullToUnknownErrorCause(getRootCauseMessage(e)), e, syncListener);
    }
  }

  void onSyncFailed(@NotNull SyncExecutionCallback callback, @Nullable GradleSyncListener syncListener) {
    Throwable error = callback.getSyncError();
    String errorMessage = error != null ? getRootCauseMessage(error) : callback.getError();
    errorMessage = nullToUnknownErrorCause(errorMessage);
    notifyAndLogSyncError(errorMessage, error, syncListener);
  }

  private void notifyAndLogSyncError(@NotNull String errorMessage, @Nullable Throwable error, @Nullable GradleSyncListener syncListener) {
    if (syncListener != null) {
      syncListener.syncFailed(myProject, errorMessage);
    }
    mySyncState.syncFailed(errorMessage);
    if (error != null) {
      getLog().warn("Gradle sync failed", error);
    }
    else {
      logSyncFailure(errorMessage);
    }
  }

  @Nullable
  private static String getRootCauseMessage(@NotNull Throwable error) {
    Throwable rootCause = getRootCause(error);
    String message = rootCause.getMessage();
    return isEmpty(message) ? rootCause.getClass().getName() : message;
  }

  @NotNull
  private static String nullToUnknownErrorCause(@Nullable String errorMessage) {
    return isEmpty(errorMessage) ? "<Unknown cause>" : errorMessage;
  }

  private static void logSyncFailure(@NotNull String errorMessage) {
    getLog().warn("Gradle sync failed: " + errorMessage);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(SyncResultHandler.class);
  }
}

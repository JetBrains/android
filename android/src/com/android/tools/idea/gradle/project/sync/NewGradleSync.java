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
package com.android.tools.idea.gradle.project.sync;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.Function;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static org.gradle.tooling.GradleConnector.newCancellationTokenSource;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

public class NewGradleSync implements GradleSync {
  @NotNull private final CommandLineArgs myCommandLineArgs;
  @NotNull private final ProjectSetup.Factory myProjectSetupFactory;

  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  NewGradleSync() {
    this(new CommandLineArgs(true /* create classpath init script */), new ProjectSetup.Factory());
  }

  @VisibleForTesting
  NewGradleSync(@NotNull CommandLineArgs commandLineArgs, @NotNull ProjectSetup.Factory projectSetupFactory) {
    myCommandLineArgs = commandLineArgs;
    myProjectSetupFactory = projectSetupFactory;
  }

  @Override
  public void sync(@NotNull Project project, @NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    String title = String.format("Syncing project '%1$s' with Gradle", project.getName());
    Task task;
    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    switch (executionMode) {
      case MODAL_SYNC:
        task = new Task.Modal(project, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(project, indicator, listener);
          }
        };
        break;
      case IN_BACKGROUND_ASYNC:
        task = new Task.Backgroundable(project, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(project, indicator, listener);
          }
        };
        break;
      default:
        throw new IllegalArgumentException(executionMode + " is not a supported execution mode");
    }
    invokeAndWaitIfNeeded((Runnable)task::queue);
  }

  private void sync(@NotNull Project project, @NotNull ProgressIndicator indicator, @Nullable GradleSyncListener syncListener) {
    Callback callback = sync(project);
    // @formatter:off
    callback.doWhenDone(() -> onSyncFinished(project, callback, indicator, syncListener))
            .doWhenRejected(() -> onSyncFailed(project, callback, syncListener));
    // @formatter:on
  }

  @VisibleForTesting
  @NotNull
  Callback sync(@NotNull Project project) {
    Callback callback = new Callback();

    if (project.isDisposed()) {
      callback.reject(String.format("Project '%1$s' is already disposed", project.getName()));
    }

    // TODO: Handle sync cancellation.
    // TODO: Show Gradle output.

    GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(project, useEmbeddedGradle());

    Function<ProjectConnection, Void> syncFunction = connection -> {
      //noinspection deprecation
      List<Class<?>> modelTypes = Lists.newArrayList(
        AndroidProject.class, // Android Java
        NativeAndroidProject.class, // Android Native
        GradleBuild.class,
        ModuleExtendedModel.class // Java libraries. TODO: contribute the Java model back to Gradle.
      );
      BuildActionExecuter<SyncAction.ProjectModels> executor = connection.action(new SyncAction(modelTypes));

      ExternalSystemTaskNotificationListener listener = new ExternalSystemTaskNotificationListenerAdapter() {
        // TODO: implement
      };
      List<String> commandLineArgs = myCommandLineArgs.get(project  /* include classpath init script */);

      // We try to avoid passing JVM arguments, to share Gradle daemons between Gradle sync and Gradle build.
      // If JVM arguments from Gradle sync are different than the ones from Gradle build, Gradle won't reuse daemons. This is bad because
      // daemons are expensive (memory-wise) and slow to start.
      ExternalSystemTaskId id = createId(project);
      prepare(executor, id, executionSettings, listener, Collections.emptyList() /* JVM args */, commandLineArgs, connection);

      CancellationTokenSource cancellationTokenSource = newCancellationTokenSource();
      executor.withCancellationToken(cancellationTokenSource.token());

      try {
        SyncAction.ProjectModels models = executor.run();
        callback.setDone(models);
      }
      catch (RuntimeException e) {
        callback.setRejected(e);
      }

      return null;
    };

    myHelper.execute(getBaseDirPath(project).getPath(), executionSettings, syncFunction);
    return callback;
  }

  @NotNull
  private static ExternalSystemTaskId createId(@NotNull Project project) {
    return ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, project);
  }

  private static boolean useEmbeddedGradle() {
    // Do not use the Gradle distribution embedded in Android Studio, but the one set in the project's preference ("local" or "wrapper.")
    return false;
  }

  private void onSyncFinished(@NotNull Project project,
                              @NotNull Callback callback,
                              @NotNull ProgressIndicator indicator,
                              @Nullable GradleSyncListener syncListener) {
    SyncAction.ProjectModels models = callback.getModels();
    if (models != null) {
      try {
        ProjectSetup projectSetup = myProjectSetupFactory.create(project);
        projectSetup.setUpProject(models, indicator);
        projectSetup.commit(true /* synchronous */);

        if (syncListener != null) {
          syncListener.syncSucceeded(project);
        }

        GradleSyncState.getInstance(project).syncEnded();
      }
      catch (Throwable e) {
        notifyAndLogSyncError(project, nullToUnknownErrorCause(getRootCauseMessage(e)), syncListener);
      }
    }
    else {
      // SyncAction.ProjectModels should not be null. Something went wrong.
      notifyAndLogSyncError(project, "Gradle did not return any project models", syncListener);
    }
  }

  private static void notifyAndLogSyncError(@NotNull Project project,
                                            @NotNull String errorMessage,
                                            @Nullable GradleSyncListener syncListener) {
    if (syncListener != null) {
      syncListener.syncFailed(project, errorMessage);
    }
    logSyncFailure(errorMessage);
  }

  private static void onSyncFailed(@NotNull Project project, @NotNull Callback callback, @Nullable GradleSyncListener syncListener) {
    Throwable error = callback.getSyncError();
    //noinspection ThrowableResultOfMethodCallIgnored
    String errorMessage = error != null ? getRootCauseMessage(error) : callback.getError();
    errorMessage = nullToUnknownErrorCause(errorMessage);

    if (syncListener != null) {
      syncListener.syncFailed(project, errorMessage);
    }

    GradleSyncState.getInstance(project).syncFailed(errorMessage);

    if (error != null) {
      getLog().warn("Gradle sync failed", error);
    }
    else {
      logSyncFailure(errorMessage);
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(NewGradleSync.class);
  }

  @Nullable
  private static String getRootCauseMessage(@NotNull Throwable error) {
    //noinspection ThrowableResultOfMethodCallIgnored
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

  @VisibleForTesting
  static class Callback extends ActionCallback {
    @Nullable private SyncAction.ProjectModels myModels;
    @Nullable private Throwable mySyncError;

    @Nullable
    SyncAction.ProjectModels getModels() {
      return myModels;
    }

    void setDone(@Nullable SyncAction.ProjectModels models) {
      myModels = models;
      setDone();
    }

    @Nullable
    Throwable getSyncError() {
      return mySyncError;
    }

    void setRejected(@NotNull Throwable error) {
      mySyncError = error;
      setRejected();
    }
  }
}

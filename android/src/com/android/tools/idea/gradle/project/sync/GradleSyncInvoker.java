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

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.project.GradleProjectSyncData;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.cleanup.PreSyncProjectCleanUp;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncChecks;
import com.google.common.collect.Lists;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
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

import static com.android.tools.idea.gradle.project.NewProjectImportGradleSyncListener.createTopLevelProjectAndOpen;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;
import static org.gradle.tooling.GradleConnector.newCancellationTokenSource;
import static org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.prepare;

public class GradleSyncInvoker {
  @NotNull private final Project myProject;
  @NotNull private final PreSyncProjectCleanUp myProjectCleanUp;
  @NotNull private final PreSyncChecks myPreSyncChecks;
  @NotNull private final ProjectSetup.Factory myProjectSetupFactory;
  @NotNull private final GradleExecutionHelper myHelper = new GradleExecutionHelper();

  @NotNull
  public static GradleSyncInvoker getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSyncInvoker.class);
  }

  public GradleSyncInvoker(@NotNull Project project, @NotNull PreSyncChecks preSyncChecks, @NotNull PreSyncProjectCleanUp projectCleanUp) {
    this(project, preSyncChecks, projectCleanUp, new ProjectSetup.Factory());
  }

  @VisibleForTesting
  GradleSyncInvoker(@NotNull Project project,
                    @NotNull PreSyncChecks preSyncChecks,
                    @NotNull PreSyncProjectCleanUp projectCleanUp,
                    @NotNull ProjectSetup.Factory projectSetupFactory) {
    myProject = project;
    myPreSyncChecks = preSyncChecks;
    myProjectSetupFactory = projectSetupFactory;
    myProjectCleanUp = projectCleanUp;
  }

  public void sync(@NotNull ProgressExecutionMode mode, @Nullable GradleSyncListener syncListener) {
    PreSyncCheckResult canSync = myPreSyncChecks.canSync(myProject);
    if (!canSync.isSuccess()) {
      String cause = nullToEmpty(canSync.getFailureCause());
      handlePreSyncCheckFailure(cause, syncListener);
      return;
    }

    myProjectCleanUp.execute();

    String title = String.format("Syncing project '%1$s' with Gradle", myProject.getName());
    Task task;
    switch (mode) {
      case MODAL_SYNC:
        task = new Task.Modal(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(indicator, syncListener);
          }
        };
        break;
      case IN_BACKGROUND_ASYNC:
        task = new Task.Backgroundable(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(indicator, syncListener);
          }
        };
        break;
      default:
        throw new IllegalArgumentException(mode + " is not a supported execution mode");
    }
    invokeAndWaitIfNeeded((Runnable)task::queue);
  }

  private void handlePreSyncCheckFailure(@NotNull String failureCause, @Nullable GradleSyncListener syncListener) {
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (syncState.syncStarted(true)) {
      createTopLevelProjectAndOpen(myProject);
      syncState.syncFailed(failureCause);
      if (syncListener != null) {
        syncListener.syncFailed(myProject, failureCause);
      }
    }
  }

  private void sync(@NotNull ProgressIndicator indicator, @Nullable GradleSyncListener syncListener) {
    Callback callback = sync();
    // @formatter:off
    callback.doWhenDone(() -> onSyncFinished(callback, indicator, syncListener))
            .doWhenRejected(() -> onSyncFailed(callback, syncListener));
    // @formatter:on
  }

  @NotNull
  private Callback sync() {
    Callback callback = new Callback();

    if (myProject.isDisposed()) {
      callback.reject(String.format("Project '%1$s' is already disposed", myProject.getName()));
    }

    // TODO: Handle sync cancellation.
    // TODO: Show Gradle output.

    GradleExecutionSettings executionSettings = getOrCreateGradleExecutionSettings(myProject, useEmbeddedGradle());

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
      List<String> commandLineArgs = new CommandLineArgs(myProject).get();

      // We try to avoid passing JVM arguments, to share Gradle daemons between Gradle sync and Gradle build.
      // If JVM arguments from Gradle sync are different than the ones from Gradle build, Gradle won't reuse daemons. This is bad because
      // daemons are expensive (memory-wise) and slow to start.
      prepare(executor, createId(), executionSettings, listener, Collections.emptyList() /* JVM args */, commandLineArgs, connection);

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

    myHelper.execute(getBaseDirPath(myProject).getPath(), executionSettings, syncFunction);
    return callback;
  }

  @NotNull
  private ExternalSystemTaskId createId() {
    return ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, myProject);
  }

  private static boolean useEmbeddedGradle() {
    // Do not use the Gradle distribution embedded in Android Studio, but the one set in the project's preference ("local" or "wrapper.")
    return false;
  }

  private void onSyncFinished(@NotNull Callback callback,
                              @NotNull ProgressIndicator indicator,
                              @Nullable GradleSyncListener syncListener) {
    SyncAction.ProjectModels models = callback.getModels();
    if (models != null) {
      try {
        ProjectSetup projectSetup = myProjectSetupFactory.create(myProject);
        projectSetup.setUpProject(models, indicator);
        projectSetup.commit(true /* synchronous */);

        if (syncListener != null) {
          syncListener.syncSucceeded(myProject);
        }
      }
      catch (Throwable e) {
        notifyAndLogSyncError(syncListener, nullToUnknownErrorCause(getRootCauseMessage(e)));
      }
    }
    else {
      // SyncAction.ProjectModels should not be null. Something went wrong.
      notifyAndLogSyncError(syncListener, "Gradle did not return any project models");
    }
  }

  private void notifyAndLogSyncError(@Nullable GradleSyncListener syncListener, @NotNull String errorMessage) {
    if (syncListener != null) {
      syncListener.syncFailed(myProject, errorMessage);
    }
    logSyncFailure(errorMessage);
  }

  private void onSyncFailed(@NotNull Callback callback, @Nullable GradleSyncListener syncListener) {
    Throwable error = callback.getSyncError();
    //noinspection ThrowableResultOfMethodCallIgnored
    String errorMessage = error != null ? getRootCauseMessage(error) : callback.getError();
    errorMessage = nullToUnknownErrorCause(errorMessage);

    if (syncListener != null) {
      syncListener.syncFailed(myProject, errorMessage);
    }

    if (error != null) {
      getLog().warn("Gradle sync failed", error);
    }
    else {
      logSyncFailure(errorMessage);
    }
  }

  // Made 'public' to avoid duplication with ProjectSetUpTask#onFailure.
  // TODO: make 'private' once the new Gradle sync is the default one.
  public void handleSyncFailure(@NotNull String errorMessage, @Nullable GradleSyncListener syncListener) {
    String newMessage = ExternalSystemBundle.message("error.resolve.with.reason", errorMessage);
    getLog().info(newMessage);

    // Remove cache data to force a sync next time the project is open. This is necessary when checking MD5s is not enough. For example,
    // when sync failed because the SDK being used by the project was accidentally removed in the SDK Manager. The state of the project did
    // not change, and if we don't force a sync, the project will use the cached state and it would look like there are no errors.
    GradleProjectSyncData.removeFrom(myProject);
    GradleSyncState.getInstance(myProject).syncFailed(newMessage);

    if (syncListener != null) {
      syncListener.syncFailed(myProject, newMessage);
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(GradleSyncInvoker.class);
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

  private static class Callback extends ActionCallback {
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

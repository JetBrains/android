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

import static com.android.tools.idea.gradle.util.GradleProjects.setSyncRequestedDuringBuild;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.IN_BACKGROUND_ASYNC;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleTasksExecutor;
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleSyncInvoker {
  private static final Logger LOG = Logger.getInstance(GradleSyncInvoker.class);

  @NotNull
  public static GradleSyncInvoker getInstance() {
    return ApplicationManager.getApplication().getService(GradleSyncInvoker.class);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSync(@NotNull Project project,
                                 @NotNull GradleSyncStats.Trigger trigger) {
    requestProjectSync(project, trigger, null);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSync(@NotNull Project project,
                                 @NotNull GradleSyncStats.Trigger trigger,
                                 @Nullable GradleSyncListener listener) {
    Request request = new Request(trigger);
    requestProjectSync(project, request, listener);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSync(@NotNull Project project, @NotNull Request request) {
    requestProjectSync(project, request, null);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  public void requestProjectSync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    if (isBuildInProgress(project)) {
      setSyncRequestedDuringBuild(project, true);
      return;
    }

    Runnable syncTask = () -> {
      ensureToolWindowContentInitialized(project, GRADLE_SYSTEM_ID);
      if (prepareProject(project, listener)) {
        sync(project, request, listener);
      }
    };

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      application.invokeAndWait(syncTask);
    }
    else if (request.runInBackground) {
      ApplicationManager.getApplication().invokeLater(syncTask);
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(syncTask);
    }
  }

  private static boolean isBuildInProgress(@NotNull Project project) {
    IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
    StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar == null) {
      return false;
    }
    for (Pair<TaskInfo, ProgressIndicator> backgroundProcess : statusBar.getBackgroundProcesses()) {
      TaskInfo task = backgroundProcess.getFirst();
      if (task instanceof GradleTasksExecutor) {
        ProgressIndicator second = backgroundProcess.getSecond();
        if (second.isRunning()) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean prepareProject(@NotNull Project project, @Nullable GradleSyncListener listener) {
    GradleProjectInfo projectInfo = GradleProjectInfo.getInstance(project);
    if (AndroidProjectInfo.getInstance(project).requiresAndroidModel() || projectInfo.hasTopLevelGradleFile()) {
      boolean isImportedProject = projectInfo.isImportedProject();
      if (!isImportedProject) {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
      return true; // continue with sync.
    }
    invokeLaterIfProjectAlive(project, () -> {
      String msg = String.format("The project '%s' is not a Gradle-based project", project.getName());
      LOG.error(msg);
      AndroidNotification.getInstance(project).showBalloon("Project Sync", msg, ERROR, new OpenMigrationToGradleUrlHyperlink());

      if (listener != null) {
        listener.syncFailed(project, msg);
      }
    });
    return false; // stop sync.
  }

  @WorkerThread
  private static void sync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    invokeAndWaitIfNeeded((Runnable)() -> GradleSyncMessages.getInstance(project).removeAllMessages());
    new GradleSyncExecutor(project).sync(request, listener);
  }

  @WorkerThread
  public void fetchAndMergeNativeVariants(@NotNull Project project,
                                          @NotNull Set<@NotNull String> requestedAbis) {
    new GradleSyncExecutor(project).fetchAndMergeNativeVariants(requestedAbis);
  }

  @WorkerThread
  @NotNull
  public List<GradleModuleModels> fetchGradleModels(@NotNull Project project) {
    return new GradleSyncExecutor(project).fetchGradleModels();
  }

  public static class Request {
    public final GradleSyncStats.Trigger trigger;

    public boolean runInBackground = true;
    public boolean forceFullVariantsSync;
    public boolean skipPreSyncChecks;

    // Perform a variant-only sync if not null.

    @VisibleForTesting
    @NotNull
    public static Request testRequest() {
      return new Request(TRIGGER_TEST_REQUESTED);
    }

    public Request(@NotNull GradleSyncStats.Trigger trigger) {
      this.trigger = trigger;
    }

    @NotNull
    public ProgressExecutionMode getProgressExecutionMode() {
      return runInBackground ? IN_BACKGROUND_ASYNC : MODAL_SYNC;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Request request = (Request)o;
      return trigger == request.trigger &&
             runInBackground == request.runInBackground &&
             forceFullVariantsSync == request.forceFullVariantsSync &&
             skipPreSyncChecks == request.skipPreSyncChecks;
    }

    @Override
    public int hashCode() {
      return Objects
        .hash(trigger, runInBackground,
              forceFullVariantsSync, skipPreSyncChecks);
    }

    @Override
    public String toString() {
      return "RequestSettings{" +
             "trigger=" + trigger +
             ", runInBackground=" + runInBackground +
             ", forceFullVariantsSync=" + forceFullVariantsSync +
             ", skipPreSyncChecks=" + skipPreSyncChecks +
             '}';
    }
  }

  @TestOnly
  public static class FakeInvoker extends GradleSyncInvoker {
    @Override
    public void requestProjectSync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
      if (listener != null) {
        listener.syncSkipped(project);
      }
    }
  }
}

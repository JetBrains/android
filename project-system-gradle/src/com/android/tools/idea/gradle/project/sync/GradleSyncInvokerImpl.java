/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

import com.android.annotations.concurrency.WorkerThread;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleSyncInvokerImpl implements GradleSyncInvoker {
  private static final Logger LOG = Logger.getInstance(GradleSyncInvoker.class);

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  @Override public void requestProjectSync(@NotNull Project project,
                                 @NotNull GradleSyncStats.Trigger trigger) {
    requestProjectSync(project, trigger, null);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  @Override public void requestProjectSync(@NotNull Project project,
                                 @NotNull GradleSyncStats.Trigger trigger,
                                 @Nullable GradleSyncListener listener) {
    Request request = new Request(trigger);
    requestProjectSync(project, request, listener);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  @Override public void requestProjectSync(@NotNull Project project, @NotNull Request request) {
    requestProjectSync(project, request, null);
  }

  /**
   * This method should not be called within a {@link DumbModeTask}, the platform will take care of ensuring that
   * sync is not run at the same time as indexing.
   */
  @Override public void requestProjectSync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    if (GradleSyncState.getInstance(project).isSyncInProgress()) {
      return;
    }
    //noinspection deprecation
    if (GradleBuildInvoker.getInstance(project).getInternalIsBuildRunning()) {
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

  private static boolean prepareProject(@NotNull Project project, @Nullable GradleSyncListener listener) {
    GradleProjectInfo projectInfo = GradleProjectInfo.getInstance(project);
    if (ProjectSystemUtil.requiresAndroidModel(project) || projectInfo.hasTopLevelGradleFile()) {
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

  @Override@WorkerThread
  public void fetchAndMergeNativeVariants(@NotNull Project project,
                                          @NotNull Set<@NotNull String> requestedAbis) {
    new GradleSyncExecutor(project).fetchAndMergeNativeVariants(requestedAbis);
  }

  @Override@WorkerThread
  @NotNull
  public List<GradleModuleModels> fetchGradleModels(@NotNull Project project) {
    return new GradleSyncExecutor(project).fetchGradleModels();
  }
}

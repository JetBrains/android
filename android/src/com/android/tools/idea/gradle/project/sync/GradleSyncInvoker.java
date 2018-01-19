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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleTasksExecutor;
import com.android.tools.idea.gradle.project.importing.OpenMigrationToGradleUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.cleanup.PreSyncProjectCleanUp;
import com.android.tools.idea.gradle.project.sync.idea.IdeaGradleSync;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult;
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncChecks;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.base.Objects;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.util.GradleProjects.setSyncRequestedDuringBuild;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.clearStoredGradleJvmArgs;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.*;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.IN_BACKGROUND_ASYNC;
import static com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.MODAL_SYNC;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.ensureToolWindowContentInitialized;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;

public class GradleSyncInvoker {
  @NotNull private final FileDocumentManager myFileDocumentManager;
  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final GradleSyncFailureHandler mySyncFailureHandler;
  @NotNull private final PreSyncProjectCleanUp myPreSyncProjectCleanUp;
  @NotNull private final PreSyncChecks myPreSyncChecks;

  @NotNull
  public static GradleSyncInvoker getInstance() {
    return ServiceManager.getService(GradleSyncInvoker.class);
  }

  public GradleSyncInvoker(@NotNull FileDocumentManager fileDocumentManager,
                           @NotNull IdeInfo ideInfo,
                           @NotNull GradleSyncFailureHandler syncFailureHandler) {
    this(fileDocumentManager, ideInfo, syncFailureHandler, new PreSyncProjectCleanUp(), new PreSyncChecks());
  }

  private GradleSyncInvoker(@NotNull FileDocumentManager fileDocumentManager,
                            @NotNull IdeInfo ideInfo,
                            @NotNull GradleSyncFailureHandler syncFailureHandler,
                            @NotNull PreSyncProjectCleanUp preSyncProjectCleanUp,
                            @NotNull PreSyncChecks preSyncChecks) {
    myFileDocumentManager = fileDocumentManager;
    myIdeInfo = ideInfo;
    mySyncFailureHandler = syncFailureHandler;
    myPreSyncProjectCleanUp = preSyncProjectCleanUp;
    myPreSyncChecks = preSyncChecks;
  }

  public void requestProjectSyncAndSourceGeneration(@NotNull Project project,
                                                    @NotNull GradleSyncStats.Trigger trigger) {
    requestProjectSyncAndSourceGeneration(project, trigger, null);
  }

  public void requestProjectSyncAndSourceGeneration(@NotNull Project project,
                                                    @NotNull GradleSyncStats.Trigger trigger,
                                                    @Nullable GradleSyncListener listener) {
    Request request = new Request(trigger);
    requestProjectSync(project, request, listener);
  }

  public void requestProjectSync(@NotNull Project project, @NotNull Request request) {
    requestProjectSync(project, request, null);
  }

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
      try {
        if (prepareProject(project, listener)) {
          sync(project, request, listener);
        }
      }
      catch (ConfigurationException e) {
        showErrorDialog(project, e.getMessage(), e.getTitle());
      }
    };

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      application.invokeAndWait(syncTask);
      return;
    }
    if (request.runInBackground) {
      TransactionGuard.getInstance().submitTransactionLater(project, syncTask);
    }
    else {
      TransactionGuard.getInstance().submitTransactionAndWait(syncTask);
    }
  }

  private static boolean isBuildInProgress(@NotNull Project project) {
    IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(project);
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

  private boolean prepareProject(@NotNull Project project, @Nullable GradleSyncListener listener)
    throws ConfigurationException {
    if (AndroidProjectInfo.getInstance(project).requiresAndroidModel() || hasTopLevelGradleBuildFile(project)) {
      boolean isImportedProject = GradleProjectInfo.getInstance(project).isImportedProject();
      if (!isImportedProject) {
        myFileDocumentManager.saveAllDocuments();
      }
      return true; // continue with sync.
    }
    invokeLaterIfProjectAlive(project, () -> {
      String msg = String.format("The project '%s' is not a Gradle-based project", project.getName());
      AndroidNotification.getInstance(project).showBalloon("Project Sync", msg, ERROR, new OpenMigrationToGradleUrlHyperlink());

      if (listener != null) {
        listener.syncFailed(project, msg);
      }
    });
    return false; // stop sync.
  }

  private static boolean hasTopLevelGradleBuildFile(@NotNull Project project) {
    String projectFolderPath = project.getBasePath();
    if (projectFolderPath != null) {
      File buildFile = new File(projectFolderPath, FN_BUILD_GRADLE);
      return buildFile.isFile();
    }
    return false;
  }

  private void sync(@NotNull Project project, @NotNull Request request, @Nullable GradleSyncListener listener) {
    if (myIdeInfo.isAndroidStudio()) {
      // See https://code.google.com/p/android/issues/detail?id=169743
      // TODO move this method out of GradleUtil.
      clearStoredGradleJvmArgs(project);
    }

    PreSyncCheckResult checkResult = myPreSyncChecks.canSync(project);
    if (!checkResult.isSuccess()) {
      // User should have already warned that something is not right and sync cannot continue.
      String cause = nullToEmpty(checkResult.getFailureCause());
      handlePreSyncCheckFailure(project, cause, listener, request);
      return;
    }

    // Do clean up tasks before calling sync started.
    // During clean up, we might change some gradle files, for example, gradle property files based on http settings, gradle wrappers and etc.
    // And any changes to gradle files after sync started will result in another sync needed.
    myPreSyncProjectCleanUp.cleanUp(project);

    // We only update UI on sync when re-importing projects. By "updating UI" we mean updating the "Build Variants" tool window and editor
    // notifications.  It is not safe to do this for new projects because the new project has not been opened yet.
    boolean isImportedProject = GradleProjectInfo.getInstance(project).isImportedProject();
    boolean started;
    if (request.useCachedGradleModels) {
      started = GradleSyncState.getInstance(project).skippedSyncStarted(!isImportedProject, request);
    }
    else {
      started = GradleSyncState.getInstance(project).syncStarted(!isImportedProject, request);
    }
    if (!started) {
      return;
    }

    if (listener != null) {
      listener.syncStarted(project, request.useCachedGradleModels, request.generateSourcesOnSuccess);
    }

    boolean useNewGradleSync = NewGradleSync.isEnabled();
    if (!useNewGradleSync) {
      removeAndroidModels(project);
    }

    GradleSync gradleSync = useNewGradleSync ? new NewGradleSync(project) : new IdeaGradleSync(project);
    gradleSync.sync(request, listener);
  }

  private void handlePreSyncCheckFailure(@NotNull Project project,
                                         @NotNull String failureCause,
                                         @Nullable GradleSyncListener syncListener,
                                         @NotNull GradleSyncInvoker.Request request) {
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (syncState.syncStarted(true, request)) {
      if (syncListener != null) {
        syncListener.syncStarted(project, request.useCachedGradleModels, request.generateSourcesOnSuccess);
      }
      mySyncFailureHandler.createTopLevelModelAndOpenProject(project);
      syncState.syncFailed(failureCause);
      if (syncListener != null) {
        syncListener.syncFailed(project, failureCause);
      }
    }
  }

  // See issue: https://code.google.com/p/android/issues/detail?id=64508
  private static void removeAndroidModels(@NotNull Project project) {
    // Remove all Android models from module. Otherwise, if re-import/sync fails, editors will not show the proper notification of the
    // failure.
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        facet.getConfiguration().setModel(null);
      }
    }
  }

  public static class Request {
    public final GradleSyncStats.Trigger trigger;

    public boolean runInBackground = true;
    public boolean generateSourcesOnSuccess = true;
    public boolean cleanProject;
    public boolean useCachedGradleModels;
    public boolean skipAndroidPluginUpgrade;

    @NotNull
    public static Request projectLoaded() {
      return new Request(TRIGGER_PROJECT_LOADED);
    }

    @NotNull
    public static Request projectModified() {
      return new Request(TRIGGER_PROJECT_MODIFIED);
    }

    @NotNull
    public static Request userRequest() {
      return new Request(TRIGGER_USER_REQUEST);
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
      return runInBackground == request.runInBackground &&
             cleanProject == request.cleanProject &&
             generateSourcesOnSuccess == request.generateSourcesOnSuccess &&
             useCachedGradleModels == request.useCachedGradleModels &&
             trigger == request.trigger;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(runInBackground, cleanProject, generateSourcesOnSuccess, useCachedGradleModels, trigger);
    }

    @Override
    public String toString() {
      return "RequestSettings{" +
             "myRunInBackground=" + runInBackground +
             ", myCleanProject=" + cleanProject +
             ", myGenerateSourcesOnSuccess=" + generateSourcesOnSuccess +
             ", myUseCachedGradleModels=" + useCachedGradleModels +
             ", myTrigger=" + trigger +
             '}';
    }
  }
}

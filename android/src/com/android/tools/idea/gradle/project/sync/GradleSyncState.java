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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.*;
import static com.intellij.openapi.ui.MessageType.ERROR;
import static com.intellij.openapi.ui.MessageType.INFO;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;

public class GradleSyncState {
  public static final Key<Boolean> PROJECT_EXTERNAL_BUILD_FILES_CHANGED = Key.create("android.gradle.project.external.build.files.changed");

  private static final Logger LOG = Logger.getInstance(GradleSyncState.class);
  private static final NotificationGroup LOGGING_NOTIFICATION = NotificationGroup.logOnlyGroup("Gradle sync");

  @VisibleForTesting
  static final Topic<GradleSyncListener> GRADLE_SYNC_TOPIC = new Topic<>("Project sync with Gradle", GradleSyncListener.class);

  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final MessageBus myMessageBus;
  @NotNull private final StateChangeNotification myChangeNotification;
  @NotNull private final GradleSyncSummary mySummary;

  private final Object myLock = new Object();

  @GuardedBy("myLock")
  private boolean mySyncNotificationsEnabled;

  @GuardedBy("myLock")
  private boolean mySyncInProgress;

  @NotNull
  public static MessageBusConnection subscribe(@NotNull Project project, @NotNull GradleSyncListener listener) {
    return subscribe(project, listener, project);
  }

  @NotNull
  public static MessageBusConnection subscribe(@NotNull Project project,
                                               @NotNull GradleSyncListener listener,
                                               @NotNull Disposable parentDisposable) {
    MessageBusConnection connection = project.getMessageBus().connect(parentDisposable);
    connection.subscribe(GRADLE_SYNC_TOPIC, listener);
    return connection;
  }

  @NotNull
  public static GradleSyncState getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSyncState.class);
  }

  public GradleSyncState(@NotNull Project project, @NotNull GradleProjectInfo gradleProjectInfo, @NotNull MessageBus messageBus) {
    this(project, gradleProjectInfo, messageBus, new StateChangeNotification(project), new GradleSyncSummary(project));
  }

  @VisibleForTesting
  GradleSyncState(@NotNull Project project,
                  @NotNull GradleProjectInfo gradleProjectInfo,
                  @NotNull MessageBus messageBus,
                  @NotNull StateChangeNotification changeNotification,
                  @NotNull GradleSyncSummary summary) {
    myProject = project;
    myGradleProjectInfo = gradleProjectInfo;
    myMessageBus = messageBus;
    myChangeNotification = changeNotification;
    mySummary = summary;
  }

  public boolean areSyncNotificationsEnabled() {
    synchronized (myLock) {
      return mySyncNotificationsEnabled;
    }
  }

  /**
   * Notification that a sync has started.
   *
   * @param notifyUser indicates whether the user should be notified.
   * @return {@code true} if there another sync is not already in progress and this sync request can continue; {@code false} if the
   * current request cannot continue because there is already one in progress.
   */
  public boolean syncStarted(boolean notifyUser) {
    synchronized (myLock) {
      if (mySyncInProgress) {
        LOG.info(String.format("Sync already in progress for project '%1$s'.", myProject.getName()));
        return false;
      }
      mySyncInProgress = true;
    }
    LOG.info(String.format("Started sync with Gradle for project '%1$s'.", myProject.getName()));

    addInfoToEventLog("Gradle sync started");

    if (notifyUser) {
      notifyStateChanged();
    }

    mySummary.reset();
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncStarted(myProject));

    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder().setCategory(GRADLE_SYNC).setKind(GRADLE_SYNC_STARTED);
    UsageTracker.getInstance().log(event);

    return true;
  }

  public void syncSkipped(long lastSyncTimestamp) {
    LOG.info(String.format("Skipped sync with Gradle for project '%1$s'. Project state loaded from cache.", myProject.getName()));

    stopSyncInProgress();
    addInfoToEventLog("Gradle sync completed");
    mySummary.setSyncTimestamp(lastSyncTimestamp);
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSkipped(myProject));

    enableNotifications();

    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder().setCategory(GRADLE_SYNC).setKind(GRADLE_SYNC_SKIPPED);
    UsageTracker.getInstance().log(event);
  }

  public void invalidateLastSync(@NotNull String error) {
    syncFailed(error);
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        facet.setAndroidModel(null);
      }
    }
  }

  public void syncFailed(@NotNull String message) {
    LOG.info(String.format("Sync with Gradle for project '%1$s' failed: %2$s", myProject.getName(), message));

    String logMsg = "Gradle sync failed";
    if (isNotEmpty(message)) {
      logMsg += String.format(": %1$s", message);
    }
    addToEventLog(logMsg, ERROR);

    syncFinished();
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncFailed(myProject, message));

    mySummary.setSyncErrorsFound(true);

    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder().setCategory(GRADLE_SYNC).setKind(GRADLE_SYNC_FAILURE);
    UsageTracker.getInstance().log(event);
  }

  public void syncEnded() {
    LOG.info(String.format("Sync with Gradle successful for project '%1$s'.", myProject.getName()));

    addInfoToEventLog("Gradle sync completed");

    // Temporary: Clear resourcePrefix flag in case it was set to false when working with
    // an older model. TODO: Remove this when we no longer support models older than 0.10.
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    LintUtils.sTryPrefixLookup = true;

    syncFinished();
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSucceeded(myProject));

    GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(myProject);
    String gradleVersionString = gradleVersion != null ? gradleVersion.toString() : "";

    // @formatter:off
    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder().setCategory(GRADLE_SYNC)
                                                                      .setKind(GRADLE_SYNC_ENDED)
                                                                      .setGradleVersion(gradleVersionString);
    // @formatter:on
    UsageTracker.getInstance().log(event);
  }

  private void addInfoToEventLog(@NotNull String message) {
    addToEventLog(message, INFO);
  }

  private void addToEventLog(@NotNull String message, @NotNull MessageType type) {
    LOGGING_NOTIFICATION.createNotification(message, type).notify(myProject);
  }

  private void syncFinished() {
    stopSyncInProgress();
    mySummary.setSyncTimestamp(System.currentTimeMillis());
    enableNotifications();
    notifyStateChanged();
  }

  private void stopSyncInProgress() {
    synchronized (myLock) {
      mySyncInProgress = false;
    }
  }

  private void syncPublisher(@NotNull Runnable publishingTask) {
    invokeLaterIfProjectAlive(myProject, publishingTask);
  }

  private void enableNotifications() {
    synchronized (myLock) {
      mySyncNotificationsEnabled = true;
    }
  }

  public void notifyStateChanged() {
    myChangeNotification.notifyStateChanged();
  }

  public boolean lastSyncFailedOrHasIssues() {
    // This will be true if sync failed because of an exception thrown by Gradle. GradleSyncState will know that sync stopped.
    boolean lastSyncFailed = lastSyncFailed();

    // This will be true if sync was successful but there were sync issues found (e.g. unresolved dependencies.)
    // GradleSyncState still thinks that sync is still being executed.
    boolean hasSyncErrors = mySummary.hasSyncErrors();

    return lastSyncFailed || hasSyncErrors;
  }

  /**
   * Indicates whether the last Gradle sync failed. This method returns {@code false} if there is a sync task is currently running.
   * <p>
   * Possible failure causes:
   * <ul>
   * <li>An error occurred in Gradle (e.g. a missing dependency, or a missing Android platform in the SDK)</li>
   * <li>An error occurred while setting up a project using the models obtained from Gradle during sync (e.g. invoking a method that
   * doesn't exist in an old version of the Android plugin)</li>
   * <li>An error in the structure of the project after sync (e.g. more than one module with the same path in the file system)</li>
   * </ul>
   * </p>
   *
   * @return {@code true} if the last Gradle sync failed; {@code false} if the last sync was successful or if there is a sync task
   * currently running.
   */
  public boolean lastSyncFailed() {
    return !isSyncInProgress() &&
           myGradleProjectInfo.isBuildWithGradle() &&
           (requiredAndroidModelMissing(myProject) || mySummary.hasSyncErrors());
  }

  public boolean isSyncInProgress() {
    synchronized (myLock) {
      return mySyncInProgress;
    }
  }

  /**
   * Indicates whether a project sync with Gradle is needed. A Gradle sync is usually needed when a build.gradle or settings.gradle file has
   * been updated <b>after</b> the last project sync was performed.
   *
   * @return {@code YES} if a sync with Gradle is needed, {@code FALSE} otherwise, or {@code UNSURE} If the timestamp of the last Gradle
   * sync cannot be found.
   */
  @NotNull
  public ThreeState isSyncNeeded() {
    long lastSync = mySummary.getSyncTimestamp();
    if (lastSync < 0) {
      // Previous sync may have failed. We don't know if a sync is needed or not. Let client code decide.
      return ThreeState.UNSURE;
    }
    return isSyncNeeded(lastSync) ? ThreeState.YES : ThreeState.NO;
  }

  /**
   * Indicates whether a project sync with Gradle is needed if changes to build.gradle or settings.gradle files were made after the given
   * time.
   *
   * @param referenceTimeInMillis the given time, in milliseconds.
   * @return {@code true} if a sync with Gradle is needed, {@code false} otherwise.
   * @throws AssertionError if the given time is less than or equal to zero.
   */
  private boolean isSyncNeeded(long referenceTimeInMillis) {
    myProject.putUserData(PROJECT_EXTERNAL_BUILD_FILES_CHANGED, null);

    assert referenceTimeInMillis > 0;
    if (isSyncInProgress()) {
      return false;
    }

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    File settingsFilePath = new File(getBaseDirPath(myProject), FN_SETTINGS_GRADLE);
    if (settingsFilePath.exists()) {
      VirtualFile settingsFile = findFileByIoFile(settingsFilePath, true);
      if (settingsFile != null && fileDocumentManager.isFileModified(settingsFile)) {
        return true;
      }
      if (settingsFilePath.lastModified() > referenceTimeInMillis) {
        return true;
      }
    }

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      VirtualFile buildFile = getGradleBuildFile(module);
      if (buildFile != null) {
        if (fileDocumentManager.isFileModified(buildFile)) {
          return true;
        }

        File buildFilePath = virtualToIoFile(buildFile);
        if (buildFilePath.lastModified() > referenceTimeInMillis) {
          return true;
        }
      }

      NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
      if (ndkModuleModel != null) {
        for (File externalBuildFile : ndkModuleModel.getAndroidProject().getBuildFiles()) {
          VirtualFile virtualFile = findFileByIoFile(externalBuildFile, true);
          if ((virtualFile != null && fileDocumentManager.isFileModified(virtualFile)) ||
              externalBuildFile.lastModified() > referenceTimeInMillis) {
            myProject.putUserData(PROJECT_EXTERNAL_BUILD_FILES_CHANGED, true);
            return true;
          }
        }
      }
    }

    return false;
  }

  @NotNull
  public GradleSyncSummary getSummary() {
    return mySummary;
  }

  public void setupStarted() {
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).setupStarted(myProject));
    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder().setCategory(GRADLE_SYNC).setKind(GRADLE_SYNC_SETUP_STARTED);
    UsageTracker.getInstance().log(event);
  }

  @VisibleForTesting
  static class StateChangeNotification {
    @NotNull private final Project myProject;

    StateChangeNotification(@NotNull Project project) {
      myProject = project;
    }

    void notifyStateChanged() {
      invokeLaterIfProjectAlive(myProject, () -> {
        EditorNotifications notifications = EditorNotifications.getInstance(myProject);
        VirtualFile[] files = FileEditorManager.getInstance(myProject).getOpenFiles();
        for (VirtualFile file : files) {
          try {
            notifications.updateNotifications(file);
          }
          catch (Throwable e) {
            String filePath = toSystemDependentName(file.getPath());
            String msg = String.format("Failed to update editor notifications for file '%1$s'", filePath);
            LOG.info(msg, e);
          }
        }

        BuildVariantView.getInstance(myProject).updateContents();
      });
    }
  }
}

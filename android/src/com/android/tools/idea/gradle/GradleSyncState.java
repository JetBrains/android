/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.android.tools.idea.stats.UsageTracker;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.collect.Lists;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.stats.UsageTracker.*;
import static com.intellij.openapi.options.Configurable.PROJECT_CONFIGURABLE;
import static com.intellij.openapi.ui.MessageType.ERROR;
import static com.intellij.openapi.ui.MessageType.INFO;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;

public class GradleSyncState {
  private static final Logger LOG = Logger.getInstance(GradleSyncState.class);
  private static final NotificationGroup LOGGING_NOTIFICATION = NotificationGroup.logOnlyGroup("Gradle sync");

  private static final List<String> PROJECT_PREFERENCES_TO_REMOVE = Lists.newArrayList(
    "org.intellij.lang.xpath.xslt.associations.impl.FileAssociationsConfigurable", "com.intellij.uiDesigner.GuiDesignerConfigurable",
    "org.jetbrains.plugins.groovy.gant.GantConfigurable", "org.jetbrains.plugins.groovy.compiler.GroovyCompilerConfigurable",
    "org.jetbrains.android.compiler.AndroidDexCompilerSettingsConfigurable", "org.jetbrains.idea.maven.utils.MavenSettings",
    "com.intellij.compiler.options.CompilerConfigurable"
  );

  private static final Topic<GradleSyncListener> GRADLE_SYNC_TOPIC = new Topic<>("Project sync with Gradle", GradleSyncListener.class);
  private static final Key<Long> PROJECT_LAST_SYNC_TIMESTAMP_KEY = Key.create("android.gradle.project.last.sync.timestamp");

  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;

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

  public GradleSyncState(@NotNull Project project, @NotNull MessageBus messageBus) {
    myProject = project;
    myMessageBus = messageBus;
  }

  public boolean areSyncNotificationsEnabled() {
    synchronized (myLock) {
      return mySyncNotificationsEnabled;
    }
  }

  public void syncSkipped(long lastSyncTimestamp) {
    LOG.info(String.format("Skipped sync with Gradle for project '%1$s'. Data model(s) loaded from cache.", myProject.getName()));

    cleanUpProjectPreferences();
    setLastGradleSyncTimestamp(lastSyncTimestamp);
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSkipped(myProject));

    enableNotifications();
    trackSyncEvent(ACTION_GRADLE_SYNC_SKIPPED);
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

    cleanUpProjectPreferences();
    if (notifyUser) {
      notifyUser();
    }
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncStarted(myProject));

    trackSyncEvent(ACTION_GRADLE_SYNC_STARTED);

    return true;
  }

  public void syncFailed(@NotNull final String message) {
    LOG.info(String.format("Sync with Gradle for project '%1$s' failed: %2$s", myProject.getName(), message));

    String logMsg = "Gradle sync failed";
    if (isNotEmpty(message)) {
      logMsg += String.format(": %1$s", message);
    }
    addToEventLog(logMsg, ERROR);

    syncFinished();
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncFailed(myProject, message));

    trackSyncEvent(ACTION_GRADLE_SYNC_FAILED);
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

    GradleVersion gradleVersion = getGradleVersion(myProject);
    if (gradleVersion != null) {
      trackSyncEvent(ACTION_GRADLE_VERSION, gradleVersion.toString());
    }

    trackSyncEvent(ACTION_GRADLE_SYNC_ENDED);
  }

  private static void trackSyncEvent(@NotNull String event) {
    trackSyncEvent(event, null);
  }

  private static void trackSyncEvent(@NotNull String event, @Nullable String extraInfo) {
    UsageTracker.getInstance().trackEvent(CATEGORY_GRADLE, event, extraInfo, null);
  }

  private void addInfoToEventLog(@NotNull String message) {
    addToEventLog(message, INFO);
  }

  private void addToEventLog(@NotNull String message, @NotNull MessageType type) {
    LOGGING_NOTIFICATION.createNotification(message, type).notify(myProject);
  }

  private void syncFinished() {
    synchronized (myLock) {
      mySyncInProgress = false;
    }
    setLastGradleSyncTimestamp(System.currentTimeMillis());
    enableNotifications();
    notifyUser();
  }

  private void syncPublisher(@NotNull Runnable publishingTask) {
    invokeLaterIfProjectAlive(myProject, publishingTask);
  }

  private void enableNotifications() {
    synchronized (myLock) {
      mySyncNotificationsEnabled = true;
    }
  }

  public void notifyUser() {
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

  public boolean isSyncInProgress() {
    synchronized (myLock) {
      return mySyncInProgress;
    }
  }

  private void setLastGradleSyncTimestamp(long timestamp) {
    myProject.putUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY, timestamp);
  }

  public long getLastGradleSyncTimestamp() {
    Long timestamp = myProject.getUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY);
    return timestamp != null ? timestamp.longValue() : -1L;
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
    long lastSync = getLastGradleSyncTimestamp();
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
    }
    return false;
  }

  private void cleanUpProjectPreferences() {
    if (!AndroidStudioInitializer.isAndroidStudio()) {
      return;
    }
    try {
      ExtensionPoint<ConfigurableEP<Configurable>> projectConfigurable =
        Extensions.getArea(myProject).getExtensionPoint(PROJECT_CONFIGURABLE);

      cleanUpPreferences(projectConfigurable, PROJECT_PREFERENCES_TO_REMOVE);
    }
    catch (Throwable e) {
      String msg = String.format("Failed to clean up preferences for project '%1$s'", myProject.getName());
      LOG.info(msg, e);
    }
  }
}

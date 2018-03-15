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

import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.settings.AndroidStudioGradleIdeSettings;
import com.android.tools.idea.gradle.project.sync.projectsystem.GradleSyncResultPublisher;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.project.IndexingSuspender;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Ordering;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.google.wireless.android.sdk.stats.KotlinSupport;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.*;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_UNKNOWN;
import static com.intellij.openapi.ui.MessageType.ERROR;
import static com.intellij.openapi.ui.MessageType.INFO;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.formatDuration;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;

public class GradleSyncState {
  private static final Logger LOG = Logger.getInstance(GradleSyncState.class);
  private static final NotificationGroup LOGGING_NOTIFICATION = NotificationGroup.logOnlyGroup("Gradle sync");

  @VisibleForTesting
  static final Topic<GradleSyncListener> GRADLE_SYNC_TOPIC = new Topic<>("Project sync with Gradle", GradleSyncListener.class);

  @NotNull private final Project myProject;
  @NotNull private final AndroidProjectInfo myAndroidProjectInfo;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final MessageBus myMessageBus;
  @NotNull private final StateChangeNotification myChangeNotification;
  @NotNull private final GradleSyncSummary mySummary;
  @NotNull private final GradleFiles myGradleFiles;
  @NotNull private final ProjectStructure myProjectStructure;

  @NotNull private final Object myLock = new Object();

  @GuardedBy("myLock")
  private boolean mySyncNotificationsEnabled;

  @GuardedBy("myLock")
  private boolean mySyncSkipped;

  @GuardedBy("myLock")
  private boolean mySyncInProgress;

  // Negative numbers mean that the events have not finished
  private long mySyncStartedTimestamp = -1L;
  private long mySyncSetupStartedTimeStamp = -1L;
  private long mySyncEndedTimeStamp = -1L;
  private long mySyncFailedTimeStamp = -1L;
  private GradleSyncStats.Trigger myTrigger = TRIGGER_UNKNOWN;

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

  public GradleSyncState(@NotNull Project project,
                         @NotNull AndroidProjectInfo androidProjectInfo,
                         @NotNull GradleProjectInfo gradleProjectInfo,
                         @NotNull GradleFiles gradleFiles,
                         @NotNull MessageBus messageBus,
                         @NotNull ProjectStructure projectStructure) {
    this(project, androidProjectInfo, gradleProjectInfo, gradleFiles, messageBus, projectStructure, new StateChangeNotification(project),
         new GradleSyncSummary(project));
  }

  @VisibleForTesting
  GradleSyncState(@NotNull Project project,
                  @NotNull AndroidProjectInfo androidProjectInfo,
                  @NotNull GradleProjectInfo gradleProjectInfo,
                  @NotNull GradleFiles gradleFiles,
                  @NotNull MessageBus messageBus,
                  @NotNull ProjectStructure projectStructure,
                  @NotNull StateChangeNotification changeNotification,
                  @NotNull GradleSyncSummary summary) {
    myProject = project;
    myAndroidProjectInfo = androidProjectInfo;
    myGradleProjectInfo = gradleProjectInfo;
    myMessageBus = messageBus;
    myChangeNotification = changeNotification;
    mySummary = summary;
    myGradleFiles = gradleFiles;
    myProjectStructure = projectStructure;

    // Call in to make sure IndexingSuspender instance is constructed.
    IndexingSuspender.ensureInitialised(myProject);
  }

  public boolean areSyncNotificationsEnabled() {
    synchronized (myLock) {
      return mySyncNotificationsEnabled;
    }
  }

  /**
   * Notification that a sync has started. It is considered "skipped" because, instead of obtaining the project models from Gradle, "sync"
   * uses the models cached in disk.
   *
   * @param notifyUser indicates whether the user should be notified.
   * @param request    the request which initiated the sync.
   * @return {@code true} if there another sync is not already in progress and this sync request can continue; {@code false} if the
   * current request cannot continue because there is already one in progress.
   */
  public boolean skippedSyncStarted(boolean notifyUser,  @NotNull GradleSyncInvoker.Request request) {
    return syncStarted(true, notifyUser, request);
  }

  /**
   * Notification that a sync has started.
   *
   * @param notifyUser indicates whether the user should be notified.
   * @param request    the request which initiated the sync.
   * @return {@code true} if there another sync is not already in progress and this sync request can continue; {@code false} if the
   * current request cannot continue because there is already one in progress.
   */
  public boolean syncStarted(boolean notifyUser, @NotNull GradleSyncInvoker.Request request) {
    return syncStarted(false, notifyUser, request);
  }

  private boolean syncStarted(boolean syncSkipped, boolean notifyUser, @NotNull GradleSyncInvoker.Request request) {
    synchronized (myLock) {
      if (mySyncInProgress) {
        LOG.info(String.format("Sync already in progress for project '%1$s'.", myProject.getName()));
        return false;
      }
      mySyncSkipped = syncSkipped;
      mySyncInProgress = true;
    }

    LOG.info(String.format("Started sync with Gradle for project '%1$s'.", myProject.getName()));

    setSyncStartedTimeStamp(System.currentTimeMillis(), request.trigger);
    addInfoToEventLog("Gradle sync started");

    if (notifyUser) {
      notifyStateChanged();
    }

    if (mySummary.getSyncTimestamp() < 0) {
      // If this is the first Gradle sync for this project this session, make sure that GradleSyncResultPublisher
      // has been initialized so that it will begin broadcasting sync results on PROJECT_SYSTEM_SYNC_TOPIC.
      GradleSyncResultPublisher.getInstance(myProject);
    }

    mySummary.reset();
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncStarted(myProject, syncSkipped, request.generateSourcesOnSuccess));

    AndroidStudioEvent.Builder event = generateSyncEvent(GRADLE_SYNC_STARTED);
    UsageTracker.getInstance().log(event);

    return true;
  }

  @VisibleForTesting
  void setSyncStartedTimeStamp(long timeStampMs, GradleSyncStats.Trigger trigger) {
    mySyncStartedTimestamp = timeStampMs;
    mySyncSetupStartedTimeStamp = -1;
    mySyncEndedTimeStamp = -1;
    mySyncFailedTimeStamp = -1;
    myTrigger = trigger;
  }

  @VisibleForTesting
  void setSyncSetupStartedTimeStamp(long timeStampMs) {
    mySyncSetupStartedTimeStamp = timeStampMs;
  }

  @VisibleForTesting
  void setSyncEndedTimeStamp(long timeStampMs) {
    mySyncEndedTimeStamp = timeStampMs;
  }

  @VisibleForTesting
  void setSyncFailedTimeStamp(long timeStampMs) {
    mySyncFailedTimeStamp = timeStampMs;
  }

  public void syncSkipped(long lastSyncTimestamp) {
    long syncEndTimestamp = System.currentTimeMillis();
    setSyncEndedTimeStamp(syncEndTimestamp);
    String msg = String.format("Gradle sync finished in %1$s (from cached state)", getFormattedSyncDuration(syncEndTimestamp));
    addInfoToEventLog(msg);
    LOG.info(msg);

    stopSyncInProgress();
    mySummary.setSyncTimestamp(lastSyncTimestamp);
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSkipped(myProject));

    enableNotifications();

    AndroidStudioEvent.Builder event = generateSyncEvent(GRADLE_SYNC_SKIPPED);
    UsageTracker.getInstance().log(event);
  }

  public void invalidateLastSync(@NotNull String error) {
    syncFailed(error);
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        facet.getConfiguration().setModel(null);
      }
    }
  }

  public void syncFailed(@NotNull String message) {
    myProjectStructure.clearData();
    long syncEndTimestamp = System.currentTimeMillis();
    // If mySyncStartedTimestamp is -1, that means sync has not started or syncFailed has been called for this invocation.
    // Reset sync state and don't log the events or notify listener again.
    if (mySyncStartedTimestamp == -1L) {
      syncFinished(syncEndTimestamp);
      return;
    }
    setSyncFailedTimeStamp(syncEndTimestamp);
    String msg = "Gradle sync failed";
    if (isNotEmpty(message)) {
      msg += String.format(": %1$s", message);
    }
    msg += String.format(" (%1$s)", getFormattedSyncDuration(syncEndTimestamp));
    addToEventLog(msg, ERROR);
    LOG.info(msg);

    AndroidStudioEvent.Builder event = generateSyncEvent(GRADLE_SYNC_FAILURE);
    UsageTracker.getInstance().log(event);

    syncFinished(syncEndTimestamp);
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncFailed(myProject, message));

    mySummary.setSyncErrorsFound(true);
  }

  public void syncEnded() {
    // syncFailed should be called if there're any sync issues.
    assert !lastSyncFailedOrHasIssues();
    long syncEndTimestamp = System.currentTimeMillis();
    // If mySyncStartedTimestamp is -1, that means sync has not started or syncEnded has been called for this invocation.
    // Reset sync state and don't log the events or notify listener again.
    if (mySyncStartedTimestamp == -1L) {
      syncFinished(syncEndTimestamp);
      return;
    }
    setSyncEndedTimeStamp(syncEndTimestamp);
    String msg = String.format("Gradle sync finished in %1$s", getFormattedSyncDuration(syncEndTimestamp));
    addInfoToEventLog(msg);
    LOG.info(msg);

    // Temporary: Clear resourcePrefix flag in case it was set to false when working with
    // an older model. TODO: Remove this when we no longer support models older than 0.10.
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    LintUtils.sTryPrefixLookup = true;

    GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(myProject);
    String gradleVersionString = gradleVersion != null ? gradleVersion.toString() : "";

    AndroidStudioEvent.Builder event = generateSyncEvent(GRADLE_SYNC_ENDED)
      .setGradleVersion(gradleVersionString)
      .setKotlinSupport(generateKotlinSupportProto());
    UsageTracker.getInstance().log(event);

    syncFinished(syncEndTimestamp);
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSucceeded(myProject));
  }

  private long getSyncDurationMS(long syncEndTimestamp) {
    return syncEndTimestamp - mySyncStartedTimestamp;
  }

  @VisibleForTesting
  @NotNull
  String getFormattedSyncDuration(long syncEndTimestamp) {
    return formatDuration(getSyncDurationMS(syncEndTimestamp));
  }

  private void addInfoToEventLog(@NotNull String message) {
    addToEventLog(message, INFO);
  }

  private void addToEventLog(@NotNull String message, @NotNull MessageType type) {
    LOGGING_NOTIFICATION.createNotification(message, type).notify(myProject);
  }

  private void syncFinished(long timestamp) {
    stopSyncInProgress();
    mySyncStartedTimestamp = -1L;
    mySummary.setSyncTimestamp(timestamp);
    enableNotifications();
    notifyStateChanged();
  }

  private void stopSyncInProgress() {
    synchronized (myLock) {
      mySyncInProgress = false;
      mySyncSkipped = false;
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
           (myAndroidProjectInfo.requiredAndroidModelMissing() || mySummary.hasSyncErrors());
  }

  public boolean isSyncInProgress() {
    synchronized (myLock) {
      return mySyncInProgress;
    }
  }

  public boolean isSyncSkipped() {
    synchronized (myLock) {
      return mySyncSkipped;
    }
  }

  /**
   * Indicates whether a project sync with Gradle is needed. A Gradle sync is usually needed when a build.gradle or settings.gradle file has
   * been updated <b>after</b> the last project sync began.
   *
   * @return {@code YES} if a sync with Gradle is needed, {@code FALSE} otherwise, or {@code UNSURE} If the timestamp of the last Gradle
   * sync cannot be found.
   */
  @NotNull
  public ThreeState isSyncNeeded() {
    return myGradleFiles.areGradleFilesModified()? ThreeState.YES: ThreeState.NO;
  }

  @NotNull
  public GradleSyncSummary getSummary() {
    return mySummary;
  }

  public void setupStarted() {
    long syncSetupTimestamp = System.currentTimeMillis();
    setSyncSetupStartedTimeStamp(syncSetupTimestamp);
    addInfoToEventLog("Project setup started");
    LOG.info(String.format("Started setup of project '%1$s'.", myProject.getName()));
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).setupStarted(myProject));
    AndroidStudioEvent.Builder event = generateSyncEvent(GRADLE_SYNC_SETUP_STARTED);
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

  @NotNull
  private AndroidStudioEvent.Builder generateSyncEvent(@NotNull AndroidStudioEvent.EventKind kind) {
    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
    GradleSyncStats.Builder syncStats = GradleSyncStats.newBuilder();
    // @formatter:off
    syncStats.setTotalTimeMs(getSyncTotalTimeMs())
             .setIdeTimeMs(getSyncIdeTimeMs())
             .setGradleTimeMs(getSyncGradleTimeMs())
             .setTrigger(myTrigger)
             .setEmbeddedRepoEnabled(AndroidStudioGradleIdeSettings.getInstance().isEmbeddedMavenRepoEnabled());
    // @formatter:on
    event.setCategory(GRADLE_SYNC).setKind(kind).setGradleSyncStats(syncStats);
    return event;
  }

  @NotNull
  private KotlinSupport.Builder generateKotlinSupportProto() {

    Ordering<GradleVersion> ordering = Ordering.natural().nullsFirst();
    GradleVersion kotlinVersion = null;
    GradleVersion ktxVersion = null;

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model == null) {
        continue;
      }

      IdeDependencies dependencies = model.getSelectedMainCompileLevel2Dependencies();

      kotlinVersion = ordering.max(kotlinVersion, findVersion("org.jetbrains.kotlin:kotlin-stdlib", dependencies.getJavaLibraries()));
      ktxVersion = ordering.max(ktxVersion, findVersion("androidx.core:core-ktx", dependencies.getAndroidLibraries()));
    }

    KotlinSupport.Builder result = KotlinSupport.newBuilder();
    if (kotlinVersion != null) {
      result.setKotlinSupportVersion(kotlinVersion.toString());
    }
    if (ktxVersion != null) {
      result.setAndroidKtxVersion(ktxVersion.toString());
    }
    return result;
  }

  @Nullable
  private static GradleVersion findVersion(@NotNull String artifact, @NotNull Iterable<Library> libraries) {
    for (Library library : libraries) {
      String coordinateString = library.getArtifactAddress();
      if (coordinateString.startsWith(artifact)) {
        GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateString);
        if (coordinate == null) {
          return null;
        }

        return coordinate.getVersion();
      }
    }

    return null;
  }


  @VisibleForTesting
  long getSyncTotalTimeMs() {
    if (mySyncEndedTimeStamp >= 0) {
      // Sync was successful
      return mySyncEndedTimeStamp - mySyncStartedTimestamp;
    }
    if (mySyncFailedTimeStamp >= 0) {
      // Sync failed
      return mySyncFailedTimeStamp - mySyncStartedTimestamp;
    }
    // If more sync steps are added, they should be checked in reverse order
    if (mySyncSetupStartedTimeStamp >= 0) {
      // Only Gradle part has finished
      return mySyncSetupStartedTimeStamp - mySyncStartedTimestamp;
    }
    // Nothing has finished yet
    return 0;
  }

  @VisibleForTesting
  long getSyncIdeTimeMs() {
    if (mySyncEndedTimeStamp >= 0) {
      // Sync finished
      if (mySyncSetupStartedTimeStamp >= 0) {
        return mySyncEndedTimeStamp - mySyncSetupStartedTimeStamp;
      }
      // Sync was done from cache (no gradle nor IDE part was done)
      return -1;
    }
    // Since Ide part is the last one, it did not start or it failed
    return -1;
  }

  @VisibleForTesting
  long getSyncGradleTimeMs() {
    if (mySyncSetupStartedTimeStamp >= 0) {
      return mySyncSetupStartedTimeStamp - mySyncStartedTimestamp;
    }
    // Gradle part has not been done
    return -1;
  }
}

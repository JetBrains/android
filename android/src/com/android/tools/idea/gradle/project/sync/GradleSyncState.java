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

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.DOT_KTS;
import static com.android.tools.idea.gradle.util.GradleUtil.getLastKnownAndroidGradlePluginVersion;
import static com.android.tools.idea.gradle.util.GradleUtil.getLastSuccessfulAndroidGradlePluginVersion;
import static com.android.tools.idea.gradle.util.GradleUtil.projectBuildFilesTypes;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_SETUP_STARTED;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_SKIPPED;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_UNKNOWN;
import static com.intellij.openapi.ui.MessageType.ERROR;
import static com.intellij.openapi.ui.MessageType.INFO;
import static com.intellij.openapi.ui.MessageType.WARNING;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.formatDuration;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive;

import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.UseJavaHomeAsJdkHyperlink;
import com.android.tools.idea.gradle.project.sync.ng.NewGradleSync;
import com.android.tools.idea.gradle.project.sync.projectsystem.GradleSyncResultPublisher;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.project.IndexingSuspender;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.android.tools.lint.detector.api.Lint;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Ordering;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.google.wireless.android.sdk.stats.KotlinSupport;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.event.HyperlinkEvent;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleSyncState {
  private static final Logger LOG = Logger.getInstance(GradleSyncState.class);
  private static final NotificationGroup SYNC_NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("Gradle sync");
  public static final NotificationGroup
    JDK_LOCATION_WARNING_NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("JDK Location different to JAVA_HOME");

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
  private long mySourceGenerationEndedTimeStamp = -1L;
  private long mySyncFailedTimeStamp = -1L;
  private GradleSyncStats.Trigger myTrigger = TRIGGER_UNKNOWN;
  private boolean myShouldRemoveModelsOnFailure = false;

  @GuardedBy("myLock")
  @Nullable private ExternalSystemTaskId myExternalSystemTaskId;

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
   * Notification that a sync has been requested.
   *
   * @param request Sync request
   * @return None
   */
  public void syncTaskCreated(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener syncListener) {
    if (syncListener != null) {
      syncListener.syncTaskCreated(myProject, request);
    }
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncTaskCreated(myProject, request));
  }

  /**
   * Notification that a sync has started.
   *
   * @param notifyUser   indicates whether the user should be notified.
   * @param request      the request which initiated the sync.
   * @param syncListener the listener for this particular sync that should be notified.
   * @return {@code true} if there another sync is not already in progress and this sync request can continue; {@code false} if the
   * current request cannot continue because there is already one in progress.
   */
  public boolean syncStarted(boolean notifyUser, @NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener syncListener) {
    synchronized (myLock) {
      if (mySyncInProgress) {
        LOG.info(String.format("Sync already in progress for project '%1$s'.", myProject.getName()));
        return false;
      }
      mySyncSkipped = request.useCachedGradleModels;
      mySyncInProgress = true;
    }
    myShouldRemoveModelsOnFailure = request.variantOnlySyncOptions == null;

    String syncType = NewGradleSync.isSingleVariantSync(myProject) ? "single-variant" : "IDEA";
    LOG.info(String.format("Started %1$s sync with Gradle for project '%2$s'.", syncType, myProject.getName()));

    setSyncStartedTimeStamp(System.currentTimeMillis(), request.trigger);
    addInfoToSyncEventLog(String.format("Gradle sync started with %1$s sync", syncType));

    if (notifyUser) {
      notifyStateChanged();
    }

    if (mySummary.getSyncTimestamp() < 0) {
      // If this is the first Gradle sync for this project this session, make sure that GradleSyncResultPublisher
      // has been initialized so that it will begin broadcasting sync results on PROJECT_SYSTEM_SYNC_TOPIC.
      GradleSyncResultPublisher.getInstance(myProject);
    }

    mySummary.reset();

    // Ensure that we don't notify the listeners until all the housekeeping has been performed in case they need to read
    // state about sync. First we notify the listener specifically for this sync, then we notify all the other registered
    // listeners.
    if (syncListener != null) {
      syncListener.syncStarted(myProject, request.generateSourcesOnSuccess);
    }
    syncPublisher(
      () -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC)
        .syncStarted(myProject, request.generateSourcesOnSuccess));

    logSyncEvent(GRADLE_SYNC_STARTED);
    return true;
  }

  @VisibleForTesting
  void setSyncStartedTimeStamp(long timeStampMs, GradleSyncStats.Trigger trigger) {
    mySyncStartedTimestamp = timeStampMs;
    mySyncSetupStartedTimeStamp = -1;
    mySyncEndedTimeStamp = -1;
    mySourceGenerationEndedTimeStamp = -1;
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
  void setSourceGenerationEndedTimeStamp(long timeStampMs) {
    mySourceGenerationEndedTimeStamp = timeStampMs;
  }

  @VisibleForTesting
  public boolean isSourceGenerationFinished() {
    return mySourceGenerationEndedTimeStamp != -1;
  }

  @VisibleForTesting
  void setSyncFailedTimeStamp(long timeStampMs) {
    mySyncFailedTimeStamp = timeStampMs;
  }

  public void syncSkipped(long lastSyncTimestamp, @Nullable GradleSyncListener syncListener) {
    long syncEndTimestamp = System.currentTimeMillis();
    setSyncEndedTimeStamp(syncEndTimestamp);
    String msg = String.format("Gradle sync finished in %1$s (from cached state)", getFormattedSyncDuration(syncEndTimestamp));
    addInfoToSyncEventLog(msg);
    LOG.info(msg);

    stopSyncInProgress();
    mySummary.setSyncTimestamp(lastSyncTimestamp);

    // Ensure that we don't notify the listeners until all the housekeeping has been performed in case they need to read
    // state about sync. First we notify the listener specifically for this sync, then we notify all the other registered
    // listeners.
    if (syncListener != null) {
      syncListener.syncSkipped(myProject);
    }
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncSkipped(myProject));

    enableNotifications();

    logSyncEvent(GRADLE_SYNC_SKIPPED);
  }

  /**
   * Notification that a sync has started.
   *
   * @param message      the failure message for this sync.
   * @param error        the throwable that caused sync to fail, null if none exist. This information is only used to log the exception.
   * @param syncListener the listener for this particular sync that should be notified.
   */
  public void syncFailed(@NotNull String message, @Nullable Throwable error, @Nullable GradleSyncListener syncListener) {
    myProjectStructure.clearData();
    long syncEndTimestamp = System.currentTimeMillis();
    // If mySyncStartedTimestamp is -1, that means sync has not started or syncFailed has been called for this invocation.
    // Reset sync state and don't log the events or notify listener again.
    if (mySyncStartedTimestamp == -1L) {
      syncFinished(syncEndTimestamp);
      return;
    }
    if (myShouldRemoveModelsOnFailure) {
      removeAndroidModels(myProject);
    }
    setSyncFailedTimeStamp(syncEndTimestamp);
    String msg = "Gradle sync failed";
    if (isNotEmpty(message)) {
      msg += String.format(": %1$s", message);
    }
    msg += String.format(" (%1$s)", getFormattedSyncDuration(syncEndTimestamp));
    addToSyncEventLog(msg, ERROR);
    LOG.warn(msg);

    // Log the error to ideas log
    // Note: we log this as well as message above so the stack trace is present in the logs.
    if (error != null) {
      LOG.warn(error);
    }

    // If we are in use tests also log to stdout to help debugging.
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      String toLog = (error == null) ? message : error.getMessage();
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("***** sync error: " + toLog);
    }

    logSyncEvent(GRADLE_SYNC_FAILURE);

    syncFinished(syncEndTimestamp);

    // Ensure that we don't notify the listeners until all the housekeeping has been performed in case they need to read
    // state about sync. First we notify the listener specifically for this sync, then we notify all the other registered
    // listeners.
    if (syncListener != null) {
      syncListener.syncFailed(myProject, message);
    }
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
    addInfoToSyncEventLog(msg);
    LOG.info(msg);

    // Temporary: Clear resourcePrefix flag in case it was set to false when working with
    // an older model. TODO: Remove this when we no longer support models older than 0.10.
    Lint.setTryPrefixLookup(true);

    logSyncEvent(GRADLE_SYNC_ENDED);

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

  private void addInfoToSyncEventLog(@NotNull String message) {
    addToSyncEventLog(message, INFO);
  }

  private void addToSyncEventLog(@NotNull String message, @NotNull MessageType type) {
    addToEventLog(SYNC_NOTIFICATION_GROUP, message, type, null);
  }

  private void addWarningToJdkEventLog(@NotNull String message, @Nullable List<NotificationHyperlink> quickFixes) {
    addToEventLog(JDK_LOCATION_WARNING_NOTIFICATION_GROUP, message, WARNING, quickFixes);
  }

  private void addToEventLog(@NotNull NotificationGroup notificationGroup,
                             @NotNull String message,
                             @NotNull MessageType type,
                             @Nullable List<NotificationHyperlink> quickFixes) {
    StringBuilder msg = new StringBuilder(message);
    NotificationListener listener = null;
    if (quickFixes != null) {
      for (NotificationHyperlink quickFix : quickFixes) {
        msg.append("\n").append(quickFix.toHtml());
      }
      listener = new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          for (NotificationHyperlink quickFix : quickFixes) {
            quickFix.executeIfClicked(myProject, event);
          }
        }
      };
    }
    notificationGroup.createNotification("", msg.toString(), type.toNotificationType(), listener).notify(myProject);
  }

  private void syncFinished(long timestamp) {
    stopSyncInProgress();
    mySyncStartedTimestamp = -1L;
    mySummary.setSyncTimestamp(timestamp);
    enableNotifications();
    notifyStateChanged();
    ApplicationManager.getApplication().invokeAndWait(() -> warnIfNotJdkHome());
  }

  private void warnIfNotJdkHome() {
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      return;
    }
    if (!NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.getDisplayId()).isShouldLog()) {
      return;
    }
    // using the IdeSdks requires us be on the dispatch thread
    ApplicationManager.getApplication().assertIsDispatchThread();

    IdeSdks ideSdks = IdeSdks.getInstance();
    if (ideSdks.isUsingJavaHomeJdk()) {
      return;
    }
    List<NotificationHyperlink> quickFixes = new ArrayList<>();
    UseJavaHomeAsJdkHyperlink useJavaHomeHyperlink = UseJavaHomeAsJdkHyperlink.create();
    if (useJavaHomeHyperlink != null) {
      quickFixes.add(useJavaHomeHyperlink);
    }
    quickFixes.add(new DoNotShowJdkHomeWarningAgainHyperlink());
    String msg = "Android Studio is using this JDK location:\n" +
                 ideSdks.getJdkPath() + "\n" +
                 "which is different to what Gradle uses by default:\n" +
                 IdeSdks.getJdkFromJavaHome() + "\n" +
                 "Using different locations may spawn multiple Gradle daemons if\n" +
                 "Gradle tasks are run from command line while using Android Studio.\n";
    addWarningToJdkEventLog(msg, quickFixes);
  }

  private void stopSyncInProgress() {
    synchronized (myLock) {
      mySyncInProgress = false;
      mySyncSkipped = false;
      myExternalSystemTaskId = null;
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
    return myGradleFiles.areGradleFilesModified() ? ThreeState.YES : ThreeState.NO;
  }

  /**
   * @return the timestamp (obtained via {@code System.currentTimeMillis()}) of when the last sync finished.
   */
  public long getLastSyncEndTimeStamp() {
    return mySyncEndedTimeStamp;
  }

  @NotNull
  public GradleSyncSummary getSummary() {
    return mySummary;
  }

  public void setupStarted() {
    long syncSetupTimestamp = System.currentTimeMillis();
    setSyncSetupStartedTimeStamp(syncSetupTimestamp);
    addInfoToSyncEventLog("Project setup started");
    LOG.info(String.format("Started setup of project '%1$s'.", myProject.getName()));
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).setupStarted(myProject));
    logSyncEvent(GRADLE_SYNC_SETUP_STARTED);
  }

  public void setExternalSystemTaskId(@Nullable ExternalSystemTaskId externalSystemTaskId) {
    synchronized (myLock) {
      myExternalSystemTaskId = externalSystemTaskId;
    }
  }

  @Nullable
  public ExternalSystemTaskId getExternalSystemTaskId() {
    synchronized (myLock) {
      return myExternalSystemTaskId;
    }
  }

  public void sourceGenerationFinished() {
    long sourceGenerationEndedTimestamp = System.currentTimeMillis();
    setSourceGenerationEndedTimeStamp(sourceGenerationEndedTimestamp);
    addInfoToSyncEventLog(String.format("Source generation ended in %1$s",
                                        formatDuration(mySourceGenerationEndedTimeStamp - mySyncSetupStartedTimeStamp)));
    LOG.info(String.format("Finished source generation of project '%1$s'.", myProject.getName()));
    syncPublisher(() -> myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).sourceGenerationFinished(myProject));
    // TODO: add metric to UsageTracker
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

  /**
   * Logs a syn event using {@link UsageTracker#log(AndroidStudioEvent.Builder)}.
   *
   * @param event the type of event to log.
   */
  private void logSyncEvent(@NotNull AndroidStudioEvent.EventKind event) {
    // Do not log an event if the project has been closed, working out the sync type for a disposed project results in
    // an error.
    if (myProject.isDisposed()) {
      return;
    }

    AndroidStudioEvent.Builder eventBuilder = generateSyncEvent(event);
    if (event == GRADLE_SYNC_ENDED) {
      GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(myProject);
      String gradleVersionString = gradleVersion != null ? gradleVersion.toString() : "";
      eventBuilder.setGradleVersion(gradleVersionString)
        .setKotlinSupport(generateKotlinSupportProto());
    }
    UsageTracker.log(eventBuilder);
  }

  @NotNull
  public AndroidStudioEvent.Builder generateSyncEvent(@NotNull AndroidStudioEvent.EventKind kind) {
    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
    GradleSyncStats.Builder syncStats = GradleSyncStats.newBuilder();
    Set<String> buildFilesTypes = projectBuildFilesTypes(myProject);
    // @formatter:off
    syncStats.setTotalTimeMs(getSyncTotalTimeMs())
             .setIdeTimeMs(getSyncIdeTimeMs())
             .setGradleTimeMs(getSyncGradleTimeMs())
             .setTrigger(myTrigger)
             .setSyncType(getSyncType())
             .setUsesBuildGradle(buildFilesTypes.contains(DOT_GRADLE))
             .setUsesBuildGradleKts(buildFilesTypes.contains(DOT_KTS));
    // @formatter:on
    setAndroidGradlePluginVersions(syncStats);
    event.setCategory(GRADLE_SYNC).setKind(kind).setGradleSyncStats(syncStats);
    return UsageTrackerUtils.withProjectId(event, myProject);
  }

  private void setAndroidGradlePluginVersions(GradleSyncStats.Builder stats) {
    String lastKnownVersion = getLastKnownAndroidGradlePluginVersion(myProject);
    if (lastKnownVersion != null) {
      stats.setLastKnownAndroidGradlePluginVersion(lastKnownVersion);
    }
    String lastSuccessfulVersion = getLastSuccessfulAndroidGradlePluginVersion(myProject);
    if (lastSuccessfulVersion != null) {
      stats.setAndroidGradlePluginVersion(lastSuccessfulVersion);
    }
  }

  @NotNull
  private GradleSyncStats.GradleSyncType getSyncType() {
    if (NewGradleSync.isShippedSync(myProject)) {
      return GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SHIPPED;
    }
    // Check in implied order (Compound requires SVS requires New Sync)
    if (NewGradleSync.isCompoundSync(myProject)) {
      return GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_COMPOUND;
    }
    if (NewGradleSync.isSingleVariantSync(myProject)) {
      return GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT;
    }
    if (NewGradleSync.isEnabled(myProject)) {
      return GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_NEW_SYNC;
    }
    return GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_IDEA;
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
}

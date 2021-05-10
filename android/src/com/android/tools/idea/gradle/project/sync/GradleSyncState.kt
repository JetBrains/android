/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.DOT_KTS
import com.android.annotations.concurrency.UiThread
import com.android.builder.model.level2.Library
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.project.sync.projectsystem.GradleSyncResultPublisher
import com.android.tools.idea.gradle.ui.SdkUiStrings.JDK_LOCATION_WARNING_URL
import com.android.tools.idea.gradle.util.GradleUtil.getLastKnownAndroidGradlePluginVersion
import com.android.tools.idea.gradle.util.GradleUtil.getLastSuccessfulAndroidGradlePluginVersion
import com.android.tools.idea.gradle.util.GradleUtil.projectBuildFilesTypes
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.project.AndroidProjectInfo
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.stats.withProjectId
import com.google.common.collect.Ordering
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.openapi.util.text.StringUtil.formatDuration
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import com.intellij.ui.EditorNotifications
import com.intellij.util.ThreeState
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG = Logger.getInstance(GradleSyncState::class.java)

private val SYNC_NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("Gradle Sync", PluginId.getId("org.jetbrains.android"))

open class StateChangeNotification(private val project: Project) {
  open fun notifyStateChanged() {
    invokeLaterIfProjectAlive(project) {
      val editorNotifications = EditorNotifications.getInstance(project)
      FileEditorManager.getInstance(project).openFiles.forEach { file ->
        try {
          editorNotifications.updateNotifications(file)
        }
        catch (e: Throwable) {
          LOG.info("Failed to update editor notifications for file '${toSystemDependentName(file.path)}'", e)
        }
      }
    }
  }
}

/**
 * This class manages the state of Gradle sync for a project.
 *
 * It contains state methods which are called from the Gradle sync infrastructure these are as follows:
 *   * syncTaskCreated
 *   * syncStarted
 *   * setupStarted
 *   * syncSucceeded
 *   * syncFailed
 *   * syncSkipped
 *   * sourceGenerationFinished
 *
 * Each of these methods record information about the current state of sync (e.g time taken for each stage) and passes these events
 * to any registered [GradleSyncListener]s via the projects messageBus or any one-time sync listeners passed into a specific invocation
 * of sync.
 */
open class GradleSyncState @NonInjectable internal constructor (
  private val project: Project,
  private val changeNotification: StateChangeNotification
) {

  constructor(
    project: Project
  ) : this(project, StateChangeNotification(project))

  companion object {
    @JvmField
    val JDK_LOCATION_WARNING_NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("JDK Location different to JAVA_HOME")

    @JvmField
    @Topic.AppLevel
    val GRADLE_SYNC_TOPIC = Topic(GradleSyncListener::class.java, Topic.BroadcastDirection.NONE)

    /**
     * These methods allow the registering of listeners to [GradleSyncState].
     *
     * See [GradleSyncListener] for more details on the different hooks through the syncing process.
     */
    @JvmStatic
    fun subscribe(project: Project, listener: GradleSyncListener) : MessageBusConnection = subscribe(project, listener, project)
    @JvmStatic
    fun subscribe(project: Project, listener: GradleSyncListener, disposable: Disposable) : MessageBusConnection {
      val connection = project.messageBus.connect(disposable)
      connection.subscribe(GRADLE_SYNC_TOPIC, listener)
      return connection
    }

    @JvmStatic
    fun getInstance(project: Project) : GradleSyncState = project.getService(GradleSyncState::class.java)

    @JvmStatic
    fun isSingleVariantSync(): Boolean {
      return StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.get() || GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC
    }
  }

  open var lastSyncedGradleVersion : GradleVersion? = null

  /**
   * Indicates whether the last started Gradle sync has failed or will fail.
   *
   * Possible failure causes:
   *   *An error occurred in Gradle (e.g. a missing dependency, or a missing Android platform in the SDK)
   *   *An error occurred while setting up a project using the models obtained from Gradle during sync (e.g. invoking a method that
   *    doesn't exist in an old version of the Android plugin)
   *   *An error in the structure of the project after sync (e.g. more than one module with the same path in the file system)
   */
  open fun lastSyncFailed(): Boolean = GradleProjectInfo.getInstance(project).isBuildWithGradle &&
                                       (AndroidProjectInfo.getInstance(project).requiredAndroidModelMissing() ||
                                        GradleSyncMessages.getInstance(project).errorCount > 0)

  // For Java compat, to be refactored out
  open fun areSyncNotificationsEnabled() = areSyncNotificationsEnabled

  private val lock = ReentrantLock()

  private var areSyncNotificationsEnabled = false
    get() = lock.withLock { return field }
    private set(value) = lock.withLock { field = value }
  open var isSyncInProgress = false
    get() = lock.withLock { return field }
    set(value) = lock.withLock { field = value }
  var externalSystemTaskId : ExternalSystemTaskId? = null
    get() = lock.withLock { return field }
    set(value) = lock.withLock { field = value }

  // Negative numbers mean that the events have not finished
  private var syncStartedTimeStamp = -1L
  private var syncSetupStartedTimeStamp = -1L
  private var syncEndedTimeStamp = -1L
  private var sourceGenerationEndedTimeStamp = -1L
  private var syncFailedTimeStamp = -1L
  open var lastSyncFinishedTimeStamp = -1L

  private var trigger = GradleSyncStats.Trigger.TRIGGER_UNKNOWN

  /*
   * START GradleSyncListener methods
   *
   * The following method deal with the GradleSync life cycle. These are called from within sync to perform housekeeping to do with
   * monitoring and recording the state of sync. They also deal with notifying listeners both directly and via the GRADLE_SYNC_TOPIC.
   *
   * In each method we ensure that we don't notify the listeners until all the housekeeping has been performed in case they need to read
   * state about sync. First we notify the listener specifically for this sync, then we notify all the other registered
   * listeners.
   * */

  /**
   * Triggered at the start of a sync which has been started by the given [request].
   *
   * This method should only be called by the sync internals.
   * Please use [GradleSyncListener] and [subscribe] if you need to hook into sync.
   */
  fun syncStarted(request: GradleSyncInvoker.Request) : Boolean {
    lock.withLock {
      if (isSyncInProgress) {
        LOG.info("Sync already in progress for project '${project.name}'.")
        return false
      }

      isSyncInProgress = true
    }

    val syncType = if (isSingleVariantSync()) "single-variant" else "full-variants"
    LOG.info("Started $syncType sync with Gradle for project '${project.name}'.")

    setSyncStartedTimeStamp()
    trigger = request.trigger

    addToEventLog(SYNC_NOTIFICATION_GROUP, "Gradle sync started", MessageType.INFO, null)

    changeNotification.notifyStateChanged()

    // If this is the first Gradle sync for this project this session, make sure that GradleSyncResultPublisher
    // has been initialized so that it will begin broadcasting sync results on PROJECT_SYSTEM_SYNC_TOPIC.
    // TODO(b/133154939): Move this out of GradleSyncState, possibly to AndroidProjectComponent.
    if (lastSyncFinishedTimeStamp < 0) GradleSyncResultPublisher.getInstance(project)

    GradleFiles.getInstance(project).maybeProcessSyncStarted()

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED)
    return true
  }

  /**
   * Triggered at the start of setup, after the models have been fetched.
   *
   * This method should only be called by the sync internals.
   * Please use [GradleSyncListener] and [subscribe] if you need to hook into sync.
   */
  open fun setupStarted() {
    syncSetupStartedTimeStamp = System.currentTimeMillis()

    LOG.info("Started setup of project '${project.name}'.")

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_SETUP_STARTED)
  }

  /**
   * Triggered at the end of a successful sync, once the models have been fetched.
   *
   * TODO: This should be called after (rather than before) project setup
   * TODO: Add SyncListener to params and call it in the method.
   */
  open fun syncSucceeded() {
    // syncFailed should be called if there're any sync issues.
    assert(!lastSyncFailed())

    val syncEndTimeStamp = System.currentTimeMillis()

    // If mySyncStartedTimestamp is -1, that means sync has not started or syncSucceeded has been called for this invocation.
    // Reset sync state and don't log the events or notify listener again.
    // TODO: Replace with exception once sync event calls are corrected to ensure this doesn't over trigger.
    if (syncStartedTimeStamp == -1L) {
      syncFinished(syncEndTimeStamp)
      return
    }
    syncEndedTimeStamp = syncEndTimeStamp

    val message = "Gradle sync finished in ${formatDuration(syncEndTimeStamp - syncStartedTimeStamp)}"
    addToEventLog(SYNC_NOTIFICATION_GROUP,
                  message,
                  MessageType.INFO,
                  null
    )
    LOG.info(message)

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED)

    syncFinished(syncEndTimeStamp)
    syncPublisher { syncSucceeded(project) }
  }

  /**
   * Triggered when a sync has been found to have failed.
   *
   * TODO: This should only be called at the end of sync, not all throughout which is currently the case
   */
  open fun syncFailed(message: String?, error: Throwable?, listener: GradleSyncListener?) {
    ProjectStructure.getInstance(project).clearData()

    val syncEndTimeStamp = System.currentTimeMillis()

    // If mySyncStartedTimestamp is -1, that means sync has not started or syncFailed has been called for this invocation.
    // Reset sync state and don't log the events or notify listener again.
    if (syncStartedTimeStamp == -1L) {
      syncFinished(syncEndTimeStamp)
      return
    }

    syncFailedTimeStamp = syncEndTimeStamp

    val throwableMessage = error?.message
    // Find a none null message from either the provided message or the given throwable.
    val causeMessage : String = when {
      !message.isNullOrBlank() -> message
      !throwableMessage.isNullOrBlank() -> throwableMessage
      GradleSyncMessages.getInstance(project).errorDescription.isNotEmpty() -> GradleSyncMessages.getInstance(project).errorDescription
      else -> "Unknown cause".also { LOG.warn(IllegalStateException("No error message given")) }
    }
    val resultMessage = "Gradle sync failed: $causeMessage (${formatDuration(syncEndTimeStamp - syncStartedTimeStamp)})"
    addToEventLog(SYNC_NOTIFICATION_GROUP, resultMessage, MessageType.ERROR, null)
    LOG.warn(resultMessage)

    // Log the error to ideas log
    // Note: we log this as well as message above so the stack trace is present in the logs.
    if (error != null) LOG.warn(error)

    // If we are in use tests also log to stdout to help debugging.
    if (ApplicationManager.getApplication().isUnitTestMode) {
      println("***** sync error ${if (error == null) message else error.message}")
    }

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE)

    syncFinished(syncEndTimeStamp)

    listener?.syncFailed(project, causeMessage)
    syncPublisher { syncFailed(project, causeMessage) }
  }

  /**
   * Triggered when a sync have been skipped, this happens when the project is setup by models from the cache.
   */
  open fun syncSkipped(listener: GradleSyncListener?) {
    val syncEndTimeStamp = System.currentTimeMillis()
    syncEndedTimeStamp = syncEndTimeStamp

    syncFinished(syncEndTimeStamp, true)

    listener?.syncSkipped(project)
    syncPublisher { syncSkipped(project) }
  }

  /*
   * END GradleSyncListener methods
   */

  /*
   * START public utility methods
   */

  open fun isSyncNeeded() : ThreeState = if (GradleFiles.getInstance(project).areGradleFilesModified()) ThreeState.YES else ThreeState.NO
  fun isSourceGenerationFinished() : Boolean = sourceGenerationEndedTimeStamp != -1L

  fun generateSyncEvent(kind: AndroidStudioEvent.EventKind) : AndroidStudioEvent.Builder {
    val event = AndroidStudioEvent.newBuilder()
    val syncStats = GradleSyncStats.newBuilder()
    val buildFileTypes = projectBuildFilesTypes(project)

    // Setup the sync stats
    syncStats.totalTimeMs = getSyncTotalTimeMs()
    syncStats.ideTimeMs = getSyncIdeTimeMs()
    syncStats.gradleTimeMs = getSyncGradleTimeMs()
    syncStats.trigger = trigger
    syncStats.syncType = getSyncType()
    syncStats.usesBuildGradle = buildFileTypes.contains(DOT_GRADLE)
    syncStats.usesBuildGradleKts = buildFileTypes.contains(DOT_KTS)

    val lastKnownVersion = getLastKnownAndroidGradlePluginVersion(project)
    if (lastKnownVersion != null) syncStats.lastKnownAndroidGradlePluginVersion = lastKnownVersion
    val lastSuccessfulVersion = getLastSuccessfulAndroidGradlePluginVersion(project)
    if (lastSuccessfulVersion != null) syncStats.androidGradlePluginVersion = lastSuccessfulVersion

    // Set up the Android studio event
    event.category = AndroidStudioEvent.EventCategory.GRADLE_SYNC
    event.kind = kind
    event.gradleSyncStats = syncStats.build()
    return event.withProjectId(project)
  }

  /**
   * Returns the total time taken by the last sync or 0 if there is a sync in progress and setup has not been reached.
   */
  fun getSyncTotalTimeMs() : Long = when {
    syncEndedTimeStamp >= 0 -> syncEndedTimeStamp - syncStartedTimeStamp // Sync was successful
    syncFailedTimeStamp >= 0 ->  syncFailedTimeStamp - syncStartedTimeStamp // Sync failed
    syncSetupStartedTimeStamp >= 0 -> syncSetupStartedTimeStamp - syncStartedTimeStamp // Only fetching model has finished
    else -> 0
  }

  /**
   * Returns the time spent in the IDE part of the last sync (excluding time spent/waiting for Gradle).
   * If sync has been done from cache, sync has never occurs, sync is in progress or the last sync failed before
   * setup this method returns -1.
   */
  fun getSyncIdeTimeMs() : Long = when {
    syncEndedTimeStamp < 0 -> -1  // Sync is in progress or something went wrong during the last sync
    syncSetupStartedTimeStamp < 0 -> -1 // Sync was done from cache (no gradle nor IDE part was done)
    else -> syncEndedTimeStamp - syncSetupStartedTimeStamp
  }

  /**
   * Returns the time spent in the Gradle part of the last sync.
   * If sync has never been performed or models were loaded from cache, this method returns -1.
   */
  fun getSyncGradleTimeMs() = if (syncSetupStartedTimeStamp >= 0) syncSetupStartedTimeStamp - syncStartedTimeStamp else -1

  /*
   * END public utility methods
   */

  /*
   * START Test-only state manipulation methods. These methods should ONLY be used in tests.
   */

  /**
   * Clears all of the [GradleSyncState]s time stamps and sets [syncStartedTimeStamp] to the given time-stamp and updates the
   * trigger to the given [newTrigger]
   */
  @TestOnly
  fun setSyncStartedTimeStamp(timeStamp: Long, newTrigger: GradleSyncStats.Trigger) {
    syncStartedTimeStamp = timeStamp
    syncSetupStartedTimeStamp = -1
    syncEndedTimeStamp = -1
    sourceGenerationEndedTimeStamp = -1
    syncFailedTimeStamp = -1
    lastSyncFinishedTimeStamp = -1
    trigger = newTrigger
  }

  @TestOnly
  fun setSyncSetupStartedTimeStamp(timeStamp: Long) { syncSetupStartedTimeStamp = timeStamp }
  @TestOnly
  fun setSyncEndedTimeStamp(timeStamp: Long) { syncEndedTimeStamp = timeStamp }
  @TestOnly
  fun setSyncFailedTimeStamp(timeStamp: Long) { syncFailedTimeStamp = timeStamp }

  /*
   * END Test-only state manipulation methods
   */

  /**
   * Common code to (re)set state once the sync has completed, all successful/failed/skipped syncs should run through this method.
   */
  private fun syncFinished(timeStamp: Long, skipped: Boolean = false) {
    syncStartedTimeStamp = -1L
    lastSyncFinishedTimeStamp = timeStamp

    lock.withLock {
      isSyncInProgress = false
      externalSystemTaskId = null

      areSyncNotificationsEnabled = true
    }

    // TODO: Move out of GradleSyncState, create a ProjectCleanupTask to show this warning?
    if (!skipped) {
      changeNotification.notifyStateChanged()
      ApplicationManager.getApplication().invokeAndWait { warnIfNotJdkHome() }
    }
  }

  @UiThread
  private fun warnIfNotJdkHome() {
    if (!IdeInfo.getInstance().isAndroidStudio) return
    if (!NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId).isShouldLog) return

    // Using the IdeSdks requires us to be on the dispatch thread
    ApplicationManager.getApplication().assertIsDispatchThread()

    val ideSdks = IdeSdks.getInstance()
    if (ideSdks.isUsingJavaHomeJdk) return

    val quickFixes = mutableListOf<NotificationHyperlink>(OpenUrlHyperlink(JDK_LOCATION_WARNING_URL, "More info..."))
    val selectJdkHyperlink = SelectJdkFromFileSystemHyperlink.create(project)
    if (selectJdkHyperlink != null) quickFixes += selectJdkHyperlink
    quickFixes.add(DoNotShowJdkHomeWarningAgainHyperlink())

    val message = """
      Android Studio is using the following JDK location when running Gradle:
      ${ideSdks.jdkPath}
      Using different JDK locations on different processes might cause Gradle to
      spawn multiple daemons, for example, by executing Gradle tasks from a terminal
      while using Android Studio.
    """.trimIndent()
    addToEventLog(JDK_LOCATION_WARNING_NOTIFICATION_GROUP, message, MessageType.WARNING, quickFixes)
  }

  /**
   * Clears all of the [GradleSyncState]s time stamps and sets [syncStartedTimeStamp] to the current system time.
   */
  private fun setSyncStartedTimeStamp() {
    syncStartedTimeStamp = System.currentTimeMillis()
    syncSetupStartedTimeStamp = -1
    syncEndedTimeStamp = -1
    sourceGenerationEndedTimeStamp = -1
    syncFailedTimeStamp = -1
    lastSyncFinishedTimeStamp = -1
  }

  /**
   * Logs a sync event using [UsageTracker]
   */
  private fun logSyncEvent(kind: AndroidStudioEvent.EventKind) {
    // Do not log an event if the project has been closed, working out the sync type for a disposed project results in
    // an error.
    if (project.isDisposed) return

    val event = generateSyncEvent(kind)

    if (kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED) {
      event.gradleVersion = GradleVersions.getInstance().getGradleVersion(project)?.toString() ?: ""
      event.setKotlinSupport(generateKotlinSupport())
    }

    UsageTracker.log(event)
  }

  private fun generateKotlinSupport() : KotlinSupport.Builder {
    var kotlinVersion : GradleVersion? = null
    var ktxVersion : GradleVersion? = null

    val ordering = Ordering.natural<GradleVersion>().nullsFirst<GradleVersion>()

    ModuleManager.getInstance(project).modules.mapNotNull { module -> AndroidModuleModel.get(module) }.forEach { model ->
      val dependencies = model.selectedMainCompileLevel2Dependencies

      kotlinVersion = ordering.max(kotlinVersion, dependencies.javaLibraries.findVersion("org.jetbrains.kotlin:kotlin-stdlib"))
      ktxVersion = ordering.max(ktxVersion, dependencies.androidLibraries.findVersion("androidx.core:core-ktx"))
    }

    val kotlinSupport = KotlinSupport.newBuilder()
    if (kotlinVersion != null) kotlinSupport.kotlinSupportVersion = kotlinVersion.toString()
    if (ktxVersion != null) kotlinSupport.androidKtxVersion = ktxVersion.toString()
    return kotlinSupport
  }

  private fun addToEventLog(
    notificationGroup: NotificationGroup,
    message: String,
    type: MessageType,
    quickFixes: List<NotificationHyperlink>?
  ) {
    var resultMessage = message
    var listener : NotificationListener? = null
    if (quickFixes != null) {
      quickFixes.forEach { quickFix ->
        resultMessage += "\n${quickFix.toHtml()}"
      }
      listener = NotificationListener { _, event ->
        quickFixes.forEach { link -> link.executeIfClicked(project, event) }
      }
    }
    val notification = notificationGroup.createNotification("", resultMessage, type.toNotificationType())
    if (listener != null) notification.setListener(listener)
    notification.notify(project)
  }

  private fun syncPublisher(block: GradleSyncListener.() -> Unit) {
    val runnable = { block.invoke(project.messageBus.syncPublisher(GRADLE_SYNC_TOPIC)) }
    if (ApplicationManager.getApplication().isUnitTestMode) {
      runnable()
    }
    else {
      invokeLaterIfProjectAlive(project, runnable)
    }
  }

  private fun getSyncType(): GradleSyncStats.GradleSyncType = when {
    isSingleVariantSync() -> GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT
    else -> GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_IDEA
  }

  class DataImportListener(val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
      LOG.info("onImportFinished($projectPath)")
      val syncState = GradleSyncState.getInstance(project)
      if (syncState.lastSyncFailed()) {
        syncState.syncFailed("", null, null)
      } else {
        syncState.syncSucceeded()
      }
    }

    override fun onImportFailed(projectPath: String?) {
      LOG.info("onImportFailed($projectPath)")
      GradleSyncState.getInstance(project).syncFailed("", null, null)
    }
  }
}

private fun Collection<Library>.findVersion(artifact: String) : GradleVersion? {
  val library = firstOrNull { library -> library.artifactAddress.startsWith(artifact) } ?: return null
  return GradleCoordinate.parseCoordinateString(library.artifactAddress)?.version
}

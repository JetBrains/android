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

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.GradleVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.project.sync.projectsystem.GradleSyncResultPublisher
import com.android.tools.idea.gradle.ui.SdkUiStrings.JDK_LOCATION_WARNING_URL
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.BuildProgressListener
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.formatDuration
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import com.intellij.util.ThreeState
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val SYNC_NOTIFICATION_GROUP =
  NotificationGroup.logOnlyGroup("Gradle Sync", PluginId.getId("org.jetbrains.android"))

data class ProjectSyncRequest(val projectRoot: String, val trigger: GradleSyncStats.Trigger, val fullSync: Boolean)

@JvmField
val PROJECT_SYNC_REQUEST = Key.create<ProjectSyncRequest>("PROJECT_SYNC_REQUEST")

/**
 * This class manages the state of Gradle sync for a project.
 *
*
 * This class records information from various sources about the current state of sync (e.g time taken for each stage) and passes these
 * events to any registered [GradleSyncListener]s via the projects messageBus or any one-time sync listeners passed into a specific
 * invocation of sync.
 */
open class GradleSyncState @NonInjectable constructor(private val project: Project) {
  companion object {
    @JvmField
    val JDK_LOCATION_WARNING_NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("JDK Location different to JAVA_HOME")

    @JvmField
    val GRADLE_SYNC_TOPIC = Topic("Project sync with Gradle", GradleSyncListener::class.java)

    /**
     * These methods allow the registering of listeners to [GradleSyncState].
     *
     * See [GradleSyncListener] for more details on the different hooks through the syncing process.
     */
    @JvmStatic
    fun subscribe(project: Project, listener: GradleSyncListener): MessageBusConnection = subscribe(project, listener, project)

    @JvmStatic
    fun subscribe(project: Project, listener: GradleSyncListener, disposable: Disposable): MessageBusConnection {
      val connection = project.messageBus.connect(disposable)
      connection.subscribe(GRADLE_SYNC_TOPIC, listener)
      return connection
    }

    @JvmStatic
    fun getInstance(project: Project): GradleSyncState = ServiceManager.getService(project, GradleSyncState::class.java)
  }

  private enum class LastSyncState(val isInProgress: Boolean = false, val isSuccessful: Boolean = false, val isFailed: Boolean = false) {
    UNKNOWN(),
    SKIPPED(isSuccessful = true),
    IN_PROGRESS(isInProgress = true),
    SUCCEEDED(isSuccessful = true),
    FAILED(isFailed = true);

    init {
      assert(!(isSuccessful && isFailed))
    }
  }

  open var lastSyncedGradleVersion: GradleVersion? = null

  /**
   * Indicates whether the last started Gradle sync has failed.
   *
   * Possible failure causes:
   *   *An error occurred in Gradle (e.g. a missing dependency, or a missing Android platform in the SDK)
   *   *An error occurred while setting up a project using the models obtained from Gradle during sync (e.g. invoking a method that
   *    doesn't exist in an old version of the Android plugin)
   *   *An error in the structure of the project after sync (e.g. more than one module with the same path in the file system)
   */
  open fun lastSyncFailed(): Boolean = state.isFailed

  open val isSyncInProgress: Boolean get() = state.isInProgress

  private val lock = ReentrantLock()

  private var state: LastSyncState = LastSyncState.UNKNOWN
    get() = lock.withLock { return field }
    set(value) = lock.withLock { field = value }
  var externalSystemTaskId: ExternalSystemTaskId? = null
    get() = lock.withLock { return field }
    set(value) = lock.withLock { field = value }

  private val eventLogger = GradleSyncEventLogger()

  fun generateSyncEvent(eventKind: AndroidStudioEvent.EventKind) = eventLogger.generateSyncEvent(project, eventKind)

  open var lastSyncFinishedTimeStamp = -1L; protected set

  /**
   * Triggered at the start of a sync.
   */
  private fun syncStarted(trigger: GradleSyncStats.Trigger, fullSync: Boolean): Boolean {
    lock.withLock {
      if (state.isInProgress) {
        LOG.error("Sync already in progress for project '${project.name}'.", Throwable())
        return false
      }

      state = LastSyncState.IN_PROGRESS
    }

    val syncType = if (fullSync) "full-variants" else "single-variant"
    LOG.info("Started $syncType ($trigger) sync with Gradle for project '${project.name}'.")

    eventLogger.syncStarted(getSyncType(fullSync), trigger)

    addToEventLog(SYNC_NOTIFICATION_GROUP, "Gradle sync started", MessageType.INFO, null)

    // If this is the first Gradle sync for this project this session, make sure that GradleSyncResultPublisher
    // has been initialized so that it will begin broadcasting sync results on PROJECT_SYSTEM_SYNC_TOPIC.
    // TODO(b/133154939): Move this out of GradleSyncState, possibly to AndroidProjectComponent.
    if (lastSyncFinishedTimeStamp < 0) GradleSyncResultPublisher.getInstance(project)

    GradleFiles.getInstance(project).maybeProcessSyncStarted()

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED)
    syncPublisher { syncStarted(project) }
    return true
  }

  /**
   * Triggered at the start of setup, after the models have been fetched.
   */
  private fun setupStarted() {
    eventLogger.setupStarted()

    LOG.info("Started setup of project '${project.name}'.")

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_SETUP_STARTED)
  }

  /**
   * Triggered at the end of a successful sync, once the models have been fetched.
   */
  private fun syncSucceeded() {
    val millisTook = eventLogger.syncEnded()

    val message = "Gradle sync finished in ${formatDuration(millisTook)}"
    addToEventLog(SYNC_NOTIFICATION_GROUP,
                  message,
                  MessageType.INFO,
                  null
    )
    LOG.info(message)

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED)

    syncFinished(LastSyncState.SUCCEEDED)
    syncPublisher { syncSucceeded(project) }
  }

  /**
   * Triggered when a sync has been found to have failed.
   */
  private fun syncFailed(message: String?, error: Throwable?) {
    val millisTook = eventLogger.syncEnded()
    ProjectStructure.getInstance(project).clearData()
    val throwableMessage = error?.message
    // Find a none null message from either the provided message or the given throwable.
    val causeMessage: String = when {
      !message.isNullOrBlank() -> message
      !throwableMessage.isNullOrBlank() -> throwableMessage
      GradleSyncMessages.getInstance(project).errorDescription.isNotEmpty() -> GradleSyncMessages.getInstance(project).errorDescription
      else -> "Unknown cause".also { LOG.warn(IllegalStateException("No error message given")) }
    }
    val resultMessage = "Gradle sync failed: $causeMessage (${formatDuration(millisTook)})"
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

    syncFinished(LastSyncState.FAILED)
    syncPublisher { syncFailed(project, causeMessage) }
  }

  /**
   * Triggered when a sync have been skipped, this happens when the project is setup by models from the cache.
   */
  fun syncSkipped(listener: GradleSyncListener?) {
    syncFinished(LastSyncState.SKIPPED)
    listener?.syncSkipped(project)
    syncPublisher { syncSkipped(project) }
  }

  open fun isSyncNeeded(): ThreeState {
    return when {
      PropertiesComponent.getInstance().getBoolean(ANDROID_GRADLE_SYNC_NEEDED_PROPERTY_NAME) -> ThreeState.YES
      GradleFiles.getInstance(project).areGradleFilesModified() -> ThreeState.YES
      else -> ThreeState.NO
    }
  }

  /**
   * Common code to (re)set state once the sync has completed, all successful/failed/skipped syncs should run through this method.
   */
  private fun syncFinished(newState: LastSyncState) {
    lastSyncFinishedTimeStamp = System.currentTimeMillis()

    lock.withLock {
      state = newState
      externalSystemTaskId = null
    }

    project.putUserData(GradleSyncExecutor.FULL_SYNC_KEY, null)
    PropertiesComponent.getInstance().setValue(ANDROID_GRADLE_SYNC_NEEDED_PROPERTY_NAME, !newState.isSuccessful)

    // TODO: Move out of GradleSyncState, create a ProjectCleanupTask to show this warning?
    if (newState != LastSyncState.SKIPPED) {
      ApplicationManager.getApplication().invokeAndWait { warnIfNotJdkHome() }
    }
  }

  @UiThread
  private fun warnIfNotJdkHome() {
    if (!IdeInfo.getInstance().isAndroidStudio) return
    if (!NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId).isShouldLog) return

    // Using the IdeSdks requires us to be on the dispatch thread
    ApplicationManager.getApplication().assertIsDispatchThread()

    val gradleInstallation = (GradleInstallationManager.getInstance() as AndroidStudioGradleInstallationManager)
    if (gradleInstallation.isUsingJavaHomeJdk(project)) {
      return
    }
    val namePrefix = "Project ${project.name}"
    val jdkPath: String? = gradleInstallation.getGradleJvmPath(project, project.basePath!!)


    val quickFixes = mutableListOf<NotificationHyperlink>(OpenUrlHyperlink(JDK_LOCATION_WARNING_URL, "More info..."))
    val selectJdkHyperlink = SelectJdkFromFileSystemHyperlink.create(project)
    if (selectJdkHyperlink != null) quickFixes += selectJdkHyperlink
    quickFixes.add(DoNotShowJdkHomeWarningAgainHyperlink())

    val message = """
      $namePrefix is using the following JDK location when running Gradle:
      $jdkPath
      Using different JDK locations on different processes might cause Gradle to
      spawn multiple daemons, for example, by executing Gradle tasks from a terminal
      while using Android Studio.
    """.trimIndent()
    addToEventLog(JDK_LOCATION_WARNING_NOTIFICATION_GROUP, message, MessageType.WARNING, quickFixes)
  }

  /**
   * Logs a sync event using [UsageTracker]
   */
  private fun logSyncEvent(kind: AndroidStudioEvent.EventKind) {
    // Do not log an event if the project has been closed, working out the sync type for a disposed project results in
    // an error.
    if (project.isDisposed) return

    val event = eventLogger.generateSyncEvent(project, kind)

    UsageTracker.log(event)
  }

  private fun addToEventLog(
    notificationGroup: NotificationGroup,
    message: String,
    type: MessageType,
    quickFixes: List<NotificationHyperlink>?
  ) {
    var resultMessage = message
    var listener: NotificationListener? = null
    if (quickFixes != null) {
      quickFixes.forEach { quickFix ->
        resultMessage += "\n${quickFix.toHtml()}"
      }
      listener = NotificationListener { _, event ->
        quickFixes.forEach { link -> link.executeIfClicked(project, event) }
      }
    }
    notificationGroup.createNotification("", resultMessage, type.toNotificationType(), listener).notify(project)
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

  private fun getSyncType(fullSync: Boolean): GradleSyncStats.GradleSyncType = when(fullSync) {
    true -> GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_IDEA
    else -> GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT
  }

  /**
   * A helper project level service to keep track of external system sync related tasks and to detect and report any mismatched or
   * unexpected events.
   */
  @Service
  class SyncStateUpdaterService : Disposable {
    val runningTasks: ConcurrentMap<ExternalSystemTaskId, Pair<String, Disposable>> = ConcurrentHashMap()

    fun trackTask(id: ExternalSystemTaskId, projectPath: String): Disposable? {
      LOG.info("trackTask($id, $projectPath)")
      val normalizedProjectPath = normalizePath(projectPath)
      val disposable = Disposer.newDisposable()
      val old = runningTasks.putIfAbsent(id, normalizedProjectPath to disposable)
      if (old != null) {
        LOG.warn("External task $id has been already started")
        Disposer.dispose(disposable)
        return null
      }
      return disposable
    }

    fun stopTrackingTask(id: ExternalSystemTaskId): Boolean {
      LOG.info("stopTrackingTask($id)")
      val info = runningTasks.remove(id)
      if (info == null) {
        LOG.warn("Unknown build $id finished")
        return false
      }
      Disposer.dispose(info.second) // Unsubscribe from notifications.
      return true
    }

    fun stopTrackingTask(projectDir: String): Boolean {
      LOG.info("stopTrackingTask($projectDir)")
      val normalizedProjectPath = normalizePath(projectDir)
      val task = runningTasks.entries.find { it.value.first == normalizedProjectPath }?.key ?: return false
      return stopTrackingTask(task)
    }

    override fun dispose() {
      runningTasks.toList().forEach { (key, value) ->
        runCatching {
          Disposer.dispose(value.second)
          runningTasks.remove(key)
        }
      }
    }
  }

  class DataImportListener(val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
      LOG.info("onImportFinished($projectPath)")
      val syncStateUpdaterService = project.getService(SyncStateUpdaterService::class.java)
      if (syncStateUpdaterService.stopTrackingTask(projectPath!!)) {
        GradleSyncState.getInstance(project).syncSucceeded()
      }
    }
  }

  class SyncStateUpdater : ExternalSystemTaskNotificationListener, BuildProgressListener {

    private fun ExternalSystemTaskId.findProjectorLog(): Project? {
      val project = findProject()
      if (project == null) {
        LOG.warn("No project found for $this")
      }
      return project
    }

    override fun onStart(id: ExternalSystemTaskId, workingDir: String) {
      if (!id.isGradleResolveProjectTask()) return
      LOG.info("onStart($id, $workingDir)")
      val project = id.findProjectorLog() ?: return
      val syncStateUpdaterService = project.getService(SyncStateUpdaterService::class.java)
      val disposable = syncStateUpdaterService.trackTask(id, workingDir) ?: return
      val trigger =
        project.getUserData(PROJECT_SYNC_REQUEST)
          ?.takeIf { it.projectRoot == workingDir }
      if (trigger != null) {
        project.putUserData(PROJECT_SYNC_REQUEST, null)
      }
      if (!GradleSyncState.getInstance(project)
          .syncStarted(trigger?.trigger ?: GradleSyncStats.Trigger.TRIGGER_UNKNOWN, trigger?.fullSync ?: false)
      ) {
        stopTrackingTask(project, id)
        return
      }
      project.getService(SyncViewManager::class.java).addListener(this, disposable)
    }


    override fun onSuccess(id: ExternalSystemTaskId) {
      if (!id.isGradleResolveProjectTask()) return
      LOG.info("onSuccess($id)")
      val project = id.findProjectorLog() ?: return
      val runningTasks = project.getService(SyncStateUpdaterService::class.java).runningTasks
      if (!runningTasks.containsKey(id)) return
      GradleSyncState.getInstance(project).setupStarted()
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
      if (!id.isGradleResolveProjectTask()) return
      LOG.info("onFailure($id, $e)")
      val project = id.findProjectorLog() ?: return
      if (!stopTrackingTask(project, id)) return
      GradleSyncState.getInstance(project).syncFailed(null, e)
    }

    override fun onStart(id: ExternalSystemTaskId) = error("Not expected to be called. onStart with a different signature is implemented")

    override fun onEnd(id: ExternalSystemTaskId) = Unit
    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) = Unit
    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) = Unit
    override fun beforeCancel(id: ExternalSystemTaskId) = Unit
    override fun onCancel(id: ExternalSystemTaskId) = Unit

    override fun onEvent(buildId: Any, event: BuildEvent) {
      if (event !is FinishBuildEvent) return
      if (buildId !is ExternalSystemTaskId) {
        LOG.warn("Unexpected buildId $buildId of type ${buildId::class.java} encountered")
        return
      }
      val project = buildId.findProjectorLog() ?: return
      if (!stopTrackingTask(project, buildId)) return
      // Regardless of the result report sync failure. A successful sync would have already removed the task via ProjectDataImportListener.
      val failure = event.result as? FailureResult
      val message = failure?.failures?.mapNotNull { it.message }?.joinToString(separator = "\n") ?: "Sync failed: reason unknown"
      val throwable = failure?.failures?.map { it.error }?.firstOrNull { it != null }
      GradleSyncState.getInstance(project).syncFailed(message, throwable)
    }

    private fun stopTrackingTask(project: Project, buildId: ExternalSystemTaskId): Boolean {
      val syncStateUpdaterService = project.getService(SyncStateUpdaterService::class.java)
      return syncStateUpdaterService.stopTrackingTask(buildId)
    }
  }
}

private fun ExternalSystemTaskId.isGradleResolveProjectTask() =
  projectSystemId == GRADLE_SYSTEM_ID && type == ExternalSystemTaskType.RESOLVE_PROJECT

private fun normalizePath(projectPath: String) = ExternalSystemApiUtil.toCanonicalPath(projectPath)

private val Any.LOG get() = Logger.getInstance(this::class.java)  // Used for non-frequent logging.
private const val ANDROID_GRADLE_SYNC_NEEDED_PROPERTY_NAME = "android.gradle.sync.needed"

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
@file:Suppress("RemoveRedundantQualifierName")

package com.android.tools.idea.gradle.project.sync

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.GradleVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.sync.GradleSyncState.Companion.JDK_LOCATION_WARNING_NOTIFICATION_GROUP
import com.android.tools.idea.gradle.project.sync.hyperlink.DoNotShowJdkHomeWarningAgainHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages
import com.android.tools.idea.gradle.ui.SdkUiStrings.JDK_LOCATION_WARNING_URL
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.project.hyperlink.NotificationHyperlink
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.sdk.IdeSdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.BuildProgressListener
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.SuccessResult
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.text.StringUtil.formatDuration
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import com.intellij.util.PathUtil.toSystemIndependentName
import com.intellij.util.ThreeState
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val SYNC_NOTIFICATION_GROUP =
  NotificationGroup.logOnlyGroup("Gradle Sync", PluginId.getId("org.jetbrains.android"))

/**
 * This class manages the state of Gradle sync for a project.
 *
*
 * This class records information from various sources about the current state of sync (e.g time taken for each stage) and passes these
 * events to any registered [GradleSyncListener]s via the projects messageBus or any one-time sync listeners passed into a specific
 * invocation of sync.
 */
class GradleSyncStateImpl constructor(project: Project) : GradleSyncState {
  private val delegate = GradleSyncStateHolder.getInstance(project)
  override val isSyncInProgress: Boolean
    get() = delegate.isSyncInProgress
  override val externalSystemTaskId: ExternalSystemTaskId?
    get() = delegate.externalSystemTaskId
  override val lastSyncFinishedTimeStamp: Long
    get() = delegate.lastSyncFinishedTimeStamp
  override val lastSyncedGradleVersion: GradleVersion?
    get() = delegate.lastSyncedGradleVersion

  override fun lastSyncFailed(): Boolean = delegate.lastSyncFailed()
  override fun isSyncNeeded(): ThreeState = delegate.isSyncNeeded()

  override fun subscribe(project: Project, listener: GradleSyncListenerWithRoot, disposable: Disposable): MessageBusConnection {
    val connection = project.messageBus.connect(disposable)
    connection.subscribe(GRADLE_SYNC_TOPIC, listener)
    return connection
  }
}

@VisibleForTesting
@Topic.AppLevel
val GRADLE_SYNC_TOPIC = Topic("Project sync with Gradle", GradleSyncListenerWithRoot::class.java, Topic.BroadcastDirection.NONE)

/**
 * A real implementation of [GradleSyncStateImpl] service which, unlike [GradleSyncStateImpl], can be accessed by various listeners in this
 * file as an implementing class type.
 */
class GradleSyncStateHolder constructor(private val project: Project)  {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleSyncStateHolder = project.getService(GradleSyncStateHolder::class.java)
  }

  private enum class LastSyncState(val isInProgress: Boolean = false, val isSuccessful: Boolean = false, val isFailed: Boolean = false) {
    UNKNOWN,
    SKIPPED(isSuccessful = true),
    IN_PROGRESS(isInProgress = true),
    SUCCEEDED(isSuccessful = true),
    FAILED(isFailed = true);

    init {
      assert(!(isSuccessful && isFailed))
    }
  }

  /**
   * Indicates whether the last started Gradle sync has failed.
   *
   * Possible failure causes:
   *   *An error occurred in Gradle (e.g. a missing dependency, or a missing Android platform in the SDK)
   *   *An error occurred while setting up a project using the models obtained from Gradle during sync (e.g. invoking a method that
   *    doesn't exist in an old version of the Android plugin)
   *   *An error in the structure of the project after sync (e.g. more than one module with the same path in the file system)
   */
  fun lastSyncFailed(): Boolean = state.get { state.isFailed }

  val lastSyncedGradleVersion: GradleVersion? get() = state.get { lastSyncedGradleVersion }
  val lastSyncFinishedTimeStamp: Long get() = state.get { lastSyncFinishedTimeStamp }
  val externalSystemTaskId: ExternalSystemTaskId? get() = state.get { externalSystemTaskId }
  val isSyncInProgress: Boolean get() = state.get { state.isInProgress }

  val syncResult: ProjectSystemSyncManager.SyncResult
    get() = state.get {
      when (state) {
        LastSyncState.IN_PROGRESS -> stateBeforeSyncStarted.toSyncResult()
        else -> state.toSyncResult()
      }
    }

  private class Holder {
    private val lock = ReentrantLock()
    private var state: HolderData = HolderData()

    inline fun <T> get(block: HolderData.() -> T): T    {
      return lock.withLock {
        state.block()
      }
    }

    inline fun set(block: HolderData.() -> HolderData)    {
      return lock.withLock {
        state = state.block()
      }
    }
  }

  private data class HolderData(
    val lastSyncedGradleVersion: GradleVersion? = null,
    val state: LastSyncState = LastSyncState.UNKNOWN,
    val stateBeforeSyncStarted: LastSyncState = LastSyncState.UNKNOWN,
    val externalSystemTaskId: ExternalSystemTaskId? = null,
    val lastSyncFinishedTimeStamp: Long = -1L
  )

  private val state: Holder = Holder()

  /**
   * Triggered at the start of a sync.
   */
  private fun syncStarted(trigger: GradleSyncStats.Trigger, rootProjectPath: @SystemIndependent String): Boolean {
    state.set {
      if (state.isInProgress) {
        LOG.warnWithDebug("Sync already in progress for project '${project.name}'.", Throwable())
        return@syncStarted false
      }
      copy(stateBeforeSyncStarted = state, state = LastSyncState.IN_PROGRESS)
    }

    LOG.info("Started ($trigger) sync with Gradle for project '${project.name}'.")

    eventLogger.syncStarted(GradleSyncStats.GradleSyncType.GRADLE_SYNC_TYPE_SINGLE_VARIANT, trigger)

    addToEventLog(SYNC_NOTIFICATION_GROUP, "Gradle sync started", MessageType.INFO, null)

    GradleFiles.getInstance(project).maybeProcessSyncStarted()

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_STARTED, rootProjectPath)
    syncPublisher() { syncStarted(project, rootProjectPath) }
    return true
  }

  /**
   * Triggered at the start of setup, after the models have been fetched.
   */
  private fun setupStarted(rootProjectPath: @SystemIndependent String) {
    eventLogger.setupStarted()

    LOG.info("Started setup of project '${project.name}'.")

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_SETUP_STARTED, rootProjectPath)
  }

  /**
   * Triggered at the end of a successful sync, once the models have been fetched.
   */
  private fun syncSucceeded(rootProjectPath: @SystemIndependent String) {
    val millisTook = eventLogger.syncEnded()

    val message = "Gradle sync finished in ${formatDuration(millisTook)}"
    addToEventLog(SYNC_NOTIFICATION_GROUP,
                  message,
                  MessageType.INFO,
                  null
    )
    LOG.info(message)

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED, rootProjectPath)

    syncFinished(LastSyncState.SUCCEEDED)
    syncPublisher { syncSucceeded(project, rootProjectPath) }
  }

  /**
   * Triggered when a sync has been found to have failed.
   */
  private fun syncFailed(message: String?, error: Throwable?, rootProjectPath: @SystemIndependent String) {
    val millisTook = eventLogger.syncEnded()
    val throwableMessage = error?.message
    // Find a none null message from either the provided message or the given throwable.
    val causeMessage: String = when {
      !message.isNullOrBlank() -> message
      !throwableMessage.isNullOrBlank() -> throwableMessage
      GradleSyncMessages.getInstance(project).errorDescription.isNotEmpty() -> GradleSyncMessages.getInstance(project).errorDescription
      else -> "Unknown cause".also { LOG.warn(IllegalStateException("No error message given")) }
    }
    val resultMessage = "Gradle sync failed in ${formatDuration(millisTook)}"
    addToEventLog(SYNC_NOTIFICATION_GROUP, resultMessage, MessageType.ERROR, null)
    LOG.warn("$resultMessage. $causeMessage")

    // Log the error to ideas log
    // Note: we log this as well as message above so the stack trace is present in the logs.
    if (error != null) LOG.warn(error)

    // If we are in use tests also log to stdout to help debugging.
    if (ApplicationManager.getApplication().isUnitTestMode) {
      println("***** sync error ${if (error == null) message else error.message}")
    }

    logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE, rootProjectPath)

    syncFinished(LastSyncState.FAILED)
    syncPublisher { syncFailed(project, causeMessage, rootProjectPath) }
  }

  /**
   * Triggered when a sync has been found to have been cancelled.
   */
  private fun syncCancelled(@Suppress("UNUSED_PARAMETER") rootProjectPath: @SystemIndependent String) {
    val resultMessage = "Gradle sync cancelled"
    addToEventLog(SYNC_NOTIFICATION_GROUP, resultMessage, MessageType.INFO, null)
    LOG.info(resultMessage)

    // TODO(b/239800883): logSyncEvent(AndroidStudioEvent.EventKind.GRADLE_SYNC_CANCELLED)

    // If the initial sync has been cancelled we do not have any models, but we cannot stay in the unknown state forever as it blocks
    // various UI features.
    val newStateAfterCancellation =
      state.get { stateBeforeSyncStarted.takeUnless { it == LastSyncState.UNKNOWN } ?: LastSyncState.FAILED }

    syncFinished(newStateAfterCancellation)
    syncPublisher { syncCancelled(project, rootProjectPath) }
  }

  /**
   * Triggered when a sync have been skipped, this happens when the project is setup by models from the cache.
   */
  fun syncSkipped(listener: GradleSyncListener?) {
    syncFinished(LastSyncState.SKIPPED)
    listener?.syncSkipped(project)
    syncPublisher { syncSkipped(project) }
  }

  fun isSyncNeeded(): ThreeState {
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

    state.set {
      copy(state = newState, externalSystemTaskId = null, lastSyncFinishedTimeStamp = System.currentTimeMillis())
    }

    PropertiesComponent.getInstance().setValue(ANDROID_GRADLE_SYNC_NEEDED_PROPERTY_NAME, !newState.isSuccessful)

    // TODO: Move out of GradleSyncState, create a ProjectCleanupTask to show this warning?
    if (newState != LastSyncState.SKIPPED) {
      ApplicationManager.getApplication().invokeLater { warnIfNotJdkHome() }
    }
  }

  fun recordGradleVersion(gradleVersion: GradleVersion) {
    state.set {
      copy(lastSyncedGradleVersion = gradleVersion)
    }
  }

  @UiThread
  private fun warnIfNotJdkHome() {
    if (project.isDisposed) return
    if (!IdeInfo.getInstance().isAndroidStudio) return
    if (!NotificationsConfigurationImpl.getSettings(JDK_LOCATION_WARNING_NOTIFICATION_GROUP.displayId).isShouldLog) return

    // Using the IdeSdks requires us to be on the dispatch thread
    ApplicationManager.getApplication().assertIsDispatchThread()

    val gradleInstallation = (GradleInstallationManager.getInstance() as AndroidStudioGradleInstallationManager)
    if (gradleInstallation.isUsingJavaHomeJdk(project)) {
      return
    }
    val quickFixes = mutableListOf<NotificationHyperlink>(OpenUrlHyperlink(JDK_LOCATION_WARNING_URL, "More info..."))
    val selectJdkHyperlink = SelectJdkFromFileSystemHyperlink.create(project)
    if (selectJdkHyperlink != null) quickFixes += selectJdkHyperlink
    quickFixes.add(DoNotShowJdkHomeWarningAgainHyperlink())

    val message = AndroidBundle.message("project.sync.warning.multiple.gradle.daemons",
      project.name,
      gradleInstallation.getGradleJvmPath(project, project.basePath.orEmpty()) ?: "Undefined",
      IdeSdks.getJdkFromJavaHome() ?: "Undefined"
    )
    addToEventLog(JDK_LOCATION_WARNING_NOTIFICATION_GROUP, message, MessageType.WARNING, quickFixes)
  }

  private val eventLogger = GradleSyncEventLogger()

  fun generateSyncEvent(eventKind: AndroidStudioEvent.EventKind, rootProjectPath: @SystemIndependent String): AndroidStudioEvent.Builder {
    return eventLogger.generateSyncEvent(project, rootProjectPath, eventKind)
  }

  /**
   * Logs a sync event using [UsageTracker]
   */
  private fun logSyncEvent(kind: AndroidStudioEvent.EventKind, rootProjectPath: @SystemIndependent String) {
    // Do not log an event if the project has been closed, working out the sync type for a disposed project results in
    // an error.
    if (project.isDisposed) return

    val event = eventLogger.generateSyncEvent(project, rootProjectPath, kind)

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
        resultMessage += "<br>${quickFix.toHtml()}"
      }
      listener = NotificationListener { _, event ->
        quickFixes.forEach { link -> link.executeIfClicked(project, event) }
      }
    }
    val notification = notificationGroup.createNotification("", resultMessage, type.toNotificationType())
    if (listener != null) notification.setListener(listener)
    notification.notify(project)
  }

  private fun syncPublisher(block: GradleSyncListenerWithRoot.() -> Unit) {
    fun publish() {
      with(project.messageBus.syncPublisher(GRADLE_SYNC_TOPIC)) { block() }
      // Publish to the project-system-wide topic after publishing to our internal topic unless it is an in-progress-state. There is no
      // reason for our callers to handle in-progress states and `SyncResultListener` has `syncEnded()` method only.
      if (state.get { !state.isInProgress }) {
        project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(syncResult)
      }
    }
    if (ApplicationManager.getApplication().isUnitTestMode) {
      publish()
    } else {
      invokeLaterIfProjectAlive(project, ::publish)
    }
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

    fun stopTrackingTask(id: ExternalSystemTaskId): @SystemIndependent String? {
      LOG.info("stopTrackingTask($id)")
      val info = runningTasks.remove(id)
      if (info == null) {
        LOG.warn("Unknown build $id finished")
        return null
      }
      Disposer.dispose(info.second) // Unsubscribe from notifications.
      return info.first
    }

    fun stopTrackingTask(projectDir: String): @SystemIndependent String? {
      LOG.info("stopTrackingTask($projectDir)")
      val normalizedProjectPath = normalizePath(projectDir)
      val task = runningTasks.entries.find { it.value.first == normalizedProjectPath }?.key ?: return null
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
      if (syncStateUpdaterService.stopTrackingTask(projectPath!!) != null) {
        GradleSyncStateHolder.getInstance(project).syncSucceeded(projectPath)
      }
    }

    @Suppress("UnstableApiUsage")
    override fun onImportFailed(projectPath: String?, t: Throwable) {
      LOG.info("onImportFailed($projectPath)")
      val syncStateUpdaterService = project.getService(SyncStateUpdaterService::class.java)
      if (syncStateUpdaterService.stopTrackingTask(projectPath!!) != null) {
        // If `onImportFailed` is called because of `ProcessCancelledException`, it results in `isCancelled == true`, and this is the way
        // we detect this case since we don't have access to the exception instance itself here.
        if (ProgressManager.getGlobalProgressIndicator()?.isCanceled == true) {
          ProgressManager.getInstance().executeNonCancelableSection {
            GradleSyncStateHolder.getInstance(project).syncCancelled(projectPath)
          }
        } else {
          // Unfortunately, the exact exception is not passed but this is an unexpected error indicating a bug in the code, and it is logged
          // as an error by the handler calling this callback in the IntelliJ platform. The error message below is used for logging and in
          // tests only, while the user still sees the actual exception in the build output window.
          GradleSyncStateHolder.getInstance(project).syncFailed("Failed to import project structure", null, projectPath)
        }
      }
    }
  }

  class SyncStateUpdater : ExternalSystemTaskNotificationListener, BuildProgressListener {

    private fun ExternalSystemTaskId.findProjectOrLog(): Project? {
      val project = findProject()
      if (project == null) {
        LOG.warn("No project found for $this")
      }
      return project
    }

    override fun onStart(id: ExternalSystemTaskId, workingDir: String) {
      if (!id.isGradleResolveProjectTask()) return
      val project = id.findProjectOrLog() ?: return
      val syncStateImpl = getInstance(project)
      syncStateImpl.state.set { copy(externalSystemTaskId = id) }
      LOG.info("onStart($id, $workingDir)")
      val syncStateUpdaterService = project.getService(SyncStateUpdaterService::class.java)
      val disposable = syncStateUpdaterService.trackTask(id, workingDir) ?: return
      val trigger =
        project.getProjectSyncRequest(workingDir)?.trigger
      if (!GradleSyncStateHolder.getInstance(project)
          .syncStarted(trigger ?: GradleSyncStats.Trigger.TRIGGER_UNKNOWN, rootProjectPath = workingDir)
      ) {
        stopTrackingTask(project, id)
        return
      }
      project.getService(SyncViewManager::class.java).addListener(this, disposable)
    }


    override fun onSuccess(id: ExternalSystemTaskId) {
      if (!id.isGradleResolveProjectTask()) return
      LOG.info("onSuccess($id)")
      val project = id.findProjectOrLog() ?: return
      val runningTasks = project.getService(SyncStateUpdaterService::class.java).runningTasks
      val (rootProjectPath, _) = runningTasks[id] ?: return
      GradleSyncStateHolder.getInstance(project).setupStarted(rootProjectPath)
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
      if (!id.isGradleResolveProjectTask()) return
      LOG.info("onFailure($id, $e)")
      val project = id.findProjectOrLog() ?: return
      val rootProjectPath = stopTrackingTask(project, id) ?: return
      GradleSyncStateHolder.getInstance(project).syncFailed(null, e, rootProjectPath)
    }

    override fun onStart(id: ExternalSystemTaskId) = error("Not expected to be called. onStart with a different signature is implemented")

    override fun onEnd(id: ExternalSystemTaskId) = Unit
    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) = Unit
    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) = Unit
    override fun beforeCancel(id: ExternalSystemTaskId) = Unit

    override fun onCancel(id: ExternalSystemTaskId) {
      if (!id.isGradleResolveProjectTask()) return
      LOG.info("onCancel($id)")
      val project = id.findProjectOrLog() ?: return
      val rootProjectPath = stopTrackingTask(project, id) ?: return

      GradleSyncStateHolder.getInstance(project).syncCancelled(rootProjectPath)
    }

    override fun onEvent(buildId: Any, event: BuildEvent) {
      if (event !is FinishBuildEvent) return
      if (buildId !is ExternalSystemTaskId) {
        LOG.warn("Unexpected buildId $buildId of type ${buildId::class.java} encountered")
        return
      }
      val project = buildId.findProjectOrLog() ?: return
      val rootProjectPath = stopTrackingTask(project, buildId) ?: return

      if (event.result is SuccessResult) {
        // A successful result at this point without first reaching `onImportFinished` currently means that data import was cancelled.
        LOG.info("Unreported sync success detected. Sync cancelled?")
        GradleSyncStateHolder.getInstance(project).syncCancelled(rootProjectPath)
      } else {

        // Regardless of the result report sync failure. A successful sync would have already removed the task via ProjectDataImportListener.
        val failure = event.result as? FailureResult
        val message = failure?.failures?.mapNotNull { it.message }?.joinToString(separator = "\n") ?: "Sync failed: reason unknown"
        val throwable = failure?.failures?.map { it.error }?.firstOrNull { it != null }

        // Log if a not yet finalised sync detected. An error in Gradle phase is supposed to be reported via `onFailure` method and
        // exceptions in data services are reported via `GradleSyncStateHolder.DataImportListener.onImportFailed`.
        LOG.error("Unreported sync failure detected", Throwable())

        GradleSyncStateHolder.getInstance(project).syncFailed(message, throwable, rootProjectPath)
      }
    }

    private fun stopTrackingTask(project: Project, buildId: ExternalSystemTaskId): @SystemIndependent String? {
      val syncStateUpdaterService = project.getService(SyncStateUpdaterService::class.java)
      return syncStateUpdaterService.stopTrackingTask(buildId)
    }
  }

  private fun GradleSyncStateHolder.LastSyncState.toSyncResult(): ProjectSystemSyncManager.SyncResult {
    return when (this) {
      LastSyncState.UNKNOWN -> ProjectSystemSyncManager.SyncResult.UNKNOWN
      GradleSyncStateHolder.LastSyncState.SKIPPED -> ProjectSystemSyncManager.SyncResult.SKIPPED
      GradleSyncStateHolder.LastSyncState.IN_PROGRESS -> ProjectSystemSyncManager.SyncResult.UNKNOWN
      GradleSyncStateHolder.LastSyncState.SUCCEEDED -> ProjectSystemSyncManager.SyncResult.SUCCESS
      GradleSyncStateHolder.LastSyncState.FAILED -> ProjectSystemSyncManager.SyncResult.FAILURE
    }
  }
}

private val PROJECT_SYNC_REQUESTS = Key.create<Map<String, GradleSyncInvoker.Request>>("PROJECT_SYNC_REQUESTS")

internal fun Project.getProjectSyncRequest(rootPath: String): GradleSyncInvoker.Request? {
  return getUserData(PROJECT_SYNC_REQUESTS)?.get(toSystemIndependentName(toSystemIndependentName(rootPath)))
}

@JvmName("setProjectSyncRequest")
internal fun Project.setProjectSyncRequest(rootPath: String, request: GradleSyncInvoker.Request?) {
  val systemIndependentRootPath = toSystemIndependentName(rootPath)
  do {
    val currentRequests = getUserData(PROJECT_SYNC_REQUESTS)
    val newRequests =
      when {
        request != null -> currentRequests.orEmpty() + (systemIndependentRootPath to request)
        else -> currentRequests.orEmpty() - systemIndependentRootPath
      }
  } while (!(this as UserDataHolderEx).replace(PROJECT_SYNC_REQUESTS, currentRequests, newRequests))
}

private fun ExternalSystemTaskId.isGradleResolveProjectTask() =
  projectSystemId == GRADLE_SYSTEM_ID && type == ExternalSystemTaskType.RESOLVE_PROJECT

private fun normalizePath(projectPath: String) = ExternalSystemApiUtil.toCanonicalPath(projectPath)

private val Any.LOG get() = Logger.getInstance(this::class.java)  // Used for non-frequent logging.
private const val ANDROID_GRADLE_SYNC_NEEDED_PROPERTY_NAME = "android.gradle.sync.needed"

/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.annotations.Trace
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.deploy.proto.Deploy
import com.android.tools.deploy.proto.Deploy.UnsupportedChange
import com.android.tools.deployer.AdbClient
import com.android.tools.deployer.AdbInstaller
import com.android.tools.deployer.Installer
import com.android.tools.deployer.MetricsRecorder
import com.android.tools.deployer.tasks.LiveUpdateDeployer
import com.android.tools.deployer.tasks.LiveUpdateDeployer.UpdateLiveEditError
import com.android.tools.deployer.tasks.LiveUpdateDeployer.UpdateLiveEditResult
import com.android.tools.deployer.tasks.LiveUpdateDeployer.UpdateLiveEditsParam
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugarResponse
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.android.tools.idea.run.deployment.liveedit.tokens.BuildSystemLiveEditServices.Companion.getApplicationLiveEditServices
import com.android.tools.idea.util.LocalInstallerPathManager.getLocalInstaller
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LiveEditEvent
import com.intellij.concurrency.JobScheduler
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.io.IOException
import java.util.Optional
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import org.jetbrains.kotlin.idea.KotlinFileType

// TODO: This class is open because there are tests that subclass it for some reason. Fix this.
open class LiveEditProjectMonitor(liveEditService: LiveEditService, private val project: Project) : Disposable {
  private val mainThreadExecutor = Executors.newSingleThreadScheduledExecutor()

  private var applicationLiveEditServices: ApplicationLiveEditServices? = null

  private var applicationProjectContext: ApplicationProjectContext? = null
  private val applicationId
    get() = applicationProjectContext?.applicationId

  /** Track the state of the project [project] on devices it has been deployed on as [applicationProjectContext]. */
  val liveEditDevices = LiveEditDevices()

  private val deviceWatcher = DeviceEventWatcher()

  // In manual mode, we buffer files until user triggers a LE push.
  private val bufferedFiles = mutableListOf<PsiFile>()

  // For every files a user modify, we keep track of whether we were able to successfully compile it. As long as one file has an error,
  // LE status remains in Paused state.
  private val filesWithCompilationErrors = mutableSetOf<String>()

  private val intermediateSyncs = AtomicReference(false)

  @VisibleForTesting val irClassCache = MutableIrClassCache()

  private val compiler = LiveEditCompiler(project, irClassCache)

  // We want to log only a percentage of LE events, but we also always want to log the *first* event after a deployment.
  private val leLogFraction = 0.1

  // Random generator used in conjunction with leLogFraction
  private val randomForLogging = Random()

  private var hasLoggedSinceReset = false

  // Bridge to ADB event (either ddmlib or adblib). We use it to receive device lifecycle events and app (a.k.a Client) lifecycle events.
  private val adbEventsListener = liveEditService.adbEventsListener

  // Care should be given when modifying this field to preserve atomicity.
  private val changedFileQueue = ConcurrentLinkedQueue<PsiFile>()

  private val psiSnapshots = ConcurrentHashMap<PsiFile, PsiState>()

  private val pendingRecompositionStatusPolls = AtomicInteger(0)

  init {
    Disposer.register(liveEditService, this)
    project.messageBus
      .connect(this)
      .subscribe(PROJECT_SYSTEM_SYNC_TOPIC, ProjectSystemSyncManager.SyncResultListener { intermediateSyncs.set(true) })

    // TODO: This maze of listeners is complicated. LiveEditDevices should directly implement LiveEditAdbEventsListener.
    deviceWatcher.addListener(liveEditDevices::handleDeviceLifecycleEvents)
    adbEventsListener.addListener(deviceWatcher)

    liveEditDevices.addListener {
      // Force the UI to redraw with the new status. See com.intellij.openapi.actionSystem.AnAction#update().
      ActivityTracker.getInstance().inc()
    }
  }

  @VisibleForTesting fun numFilesWithCompilationErrors() = filesWithCompilationErrors.size

  fun status(device: IDevice) = liveEditDevices.getInfo(device)?.status ?: LiveEditStatus.Disabled

  private fun processQueuedChanges() {
    if (changedFileQueue.isEmpty()) {
      return
    }

    val copy = mutableListOf<PsiFile>()
    changedFileQueue.removeIf { e ->
      copy.add(e)
      true
    }

    updateEditableStatus(LiveEditStatus.InProgress)

    if (!handleChangedMethods(project, copy)) {
      changedFileQueue.addAll(copy)
      val delay = LiveEditAdvancedConfiguration.getInstance().refreshRateMs.toLong()
      mainThreadExecutor.schedule({ this.processQueuedChanges() }, delay, TimeUnit.MILLISECONDS)
    }
  }

  override fun dispose() {
    // Don't leak deviceWatcher in our ADB bridge listeners.
    adbEventsListener.removeListener(deviceWatcher)
    changedFileQueue.clear()
    liveEditDevices.clear()
    deviceWatcher.clearListeners()
    mainThreadExecutor.shutdownNow()
  }

  /**
   * Notifies the monitor that a [com.intellij.execution.configurations.RunConfiguration] has just started execution.
   *
   * @param devices The devices that the execution will deploy to.
   * @return true if multi-deploy is detected, false otherwise (this will be removed once multi-deploy is supported)
   */
  fun notifyExecution(devices: Collection<IDevice>): Boolean {
    val newDevices = devices.toMutableSet()
    newDevices.removeIf { !supportLiveEdits(it) }
    val multiDeploy = Ref<Boolean>(false)
    liveEditDevices.update { oldDevice: IDevice, status: LiveEditStatus ->
      if (newDevices.contains(oldDevice)) {
        return@update if (status === LiveEditStatus.NoMultiDeploy) LiveEditStatus.Disabled else status
      }
      if (status === LiveEditStatus.Disabled) {
        return@update status
      }
      multiDeploy.set(true)
      LiveEditStatus.NoMultiDeploy
    }
    return multiDeploy.get()
  }

  // Called from Android Studio when an app is "Refreshed" (namely Apply Changes or Apply Code Changes) to a device
  fun notifyAppRefresh(device: IDevice): Boolean {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit || !supportLiveEdits(device)) {
      return false
    }
    liveEditDevices.update(device, LiveEditStatus.UpToDate)
    return true
  }

  // Called from Android Studio when an app is deployed (a.k.a Installed / IWIed / Delta-installed) to a device
  @Throws(ExecutionException::class, InterruptedException::class)
  fun notifyAppDeploy(
    applicationProjectContext: ApplicationProjectContext,
    device: IDevice,
    app: LiveEditApp,
    openFiles: List<VirtualFile>,
    isLiveEditable: Supplier<Boolean>,
  ): Boolean {
    if (!isLiveEditable.get()) {
      logger.info("Can not live edit the app due to either non-debuggability or does not use Compose")
      liveEditDevices.clear(device)
      return false
    }

    if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit) {
      if (supportLiveEdits(device) && LiveEditService.usesCompose(project)) {
        LiveEditService.getInstance(project).notifyLiveEditAvailability(device)
      }

      logger.info("Live Edit on device disabled via settings.")
      return false
    }

    if (!supportLiveEdits(device)) {
      logger.info(
        "Live edit not support for device API %d targeting app %s",
        device.getVersion().androidApiLevel,
        applicationProjectContext.applicationId,
      )
      liveEditDevices.addDevice(device, LiveEditStatus.UnsupportedVersion, app)
      return false
    }

    logger.info("Creating monitor for project %s targeting app %s", project.name, applicationProjectContext.applicationId)

    // Initialize EditStatus for current device.
    liveEditDevices.addDevice(device, LiveEditStatus.Loading, app)

    // This method (notifyAppDeploy) is called from Studio on a random Worker thread. We schedule the data update on the same Executor
    // we process our keystrokes {@link #methodChangesExecutor}
    val applicationLiveEditServices = applicationProjectContext.getApplicationLiveEditServices()
    if (applicationLiveEditServices == null) {
      logger.warning("Build system for live edit is not available for $applicationProjectContext")
      return false
    }

    mainThreadExecutor
      .submit {
        this.applicationProjectContext = applicationProjectContext
        this.applicationLiveEditServices = applicationLiveEditServices
        intermediateSyncs.set(false)

        bufferedFiles.clear()
        filesWithCompilationErrors.clear()
        compiler.resetState(applicationLiveEditServices)
        hasLoggedSinceReset = false

        // The app may have connected to ADB before we set up our ADB listeners.
        if (device.getClient(applicationId) != null) {
          updateEditStatus(device, LiveEditStatus.UpToDate)
        }
        deviceWatcher.setApplicationId(applicationId!!)

        psiSnapshots.clear()
        updatePsiSnapshots(openFiles)
        irClassCache.clear()
      }
      .get()

    return true
  }

  // Called when a new file is open in the editor. Only called on the class-differ code path.
  fun updatePsiSnapshot(file: VirtualFile) {
    if (!shouldLiveEdit()) {
      return
    }

    mainThreadExecutor.submit { updatePsiSnapshots(listOf(file)) }
  }

  private fun updatePsiSnapshots(files: List<VirtualFile>) =
    ReadAction.run<RuntimeException> {
      // We don't care about PSI validation for non-Kotlin files. The errors displayed for editing
      // non-Kotlin files during a Live Edit session are thrown later in the pipeline.
      files
        .mapNotNull { getPsiInProject(it) }
        .filter { it.fileType === KotlinFileType.INSTANCE && !psiSnapshots.containsKey(it) }
        .forEach { psiSnapshots[it] = getPsiValidationState(it) }
    }

  // Called when a file is modified. Only called on the class-differ code path.
  fun fileChanged(file: VirtualFile) {
    if (liveEditDevices.hasUnsupportedApi()) {
      liveEditDevices.update(LiveEditStatus.UnsupportedVersionOtherDevice)
      return
    }

    if (!shouldLiveEdit()) {
      return
    }

    if (file is LightVirtualFile) {
      // Ignore any in-memory file changes.
      logger.info("Ignoring LightVirtualFiles %s", file.name)
      return
    }

    mainThreadExecutor.submit {
      val psiFile = ReadAction.compute<PsiFile?, RuntimeException> { getPsiInProject(file) }
      if (psiFile == null) {
        return@submit
      }

      changedFileQueue.add(psiFile)

      if (project.getProjectSystem().getSyncManager().isSyncNeeded() || intermediateSyncs.get()) {
        updateEditStatus(LiveEditStatus.SyncNeeded)
        return@submit
      }
      processQueuedChanges()
    }
  }

  @RequiresReadLock
  private fun getPsiInProject(file: VirtualFile): PsiFile? {
    // Ignore files in closed projects, deleted files, or read-only files.
    if (project.isDisposed || !file.isValid || !file.isWritable) {
      return null
    }

    if (!ProjectFileIndex.getInstance(project).isInProject(file)) {
      return null
    }

    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile != null) {
      // Ensure that we have the original, VirtualFile-backed version of the file, since sometimes
      // an event is generated with a non-physical version of a given file, which will cause some
      // Live Edit checks that assume a non-null VirtualFile to fail.
      return psiFile.originalFile
    }
    return null
  }

  private fun shouldLiveEdit() =
    LiveEditApplicationConfiguration.getInstance().isLiveEdit &&
      !applicationId.isNullOrEmpty() &&
      applicationLiveEditServices != null && // support from the project system.
      !liveEditDevices.isUnrecoverable() &&
      !liveEditDevices.isDisabled()

  // Triggered from LiveEdit manual mode. Use buffered changes.
  @Trace
  fun onManualLETrigger() {
    mainThreadExecutor.schedule({ this.doOnManualLETrigger() }, 0, TimeUnit.MILLISECONDS)
  }

  @Trace
  fun onAgentTrigger(path: String, vibe: String?): String {
    if (liveEditDevices.devices().isEmpty()) {
      throw LiveEditUpdateException.internalErrorVibeEdit("No running application available for Live Edit.")
    }

    if (liveEditDevices.isDisabled()) {
      throw LiveEditUpdateException.internalErrorVibeEdit("Live Edit is not enabled.")
    }

    // We want to flush out all pending changes if we can.
    doOnManualLETrigger()

    val virtualFile =
      LocalFileSystem.getInstance().findFileByPath(path)
        ?: throw LiveEditUpdateException.internalErrorVibeEdit("$path not found in local file system.")

    val file = PsiManager.getInstance(project).findFile(virtualFile)

    // TODO: Add LiveEditEvent.Mode.AGENT_TOOL_VIBE
    while (!processChanges(project, listOf(file!!), LiveEditEvent.Mode.MANUAL, vibe)) {
      logger.info("Vibe Edit ProcessChanges was interrupted")
    }
    return "The runtime behavior has been changed. Check the running device."
  }

  @VisibleForTesting
  fun doOnManualLETrigger() {
    // If user to trigger a LE push twice in a row with compilation errors, the second trigger would set the state to "synced" even
    // though the compilation error prevented a push on the first trigger
    if (bufferedFiles.isEmpty()) {
      return
    }

    updateEditableStatus(LiveEditStatus.InProgress)

    val triggerMode = if (LiveEditService.isLeTriggerOnSave()) LiveEditEvent.Mode.ON_SAVE else LiveEditEvent.Mode.MANUAL
    while (!processChanges(project, bufferedFiles, triggerMode)) {
      logger.info("ProcessChanges was interrupted")
    }
    bufferedFiles.clear()
  }

  @Trace
  fun handleChangedMethods(project: Project, changedFiles: List<PsiFile>): Boolean {
    logger.info("Change detected for project %s targeting app %s", project.name, applicationId!!)

    // In manual mode, we store changes and update status but defer processing.
    if (LiveEditService.isLeTriggerManual()) {
      if (bufferedFiles.size < 2000) {
        bufferedFiles.addAll(changedFiles)
        updateEditableStatus(LiveEditStatus.OutOfDate)
      } else {
        // Something is wrong. Discard event otherwise we will run Out Of Memory
        updateEditableStatus(LiveEditStatus.createErrorStatus("Too many buffered LE keystrokes. Redeploy app."))
      }
      return true
    }
    return processChanges(project, changedFiles, LiveEditEvent.Mode.AUTO)
  }

  // Allows calling processChanges correctly on the main thread executor from a test context, to prevent hacks/concurrency bugs
  // that only appear in tests due to incorrectly calling processChanges on a thread other than the executor.
  @VisibleForTesting
  @Throws(Exception::class)
  fun processChangesForTest(project: Project, changedFiles: List<PsiFile>, mode: LiveEditEvent.Mode): Boolean {
    return mainThreadExecutor.submit<Boolean> { processChanges(project, changedFiles, mode) }.get()
  }

  // Waits for the LE main thread to complete all previously scheduled work. Not perfectly reliable due to retry logic, and the
  // existence of both this and processChangesForTest strongly imply a need to refactor our threading to make it testable, but that's not
  // a high priority right now.
  @VisibleForTesting
  @Throws(Exception::class)
  fun waitForThreadInTest(timeoutMillis: Long) {
    mainThreadExecutor.submit {}.get(timeoutMillis, TimeUnit.MILLISECONDS)
  }

  private fun processChanges(project: Project, changedFiles: List<PsiFile>, mode: LiveEditEvent.Mode): Boolean {
    return processChanges(project, changedFiles, mode, null)
  }

  @Trace
  /** @return true is the changes were successfully processed (without being interrupted). Otherwise, false. */
  private fun processChanges(project: Project, changedFiles: List<PsiFile>, mode: LiveEditEvent.Mode, vibe: String?): Boolean {
    if (vibe != null && changedFiles.size != 1) {
      // This is unlikely to happen.
      throw LiveEditUpdateException.internalErrorVibeEdit("Vibe Edit mode only support one change at time.")
    }

    val event = LiveEditEvent.newBuilder().setMode(mode)
    val start = System.nanoTime()
    val compiled: Optional<LiveEditDesugarResponse>
    val psiManager = PsiDocumentManager.getInstance(project)
    try {
      prebuildChecks(project, changedFiles)

      val inputs = mutableListOf<LiveEditCompilerInput>()
      for (file in changedFiles) {
        // The PSI might not update immediately after a file is edited. Interrupt until all changes are committed to the PSI.
        val doc = psiManager.getDocument(file)
        if (doc != null && psiManager.isUncommited(doc)) {
          return false
        }

        val state = psiSnapshots[file]
        inputs.add(LiveEditCompilerInput(file, state, vibe))
      }

      // TODO: Set unrestricted to true if mode == ON_VIBE
      val unrestricted = LiveEditAdvancedConfiguration.getInstance().allowClassStructuralRedefinition
      val apiLevels = editableDeviceIterator().mapNotNull { liveEditDevices.getInfo(it)?.app?.minAPI }.toSet()

      compiled = compiler.compile(inputs, !LiveEditService.isLeTriggerManual(), unrestricted, apiLevels)
      if (compiled.isEmpty) {
        return false
      }

      // Remove files successfully compiled from the error set.
      changedFiles.forEach { filesWithCompilationErrors.remove(it.name) }
    } catch (e: LiveEditUpdateException) {
      val recoverable = e.error.recoverable

      // The FIRST thing we should do is update the status and do the bookkeeping task after.
      // Otherwise, if any of bookkeeping step causes a crash, status is not updated, and we get an infinite spinner.
      val status =
        if (recoverable) {
          LiveEditStatus.createPausedStatus(errorMessage(e))
        } else {
          LiveEditStatus.createRerunnableErrorStatus(errorMessage(e))
        }
      updateEditableStatus(status)

      if (recoverable) {
        changedFiles.forEach { filesWithCompilationErrors.add(it.name) }
      }

      // We log all unrecoverable events, ignoring easily recoverable syntax / type errors that happens way too common during editing.
      // Both inlining restriction should are also logged despite being recoverable as well.
      if (
        e.error == LiveEditUpdateException.Error.UNABLE_TO_INLINE ||
          e.error == LiveEditUpdateException.Error.NON_PRIVATE_INLINE_FUNCTION ||
          !recoverable
      ) {
        event.status = e.error.metric
        logLiveEditEvent(event)
      }

      // Even though we have UI to properly display the errors, it is important that we log
      // LiveUpdateException at least ONCE. This is useful for bug reports as well as test flakes
      // where we have an unexpected update error.
      logger.warning("Live Edit Update Error %s %s", e.message, e.details)
      return true
    }

    val errorFilename = filesWithCompilationErrors.firstOrNull()
    if (mode == LiveEditEvent.Mode.AUTO && errorFilename != null) {
      // When we are only confined to the current file, we are not going to check if there are errors in other files.
      if (!StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS.get()) {
        val errorMsg = leErrorMessage(LiveEditUpdateException.Error.COMPILATION_ERROR, errorFilename)
        updateEditStatus(LiveEditStatus.createPausedStatus(errorMsg))
        return true
      }
    }

    val desugaredResponse = compiled.get()
    event.hasNonCompose = desugaredResponse.hasNonComposeChanges

    val compileFinish = System.nanoTime()
    event.compileDurationMs = TimeUnit.NANOSECONDS.toMillis(compileFinish - start)
    logger.info("LiveEdit compile completed in %dms", event.compileDurationMs)

    val errors = editableDeviceIterator().map { pushUpdatesToDevice(applicationId!!, it, desugaredResponse).errors }.flatMap { it }

    val devices = liveEditDevices.devices()
    event.targetDevice =
      if (devices.isEmpty()) {
        LiveEditEvent.Device.NONE
      } else if (devices.size > 1) {
        LiveEditEvent.Device.MULTI
      } else if (devices.first().isEmulator) {
        LiveEditEvent.Device.EMULATOR
      } else {
        LiveEditEvent.Device.PHYSICAL
      }

    if (errors.isEmpty()) {
      event.status = LiveEditEvent.Status.SUCCESS
      desugaredResponse.compilerOutput.irClasses.forEach { irClassCache.update(it) }
    } else {
      event.status = errorToStatus(errors[0])
    }

    val pushFinish = System.nanoTime()
    event.pushDurationMs = TimeUnit.NANOSECONDS.toMillis(pushFinish - compileFinish)
    logger.info("LiveEdit push completed in %dms", event.pushDurationMs)

    logLiveEditEvent(event)
    return true
  }

  private fun pushUpdatesToDevice(applicationId: String, device: IDevice, update: LiveEditDesugarResponse): UpdateLiveEditResult {
    val deployer = LiveUpdateDeployer(logger)
    val installer = newInstaller(device)
    val adb = AdbClient(device, logger)

    val config = LiveEditAdvancedConfiguration.getInstance()
    val useDebugMode = config.useDebugMode
    val useStructureRedefinition = config.allowClassStructuralRedefinition

    val apiLevel = liveEditDevices.getInfo(device)!!.app!!.minAPI
    val param =
      UpdateLiveEditsParam(
        update.classes(apiLevel),
        update.supportClasses(apiLevel),
        update.groupIds,
        update.invalidateMode,
        useDebugMode,
        useStructureRedefinition,
      )

    val result = deployer.updateLiveEdit(installer, adb, applicationId, param)

    if (filesWithCompilationErrors.isEmpty() || StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS.get()) {
      updateEditStatus(device, LiveEditStatus.UpToDate)
    } else {
      val errorFilename = filesWithCompilationErrors.stream().sequential().findFirst()
      val errorMsg = leErrorMessage(LiveEditUpdateException.Error.COMPILATION_ERROR, errorFilename.get())
      updateEditStatus(device, LiveEditStatus.createPausedStatus(errorMsg))
    }
    scheduleErrorPolling(deployer, installer, adb, applicationId)

    if (result.errors.isNotEmpty()) {
      val firstProblem = result.errors[0]
      if (firstProblem.type == Deploy.UnsupportedChange.Type.UNSUPPORTED_COMPOSE_VERSION) {
        updateEditStatus(device, LiveEditStatus.createComposeVersionError(firstProblem.getMessage()))
      } else {
        updateEditStatus(device, LiveEditStatus.createRerunnableErrorStatus(firstProblem.getMessage()))
      }
    }
    return result
  }

  fun requestRerun() {
    // This is triggered when Live Edit is just toggled on. Since the last deployment didn't start the Live Edit service,
    // we will fetch all the running devices and change every one of them to be outdated.
    for (device in AndroidDebugBridge.getBridge()!!.getDevices()) {
      liveEditDevices.addDevice(device, LiveEditStatus.createRerunnableErrorStatus("Re-run application to start Live Edit updates."))
    }
  }

  @VisibleForTesting
  fun scheduleErrorPolling(deployer: LiveUpdateDeployer, installer: Installer?, adb: AdbClient, packageName: String?) {
    if (pendingRecompositionStatusPolls.getAndSet(NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT) < 1) {
      scheduleNextErrorPolling(deployer, installer, adb, packageName)
    }
  }

  private fun scheduleNextErrorPolling(deployer: LiveUpdateDeployer, installer: Installer?, adb: AdbClient, packageName: String?) {
    val scheduler = JobScheduler.getScheduler()

    scheduler.schedule(
      {
        val pollsLeft = pendingRecompositionStatusPolls.decrementAndGet()
        try {
          val errors = deployer.retrieveComposeStatus(installer, adb, packageName)
          if (errors.isNotEmpty()) {
            val error = errors[0]
            updateEditableStatus(LiveEditStatus.createRecomposeErrorStatus(error.exceptionClassName, error.message, error.recoverable))
          }
          if (pollsLeft > 0) {
            scheduleNextErrorPolling(deployer, installer, adb, packageName)
          }
        } catch (e: IOException) {
          updateEditableStatus(LiveEditStatus.createRecomposeRetrievalErrorStatus(e))
          logger.warning(e.toString())
        }
      },
      2,
      TimeUnit.SECONDS,
    )
  }

  fun clearDevices() {
    liveEditDevices.clear()
  }

  private fun errorToStatus(error: UpdateLiveEditError): LiveEditEvent.Status {
    return when (error.type) {
      UnsupportedChange.Type.ADDED_METHOD -> LiveEditEvent.Status.UNSUPPORTED_ADDED_METHOD
      UnsupportedChange.Type.REMOVED_METHOD -> LiveEditEvent.Status.UNSUPPORTED_REMOVED_METHOD
      UnsupportedChange.Type.ADDED_CLASS -> LiveEditEvent.Status.UNSUPPORTED_ADDED_CLASS
      UnsupportedChange.Type.ADDED_FIELD,
      UnsupportedChange.Type.MODIFIED_FIELD -> LiveEditEvent.Status.UNSUPPORTED_ADDED_FIELD
      UnsupportedChange.Type.REMOVED_FIELD -> LiveEditEvent.Status.UNSUPPORTED_REMOVED_FIELD
      UnsupportedChange.Type.MODIFIED_SUPER,
      UnsupportedChange.Type.ADDED_INTERFACE,
      UnsupportedChange.Type.REMOVED_INTERFACE -> LiveEditEvent.Status.UNSUPPORTED_MODIFY_INHERITANCE
      UnsupportedChange.Type.UNSUPPORTED_COMPOSE_VERSION -> LiveEditEvent.Status.UNSUPPORTED_COMPOSE_RUNTIME_VERSION
      else -> LiveEditEvent.Status.UNKNOWN_LIVE_UPDATE_DEPLOYER_ERROR
    }
  }

  private fun logLiveEditEvent(event: LiveEditEvent.Builder) {
    if (!hasLoggedSinceReset || randomForLogging.nextDouble() < leLogFraction) {
      UsageTracker.log(
        AndroidStudioEvent.newBuilder()
          .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
          .setKind(AndroidStudioEvent.EventKind.LIVE_EDIT_EVENT)
          .setLiveEditEvent(event)
          .withProjectId(project)
      )
      hasLoggedSinceReset = true
    }
  }

  private fun updateEditStatus(device: IDevice, status: LiveEditStatus) = liveEditDevices.update(device, status)

  private fun updateEditStatus(status: LiveEditStatus) = liveEditDevices.update(status)

  // TODO: This function is extended in tests. Fix this.
  @VisibleForTesting
  open fun updateEditableStatus(newStatus: LiveEditStatus) {
    liveEditDevices.update { _: IDevice, prevStatus: LiveEditStatus ->
      if (prevStatus.unrecoverable() || prevStatus === LiveEditStatus.Disabled || prevStatus === LiveEditStatus.NoMultiDeploy) {
        prevStatus
      } else {
        newStatus
      }
    }
  }

  private fun editableDeviceIterator(): List<IDevice> {
    return liveEditDevices.devices().filter {
      if (!it.isOnline) return@filter false
      val info = liveEditDevices.getInfo(it) ?: return@filter false
      info.status !== LiveEditStatus.Disabled && info.status !== LiveEditStatus.NoMultiDeploy
    }
  }

  @VisibleForTesting fun isGradleSyncNeeded() = project.getProjectSystem().getSyncManager().isSyncNeeded() || intermediateSyncs.get()

  companion object {
    private val logger = LogWrapper(Logger.getInstance(LiveEditProjectMonitor::class.java))

    @VisibleForTesting val NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT = 5

    fun supportLiveEdits(device: IDevice) = device.getVersion().isAtLeast(AndroidVersion.VersionCodes.R)

    @VisibleForTesting
    fun newInstaller(device: IDevice): Installer {
      val metrics = MetricsRecorder()
      val adb = AdbClient(device, logger)
      return AdbInstaller(getLocalInstaller(), adb, metrics.deployMetrics, logger, AdbInstaller.Mode.DAEMON)
    }
  }
}

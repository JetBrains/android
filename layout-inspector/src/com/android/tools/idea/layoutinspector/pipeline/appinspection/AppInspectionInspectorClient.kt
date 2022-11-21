/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.annotations.concurrency.Slow
import com.android.repository.Revision
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoPackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.InspectorConnectionError
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.skia.SkiaParserImpl
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.StudioProgressRunner
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.EnumSet
import java.util.concurrent.TimeUnit

@com.google.common.annotations.VisibleForTesting
const val API_29_BUG_MESSAGE = "Live Inspection not available on this system image revision."
@com.google.common.annotations.VisibleForTesting
const val API_29_BUG_UPGRADE = "Please update to the latest revision."
@com.google.common.annotations.VisibleForTesting
const val MIN_API_29_GOOGLE_APIS_SYSIMG_REV = 12
@com.google.common.annotations.VisibleForTesting
const val MIN_API_29_AOSP_SYSIMG_REV = 8

/**
 * An [InspectorClient] that talks to an app-inspection based inspector running on a target device.
 *
 * @param apiServices App inspection services used for initializing and shutting down app
 *     inspection-based inspectors.
 */
class AppInspectionInspectorClient(
  process: ProcessDescriptor,
  isInstantlyAutoConnected: Boolean,
  private val model: InspectorModel,
  private val metrics: LayoutInspectorSessionMetrics,
  private val treeSettings: TreeSettings,
  parentDisposable: Disposable,
  @TestOnly private val apiServices: AppInspectionApiServices = AppInspectionDiscoveryService.instance.apiServices,
  @TestOnly private val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
) : AbstractInspectorClient(APP_INSPECTION_CLIENT, model.project, process, isInstantlyAutoConnected,
                            SessionStatisticsImpl(APP_INSPECTION_CLIENT), parentDisposable) {

  private var viewInspector: ViewLayoutInspectorClient? = null
  private lateinit var propertiesProvider: AppInspectionPropertiesProvider
  private val scope = AndroidCoroutineScope(this)

  /** Compose inspector, may be null if user's app isn't using the compose library. */
  @VisibleForTesting
  var composeInspector: ComposeLayoutInspectorClient? = null
    private set

  private val loggingExceptionHandler = CoroutineExceptionHandler { _, t ->
    fireError(t.message!!)
  }

  private val bannerExceptionHandler = CoroutineExceptionHandler { ctx, t ->
    loggingExceptionHandler.handleException(ctx, t)

    val message = when {
      t is ConnectionFailedException -> t.message!!
      process.device.apiLevel >= 29 -> {
        logUnexpectedError(InspectorConnectionError(t))
        AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY)
      }
      else -> {
        logUnexpectedError(InspectorConnectionError(t))
        "Unknown error"
      }
    }

    InspectorBannerService.getInstance(model.project)?.setNotification(message)
  }

  private var debugViewAttributesChanged = false

  override val capabilities =
    EnumSet.of(Capability.SUPPORTS_CONTINUOUS_MODE,
               Capability.SUPPORTS_SYSTEM_NODES,
               Capability.SUPPORTS_SKP)!!

  private val skiaParser = SkiaParserImpl(
    {
      viewInspector?.updateScreenshotType(LayoutInspectorViewProtocol.Screenshot.Type.BITMAP)
      capabilities.remove(Capability.SUPPORTS_SKP)
    })

  override val treeLoader: TreeLoader = AppInspectionTreeLoader(
    model.project,
    logEvent = ::logEvent,
    skiaParser
  )
  override val provider: PropertiesProvider
    get() = propertiesProvider
  override val isCapturing: Boolean
    get() = InspectorClientSettings.isCapturingModeOn

  override fun doConnect(): ListenableFuture<Nothing> {
    val future = SettableFuture.create<Nothing>()
    try {
      checkApi29Version(process, model.project, sdkHandler)
    }
    catch (exception: ConnectionFailedException) {
      future.setException(exception)
      return future
    }

    val exceptionHandler = CoroutineExceptionHandler { ctx, t ->
      bannerExceptionHandler.handleException(ctx, t)
      future.setException(t)
    }
    scope.launch(exceptionHandler) {
      logEvent(DynamicLayoutInspectorEventType.ATTACH_REQUEST)

      // Create the app inspection connection now, so we can log that it happened.
      apiServices.attachToProcess(process, model.project.name)
      launchMonitor.updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.ATTACH_SUCCESS)

      composeInspector = ComposeLayoutInspectorClient.launch(apiServices, process, model, treeSettings, capabilities, launchMonitor,
                                                             ::logComposeAttachError)
      val viewIns = ViewLayoutInspectorClient.launch(apiServices, process, model, stats, scope, composeInspector,
                                                     ::fireError, ::fireTreeEvent, launchMonitor)
      propertiesProvider = AppInspectionPropertiesProvider(viewIns.propertiesCache, composeInspector?.parametersCache, model)
      viewInspector = viewIns

      logEvent(DynamicLayoutInspectorEventType.ATTACH_SUCCESS)

      debugViewAttributesChanged = DebugViewAttributes.getInstance().set(model.project, process)
      if (debugViewAttributesChanged && !isInstantlyAutoConnected) {
        showActivityRestartedInBanner(model.project, process)
      }

      lateinit var updateListener: (AndroidWindow?, AndroidWindow?, Boolean) -> Unit
      updateListener = { _, _, _ ->
        future.set(null)
        model.modificationListeners.remove(updateListener)
      }
      model.modificationListeners.add(updateListener)
      if (isCapturing) {
        startFetchingInternal()
      }
      else {
        refreshInternal()
      }
    }
    return future
  }

  override fun doDisconnect(): ListenableFuture<Nothing> {
    val future = SettableFuture.create<Nothing>()
    // Create a new scope since we might be disconnecting because the original one died.
    model.project.coroutineScope.createChildScope(true).launch(loggingExceptionHandler) {
      val debugViewAttributes = DebugViewAttributes.getInstance()
      if (debugViewAttributesChanged && !debugViewAttributes.usePerDeviceSettings()) {
        debugViewAttributes.clear(model.project, process)
      }
      viewInspector?.disconnect()
      composeInspector?.disconnect()
      skiaParser.shutdown()
      logEvent(DynamicLayoutInspectorEventType.SESSION_DATA)

      future.set(null)
    }
    return future
  }

  override fun startFetching() =
    scope.launch(bannerExceptionHandler) {
      startFetchingInternal()
    }.asCompletableFuture()

  private suspend fun startFetchingInternal() {
    stats.currentModeIsLive = true
    viewInspector?.startFetching(continuous = true)
  }

  override fun stopFetching() =
    scope.launch(loggingExceptionHandler) {
      // Reset the scale to 1 to support zooming while paused, and get an SKP if possible.
      if (capabilities.contains(Capability.SUPPORTS_SKP)) {
        updateScreenshotType(AndroidWindow.ImageType.SKP, 1.0f)
      }
      else {
        viewInspector?.updateScreenshotType(null, 1.0f)
      }
      stats.currentModeIsLive = false
      viewInspector?.stopFetching()
    }.asCompletableFuture()

  override fun refresh() {
    scope.launch(loggingExceptionHandler) {
      refreshInternal()
    }
  }

  private fun logEvent(eventType: DynamicLayoutInspectorEventType) {
    metrics.logEvent(eventType, stats)
  }

  private fun logComposeAttachError(errorCode: AttachErrorCode) {
    stats.composeAttachError(errorCode)
  }

  private suspend fun refreshInternal() {
    stats.currentModeIsLive = false
    viewInspector?.startFetching(continuous = false)
  }

  override fun updateScreenshotType(type: AndroidWindow.ImageType?, scale: Float) {
    if (model.pictureType != type || scale >= 0f) {
      viewInspector?.updateScreenshotType(type?.protoType, scale)
    }
  }

  override fun addDynamicCapabilities(dynamicCapabilities: Set<Capability>) {
    capabilities.addAll(dynamicCapabilities)
  }

  fun updateRecompositionCountSettings() {
    scope.launch(loggingExceptionHandler) {
      composeInspector?.updateSettings()
    }
  }

  @Slow
  override fun saveSnapshot(path: Path) {
    val startTime = System.currentTimeMillis()
    val metadata = viewInspector?.saveSnapshot(path)
    metadata?.saveDuration = System.currentTimeMillis() - startTime
    // Use a separate metrics instance since we don't want the snapshot metadata to hang around
    val saveMetrics = LayoutInspectorSessionMetrics(model.project, snapshotMetadata = metadata)
    saveMetrics.logEvent(DynamicLayoutInspectorEventType.SNAPSHOT_CAPTURED, stats)
  }

  private fun checkApi29Version(
    process: ProcessDescriptor,
    project: Project,
    sdkHandler: AndroidSdkHandler
  ) {
    val (success, image) = checkSystemImageForAppInspectionCompatibility(process, project, sdkHandler)
    if (success || image == null) {
      return
    }

    val tags = (image.typeDetails as? DetailsTypes.SysImgDetailsType)?.tags ?: listOf()

    val bannerService = InspectorBannerService.getInstance(project) ?: return
    if (tags.contains(SystemImage.GOOGLE_APIS_TAG) || tags.contains(SystemImage.DEFAULT_TAG)) {
      val logger = StudioLoggerProgressIndicator(AppInspectionInspectorClient::class.java)
      val showBanner = RepoManager.RepoLoadedListener { packages ->
        val message: String
        val actions: List<AnAction>
        val remote = packages.consolidatedPkgs[image.path]?.remote
        if (remote != null &&
            ((tags.contains(SystemImage.GOOGLE_APIS_TAG) && remote.version >= Revision(MIN_API_29_GOOGLE_APIS_SYSIMG_REV)) ||
             (tags.contains(SystemImage.DEFAULT_TAG) && remote.version >= Revision(MIN_API_29_AOSP_SYSIMG_REV)))) {
          message = "$API_29_BUG_MESSAGE $API_29_BUG_UPGRADE"
          actions = listOf(object : AnAction("Download Update") {
            override fun actionPerformed(e: AnActionEvent) {
              if (SdkQuickfixUtils.createDialogForPaths(project, listOf(image.path))?.showAndGet() == true) {
                Messages.showInfoMessage(project, "Please restart the emulator for update to take effect.", "Restart Required")
              }
            }
          }, bannerService.DISMISS_ACTION)
        }
        else {
          message = API_29_BUG_MESSAGE
          actions = listOf(bannerService.DISMISS_ACTION)
        }
        bannerService.setNotification(message, actions)
      }
      sdkHandler.getSdkManager(logger).load(0, null, listOf(showBanner), null,
                                            StudioProgressRunner(false, false, "Checking available system images", null),
                                            StudioDownloader(), StudioSettingsController.getInstance())
    }
    else {
      bannerService.setNotification(API_29_BUG_MESSAGE, listOf(bannerService.DISMISS_ACTION))
    }
    throw ConnectionFailedException("Unsupported system image revision", AttachErrorCode.LOW_API_LEVEL)
  }
}

/**
 * Check whether the current target's system image is compatible with app inspection.
 */
fun checkSystemImageForAppInspectionCompatibility(
  process: ProcessDescriptor,
  project: Project,
  sdkHandler: AndroidSdkHandler
): Pair<Boolean, RepoPackage?> {
  if (process.device.isEmulator && process.device.apiLevel == 29) {
    val adb = AdbUtils.getAdbFuture(project).get()
    val avdName = adb?.devices?.find { it.serialNumber == process.device.serial }?.avdData?.get(1, TimeUnit.SECONDS)?.name
    val avd = avdName?.let { AvdManagerConnection.getAvdManagerConnection(sdkHandler).findAvd(avdName) }
    val imagePackage = (avd?.systemImage as? SystemImage)?.`package`
    if (imagePackage != null) {
      if ((SystemImage.GOOGLE_APIS_TAG == avd.tag && imagePackage.version < Revision(MIN_API_29_GOOGLE_APIS_SYSIMG_REV)) ||
          (SystemImage.DEFAULT_TAG == avd.tag && imagePackage.version < Revision(MIN_API_29_AOSP_SYSIMG_REV) ||
           // We don't know when the play store images will be updated yet
           SystemImage.PLAY_STORE_TAG == avd.tag)
      ) {
        return Pair(false, imagePackage)
      }
    }
  }
  return Pair(true, null)
}

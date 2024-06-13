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
import com.android.sdklib.SystemImageTags
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCrashException
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatisticsImpl
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.AbstractInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.InspectorConnectionError
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.appinspection.Compatibility.NotCompatible.Reason.API_29_PLAY_STORE
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.skia.SkiaParserImpl
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.sdk.AndroidSdks
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType.APP_INSPECTION_CLIENT
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel.Status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

const val SYSTEM_IMAGE_LIVE_UNSUPPORTED_KEY = "system.image.live.unsupported"
@com.google.common.annotations.VisibleForTesting const val MIN_API_29_GOOGLE_APIS_SYSIMG_REV = 12
@com.google.common.annotations.VisibleForTesting const val MIN_API_29_AOSP_SYSIMG_REV = 8

/**
 * An [InspectorClient] that talks to an app-inspection based inspector running on a target device.
 *
 * @param apiServices App inspection services used for initializing and shutting down app
 *   inspection-based inspectors.
 */
class AppInspectionInspectorClient(
  process: ProcessDescriptor,
  private val model: InspectorModel,
  notificationModel: NotificationModel,
  private val metrics: LayoutInspectorSessionMetrics,
  private val treeSettings: TreeSettings,
  private val inspectorClientSettings: InspectorClientSettings,
  coroutineScope: CoroutineScope,
  parentDisposable: Disposable,
  @TestOnly
  private val apiServices: AppInspectionApiServices =
    AppInspectionDiscoveryService.instance.apiServices,
  @TestOnly
  private val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler(),
  private val debugViewAttributes: DebugViewAttributes = DebugViewAttributes(model.project),
) :
  AbstractInspectorClient(
    APP_INSPECTION_CLIENT,
    model.project,
    notificationModel,
    process,
    SessionStatisticsImpl(APP_INSPECTION_CLIENT),
    coroutineScope,
    parentDisposable,
  ) {

  private var viewInspector: ViewLayoutInspectorClient? = null
  private lateinit var propertiesProvider: AppInspectionPropertiesProvider

  /** Compose inspector, may be null if user's app isn't using the compose library. */
  @VisibleForTesting
  var composeInspector: ComposeLayoutInspectorClient? = null
    private set

  private val loggingExceptionHandler = CoroutineExceptionHandler { _, t ->
    notifyError(t)
    logError(t)
  }

  override val capabilities =
    EnumSet.of(
      Capability.SUPPORTS_CONTINUOUS_MODE,
      Capability.SUPPORTS_SYSTEM_NODES,
      Capability.SUPPORTS_SKP,
    )!!

  private val skiaParser =
    SkiaParserImpl({
      viewInspector?.updateScreenshotType(LayoutInspectorViewProtocol.Screenshot.Type.BITMAP)
      capabilities.remove(Capability.SUPPORTS_SKP)
    })

  override val treeLoader: TreeLoader =
    AppInspectionTreeLoader(notificationModel, logEvent = ::logEvent, skiaParser)
  override val provider: PropertiesProvider
    get() = propertiesProvider

  override val inLiveMode: Boolean
    get() = inspectorClientSettings.inLiveMode

  override suspend fun doConnect() {
    // we run this function outside the runCatching because it sets a banner in case of exception.
    // We don't want the runCatching to handle it.
    checkApi29Version(process, model.project, sdkHandler)

    runCatching {
        logEvent(DynamicLayoutInspectorEventType.ATTACH_REQUEST)

        // Create the app inspection connection now, so we can log that it happened.
        apiServices.attachToProcess(process, model.project.name)
        launchMonitor.updateProgress(
          DynamicLayoutInspectorErrorInfo.AttachErrorState.ATTACH_SUCCESS
        )

        composeInspector =
          ComposeLayoutInspectorClient.launch(
            apiServices,
            process,
            model,
            notificationModel,
            treeSettings,
            capabilities,
            launchMonitor,
            ::logComposeAttachError,
          )
        val viewIns =
          ViewLayoutInspectorClient.launch(
            apiServices,
            process,
            model,
            stats,
            coroutineScope,
            composeInspector,
            this::notifyError,
            ::fireRootsEvent,
            ::fireTreeEvent,
            launchMonitor,
          )
        propertiesProvider =
          AppInspectionPropertiesProvider(
            viewIns.propertiesCache,
            composeInspector?.parametersCache,
            model,
          )
        viewInspector = viewIns

        logEvent(DynamicLayoutInspectorEventType.ATTACH_SUCCESS)

        when (val setFlagResult = debugViewAttributes.set(process.device)) {
          is SetFlagResult.Set -> {
            if (!setFlagResult.previouslySet) {
              // Show the banner only if debugViewAttributes has changed.
              showActivityRestartedInBanner(notificationModel)
            }
          }
          is SetFlagResult.Failure -> {
            showUnableToSetDebugViewAttributesBanner(notificationModel)
          }
          is SetFlagResult.Cancelled -> {}
        }

        val completableDeferred = CompletableDeferred<Unit>()
        val updateListener: (AndroidWindow?, AndroidWindow?, Boolean) -> Unit = { _, _, _ ->
          completableDeferred.complete(Unit)
        }

        model.addModificationListener(updateListener)

        if (inspectorClientSettings.disableBitmapScreenshot) {
          disableBitmapScreenshots(true)
        }

        if (inLiveMode) {
          startFetchingInternal()
        } else {
          refreshInternal()
        }

        // wait until we start receiving updates
        completableDeferred.await()
        model.removeModificationListener(updateListener)
      }
      .recover { t ->
        val error = getOriginalError(t)
        notifyError(error)
        val expectedError = handleConnectionError(error)
        if (!expectedError) {
          logError(error)
        }
        throw t
      }
  }

  /**
   * [CancellationException] can hide other errors from App Inspection. Which can be stored one or
   * more level deep, for example `t.cause.cause`. This function finds that error or returns the
   * original one if it can't be found.
   */
  private fun getOriginalError(t: Throwable): Throwable {
    return if (t is CancellationException) {
      var originalCause = t.cause
      while (originalCause != null && originalCause is CancellationException) {
        originalCause = originalCause.cause
      }
      originalCause ?: t
    } else {
      t
    }
  }

  /**
   * Handles a connection error.
   *
   * @return true if the error is expected, false if it's unexpected.
   */
  private fun handleConnectionError(throwable: Throwable): Boolean {
    if (throwable is CancellationException) {
      return true
    }

    val errorCode = throwable.toAttachErrorInfo().code
    launchMonitor.logAttachErrorToMetrics(errorCode)

    return errorCode != AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR &&
      errorCode != AttachErrorCode.UNEXPECTED_ERROR
  }

  private fun logError(throwable: Throwable) {
    when (throwable) {
      is CancellationException -> {}
      is ConnectionFailedException -> {
        Logger.getInstance(AppInspectionInspectorClient::class.java).warn(throwable.message)
      }
      else -> {
        logUnexpectedError(InspectorConnectionError(throwable))
      }
    }
  }

  /** Crate user-visible error message from [throwable] and notify [errorCallbacks]. */
  private fun notifyError(throwable: Throwable) {
    val userVisibleErrorMessage =
      when (throwable) {
        is CancellationException -> null
        is ConnectionFailedException -> throwable.message
        is AppInspectionCrashException -> "Layout Inspector crashed on the device."
        else -> "An unknown error happened."
      }

    if (userVisibleErrorMessage != null) {
      notifyError(userVisibleErrorMessage)
    }
  }

  override suspend fun doDisconnect() =
    withContext(AndroidDispatchers.workerThread) {
      try {
        viewInspector?.disconnect()
        composeInspector?.disconnect()
        // TODO: skiaParser#shutdown is a blocking function. Should be ported to coroutines
        skiaParser.shutdown()
        logEvent(DynamicLayoutInspectorEventType.SESSION_DATA)
      } catch (t: Throwable) {
        val error = getOriginalError(t)
        notifyError(error)
        logError(error)
        throw t
      }
    }

  override suspend fun startFetching() {
    try {
      startFetchingInternal()
    } catch (t: Throwable) {
      val error = getOriginalError(t)
      notifyError(error)
      logError(error)
      throw t
    }
  }

  private suspend fun startFetchingInternal() {
    stats.currentModeIsLive = true
    viewInspector?.startFetching(continuous = true)
  }

  private suspend fun disableBitmapScreenshots(disable: Boolean) {
    // TODO(b/265150325) disableBitmapScreenshots to stats
    viewInspector?.disableBitmapScreenshots(disable)
  }

  override suspend fun stopFetching() {
    try {
      // Reset the scale to 1 to support zooming while paused, and get an SKP if possible.
      if (capabilities.contains(Capability.SUPPORTS_SKP)) {
        updateScreenshotType(AndroidWindow.ImageType.SKP, 1.0f)
      } else {
        viewInspector?.updateScreenshotType(null, 1.0f)
      }
      stats.currentModeIsLive = false
      viewInspector?.stopFetching()
    } catch (t: Throwable) {
      val error = getOriginalError(t)
      notifyError(error)
      logError(error)
      throw t
    }
  }

  override fun refresh() {
    coroutineScope.launch(loggingExceptionHandler) { refreshInternal() }
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
    coroutineScope.launch(loggingExceptionHandler) { composeInspector?.updateSettings() }
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

  /**
   * Check if the system image used by the emulator is supported or not. API 29 Play Store images
   * are not supported: b/180622424. If not supported, show a banner informing the user.
   */
  private fun checkApi29Version(
    process: ProcessDescriptor,
    project: Project,
    sdkHandler: AndroidSdkHandler,
  ) {
    val compatibility =
      checkSystemImageForAppInspectionCompatibility(
        process.device.isEmulator,
        process.device.apiLevel,
        process.device.serial,
        project,
        sdkHandler,
      )

    val notCompatibleReason =
      when (compatibility) {
        Compatibility.Compatible -> return
        is Compatibility.NotCompatible -> compatibility.reason
      }

    when (notCompatibleReason) {
      API_29_PLAY_STORE -> {
        notificationModel.addNotification(
          SYSTEM_IMAGE_LIVE_UNSUPPORTED_KEY,
          LayoutInspectorBundle.message("api29.playstore.message"),
          Status.Warning,
          listOf(notificationModel.dismissAction),
        )
      }
    }

    throw ConnectionFailedException(
      "Unsupported system image revision",
      AttachErrorCode.LOW_API_LEVEL,
    )
  }
}

/** Check whether the current target's system image is compatible with app inspection. */
fun checkSystemImageForAppInspectionCompatibility(
  isEmulator: Boolean,
  apiLevel: Int,
  serialNumber: String,
  project: Project,
  sdkHandler: AndroidSdkHandler,
): Compatibility {
  if (!isEmulator || apiLevel != 29) {
    // We are interested in checking only emulators running API 29.
    return Compatibility.Compatible
  }

  val adb = AdbUtils.getAdbFuture(project).get()
  val avdName =
    adb?.devices?.find { it.serialNumber == serialNumber }?.avdData?.get(1, TimeUnit.SECONDS)?.name
      ?: return Compatibility.Compatible

  val avd = AvdManagerConnection.getAvdManagerConnection(sdkHandler).findAvd(avdName)

  return if (SystemImageTags.PLAY_STORE_TAG == avd?.tag) {
    // We don't support Play Store images on API 29: b/180622424
    Compatibility.NotCompatible(API_29_PLAY_STORE)
  } else {
    Compatibility.Compatible
  }
}

sealed class Compatibility {
  object Compatible : Compatibility()

  data class NotCompatible(val reason: Reason) : Compatibility() {
    enum class Reason {
      API_29_PLAY_STORE
    }
  }
}

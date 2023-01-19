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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.launch.LaunchParameters
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLaunchMonitor
import com.android.tools.idea.layoutinspector.pipeline.appinspection.ConnectionFailedException
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.GetComposablesResult
import com.android.tools.idea.layoutinspector.snapshots.APP_INSPECTION_SNAPSHOT_VERSION
import com.android.tools.idea.layoutinspector.snapshots.SnapshotMetadata
import com.android.tools.idea.layoutinspector.snapshots.saveAppInspectorSnapshot
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.CaptureSnapshotCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ErrorEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.PropertiesEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent
import com.android.tools.idea.protobuf.InvalidProtocolBufferException
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import layoutinspector.compose.inspection.LayoutInspectorComposeProtocol.GetAllParametersResponse
import layoutinspector.snapshots.Metadata
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

const val VIEW_LAYOUT_INSPECTOR_ID = "layoutinspector.view.inspection"
private val JAR = AppInspectorJar("layoutinspector-view-inspection.jar",
                                  developmentDirectory = "bazel-bin/tools/base/dynamic-layout-inspector/agent/appinspection/",
                                  releaseDirectory = "plugins/android/resources/app-inspection/")

/**
 * The client responsible for interacting with the view layout inspector running on the target
 * device.
 *
 * @param scope A coroutine scope used for asynchronously responding to events coming in from
 *     the inspector and other miscellaneous coroutine tasks.
 *
 * @param messenger The messenger that lets us communicate with the view inspector.
 *
 * @param composeInspector An inspector which, if provided, lets us fetch additional data useful
 *     for compose related values contained within our view tree.
 */
class ViewLayoutInspectorClient(
  private val model: InspectorModel,
  private val stats: SessionStatistics,
  private val processDescriptor: ProcessDescriptor,
  private val scope: CoroutineScope,
  private val messenger: AppInspectorMessenger,
  private val composeInspector: ComposeLayoutInspectorClient?,
  private val fireError: (String?, Throwable?) -> Unit = { _, _ -> },
  private val fireRootsEvent: (List<Long>) -> Unit = {},
  private val fireTreeEvent: (Data) -> Unit = {},
  private val launchMonitor: InspectorClientLaunchMonitor
) {

  private val project = model.project

  /**
   * Data packaged up and sent via [fireTreeEvent], needed for constructing the tree view in the
   * layout inspector.
   */
  class Data(
    val generation: Int,
    val rootIds: List<Long>,
    val viewEvent: LayoutEvent,
    val composeEvent: GetComposablesResult?
  )

  companion object {
    /**
     * Helper function for launching the view layout inspector and creating a client to interact
     * with it.
     *
     * @param eventScope Scope which will be used for processing incoming inspector events. It's
     *     expected that this will be a scope associated with a background dispatcher.
     */
    suspend fun launch(
      apiServices: AppInspectionApiServices,
      process: ProcessDescriptor,
      model: InspectorModel,
      stats: SessionStatistics,
      eventScope: CoroutineScope,
      composeLayoutInspectorClient: ComposeLayoutInspectorClient?,
      fireError: (String?, Throwable?) -> Unit,
      fireRootsEvent: (List<Long>) -> Unit,
      fireTreeEvent: (Data) -> Unit,
      launchMonitor: InspectorClientLaunchMonitor
    ): ViewLayoutInspectorClient {
      // Set force = true, to be more aggressive about connecting the layout inspector if an old version was
      // left running for some reason. This is a better experience than silently falling back to a legacy client.
      val params = LaunchParameters(process, VIEW_LAYOUT_INSPECTOR_ID, JAR, model.project.name, force = true)
      val messenger = apiServices.launchInspector(params)
      return ViewLayoutInspectorClient(model, stats, process, eventScope, messenger, composeLayoutInspectorClient, fireError,
                                       fireRootsEvent, fireTreeEvent, launchMonitor)
    }
  }

  val propertiesCache = LiveViewPropertiesCache(this, model)

  /**
   * Whether this client is continuously receiving layout events or not.
   *
   * This will be true between calls to `startFetching(continuous = true)` and
   * `stopFetching`.
   */
  private var isFetchingContinuously: Boolean = false
    set(value) {
      field = value
      propertiesCache.allowFetching = value
      composeInspector?.parametersCache?.allowFetching = value
      lastData.clear()
      lastProperties.clear()
      lastComposeParameters.clear()
    }

  private var generation = 0 // Update the generation each time we get a new LayoutEvent
  private val currRoots = mutableListOf<Long>()

  private var lastData = ConcurrentHashMap<Long, Data>()
  private var lastProperties = ConcurrentHashMap<Long, PropertiesEvent>()
  private var lastComposeParameters = ConcurrentHashMap<Long, GetAllParametersResponse>()
  private val recentLayouts = ConcurrentHashMap<Long, LayoutEvent>() // Map of root IDs to their layout

  init {
    scope.launch {
      // Layout events are very expensive to process and we may get a bunch of intermediate layouts while still processing an older one.
      // We skip over rendering these obsolete frames, which makes the UX feel much more responsive.
      messenger.eventFlow
        .mapNotNull { eventBytes ->
          try {
            Event.parseFrom(eventBytes)
          } catch (e: InvalidProtocolBufferException) {
            // Catch and swallow protocol exceptions thrown when debugging the application.
            // The above bytes are stitched together from separate messages. However, messages
            // sent during break point suspension can get duplicated in the transport layer,
            // resulting in a larger than expected payload.
            // See b/181908873 for context.
            null
          }
        }
        .onEach { event ->
          if (event.specializedCase == Event.SpecializedCase.LAYOUT_EVENT) {
            recentLayouts[event.layoutEvent.rootView.id] = event.layoutEvent
          }
        }
        .buffer(capacity = UNLIMITED) // Buffering allows event collection to keep happening even as we're still processing older ones
        .filter { event ->
          event.specializedCase != Event.SpecializedCase.LAYOUT_EVENT || event.layoutEvent === recentLayouts[event.layoutEvent.rootView.id]
        }
        .collect { event ->
          when (event.specializedCase) {
            Event.SpecializedCase.ERROR_EVENT -> handleErrorEvent(event.errorEvent)
            Event.SpecializedCase.ROOTS_EVENT -> handleRootsEvent(event.rootsEvent)
            Event.SpecializedCase.LAYOUT_EVENT -> handleLayoutEvent(event.layoutEvent)
            Event.SpecializedCase.PROPERTIES_EVENT -> handlePropertiesEvent(event.propertiesEvent)
            Event.SpecializedCase.PROGRESS_EVENT -> handleProgressEvent(event.progressEvent)
            Event.SpecializedCase.FOLD_EVENT -> handleFoldEvent(event.foldEvent)
            else -> error { "Unhandled event case: ${event.specializedCase}" }
          }
        }
    }
  }

  private fun handleFoldEvent(foldEvent: LayoutInspectorViewProtocol.FoldEvent) {
    model.foldInfo = foldEvent.convert()
  }

  private fun handleProgressEvent(progressEvent: LayoutInspectorViewProtocol.ProgressEvent) =
    launchMonitor.updateProgress(progressEvent.checkpoint.toAttachErrorState())

  suspend fun startFetching(continuous: Boolean) {
    isFetchingContinuously = continuous
    launchMonitor.updateProgress(AttachErrorState.START_REQUEST_SENT)
    val response = messenger.sendCommand {
      startFetchCommand = StartFetchCommand.newBuilder().apply {
        this.continuous = continuous
      }.build()
    }
    if (!response.startFetchResponse.error.isNullOrEmpty()) {
      throw ConnectionFailedException(response.startFetchResponse.error, response.startFetchResponse.code.toAttachErrorCode())
    }
  }

  fun updateScreenshotType(type: Screenshot.Type?, scale: Float = 1.0f) {
    scope.launch {
      messenger.sendCommand {
        updateScreenshotTypeCommandBuilder.apply {
          if (type != null) {
            this.type = type
          }
          this.scale = if (scale == 0f) 1f else scale
        }
      }
    }
  }

  suspend fun stopFetching() {
    isFetchingContinuously = false
    messenger.sendCommand {
      stopFetchCommand = StopFetchCommand.getDefaultInstance()
    }
  }

  suspend fun getProperties(rootViewId: Long, viewId: Long): GetPropertiesResponse {
    val response = messenger.sendCommand {
      getPropertiesCommand = GetPropertiesCommand.newBuilder().apply {
        this.rootViewId = rootViewId
        this.viewId = viewId
      }.build()
    }
    return response.getPropertiesResponse
  }

  fun disconnect() {
    messenger.scope.cancel()
  }

  private fun handleErrorEvent(errorEvent: ErrorEvent) {
    fireError(errorEvent.message, null)
  }

  private fun handleRootsEvent(rootsEvent: WindowRootsEvent) {
    launchMonitor.updateProgress(AttachErrorState.ROOTS_EVENT_RECEIVED)
    currRoots.clear()
    currRoots.addAll(rootsEvent.idsList)

    propertiesCache.retain(currRoots)
    composeInspector?.parametersCache?.retain(currRoots)
    lastData.keys.retainAll(currRoots.toSet())
    lastComposeParameters.keys.retainAll(currRoots.toSet())
    lastProperties.keys.retainAll(currRoots.toSet())
    recentLayouts.keys.retainAll(currRoots.toSet())
    fireRootsEvent(rootsEvent.idsList)
  }

  private suspend fun handleLayoutEvent(layoutEvent: LayoutEvent) {
    launchMonitor.updateProgress(AttachErrorState.LAYOUT_EVENT_RECEIVED)
    generation++
    stats.frameReceived()
    propertiesCache.clearFor(layoutEvent.rootView.id)
    composeInspector?.parametersCache?.clearFor(layoutEvent.rootView.id)

    val composablesResult = composeInspector?.getComposeables(layoutEvent.rootView.id, generation, !isFetchingContinuously)

    val data = Data(
      generation,
      currRoots,
      layoutEvent,
      composablesResult
    )
    if (!isFetchingContinuously) {
      lastData[layoutEvent.rootView.id] = data
    }
    fireTreeEvent(data)
  }

  private suspend fun handlePropertiesEvent(propertiesEvent: PropertiesEvent) {
    if (!isFetchingContinuously) {
      lastProperties[propertiesEvent.rootId] = propertiesEvent
    }
    propertiesCache.setAllFrom(propertiesEvent)

    composeInspector?.let {
      val composeParameters = it.getAllParameters(propertiesEvent.rootId)
      if (!isFetchingContinuously) {
        lastComposeParameters[propertiesEvent.rootId] = composeParameters
      }
      it.parametersCache.setAllFrom(composeParameters)
    }
  }

  @Slow
  fun saveSnapshot(path: Path): SnapshotMetadata {
    val snapshotMetadata = SnapshotMetadata(
      snapshotVersion = APP_INSPECTION_SNAPSHOT_VERSION,
      apiLevel = processDescriptor.device.apiLevel,
      processName = processDescriptor.name,
      liveDuringCapture = isFetchingContinuously,
      source = Metadata.Source.STUDIO,
      sourceVersion = ApplicationInfo.getInstance().fullVersion,
      dpi = model.resourceLookup.dpi,
      fontScale = model.resourceLookup.fontScale,
      screenDimension = model.resourceLookup.screenDimension
    )

    if (isFetchingContinuously) {
      fetchAndSaveSnapshot(path, snapshotMetadata)
    }
    else {
      saveNonLiveSnapshot(path, snapshotMetadata)
    }
    return snapshotMetadata
  }

  private fun saveNonLiveSnapshot(path: Path, snapshotMetadata: SnapshotMetadata) {
    // If we just switched to snapshot mode we may not have received data from the device yet. Wait until we have.
    try {
      launchWithProgress {
        while (lastData.isEmpty() || lastProperties.isEmpty() || (composeInspector != null && lastComposeParameters.isEmpty())) {
          delay(200)
        }
      }
    }
    catch (ignore: CancellationException) {
      return
    }
    // There could be a synchronization issue here, if we get an update just as these maps are being copied. However, since we only get
    // here in non-live mode, we shouldn't be getting any unexpected updates.
    val data = HashMap(lastData)
    val properties = HashMap(lastProperties)
    val composeParameters = HashMap(lastComposeParameters)
    saveAppInspectorSnapshot(path, data, properties, composeParameters, snapshotMetadata, model.foldInfo)
  }

  private fun fetchAndSaveSnapshot(path: Path, snapshotMetadata: SnapshotMetadata) {
    val start = System.currentTimeMillis()
    try {
      launchWithProgress { fetchAndSaveSnapshotAsync(path, snapshotMetadata) }
    }
    catch (cancellationException: CancellationException) {
      snapshotMetadata.saveDuration = System.currentTimeMillis() - start
      LayoutInspectorSessionMetrics(project, processDescriptor, snapshotMetadata)
        .logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.SNAPSHOT_CANCELLED, stats)
      // Delete the file in case we wrote out partial data
      Files.delete(path)
    }
  }

  private fun launchWithProgress(runnable: suspend CoroutineScope.() -> Unit) {
    val job = scope.launch(block = runnable) // TODO: error handling
    // Watch for the progress indicator to be canceled and cancel the job if so.
    val progress = ProgressManager.getInstance().progressIndicator
    scope.launch {
      while (true) {
        delay(300)
        if (progress?.isCanceled == true) {
          job.cancel()
          break
        }
        if (!job.isActive) {
          break
        }
      }
    }
    job.asCompletableFuture().get()
  }

  private suspend fun fetchAndSaveSnapshotAsync(path: Path, snapshotMetadata: SnapshotMetadata) {
    messenger.sendCommand {
      captureSnapshotCommand = CaptureSnapshotCommand.newBuilder().apply {
        // TODO: support bitmap
        screenshotType = Screenshot.Type.SKP
      }.build()
    }.captureSnapshotResponse?.let { snapshotResponse ->
      val composeInfo = composeInspector?.let { composeInspector ->
        generation++
        snapshotResponse.windowRoots.idsList.associateWith { id ->
          Pair(composeInspector.getComposeables(id, generation, forSnapshot = true),
               composeInspector.getAllParameters(id))
        }
      } ?: mapOf()

      saveAppInspectorSnapshot(path, snapshotResponse, composeInfo, snapshotMetadata, model.foldInfo)
    } ?: throw Exception()
  }
}

/**
 * Convenience method for wrapping a specific view-inspector command inside a parent
 * app inspection command.
 */
private suspend fun AppInspectorMessenger.sendCommand(initCommand: Command.Builder.() -> Unit): Response {
  val command = Command.newBuilder()
  command.initCommand()
  return Response.parseFrom(sendRawCommand(command.build().toByteArray()))
}

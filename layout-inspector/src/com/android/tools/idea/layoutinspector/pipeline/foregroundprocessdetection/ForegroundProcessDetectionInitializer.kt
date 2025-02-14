/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.api.process.SimpleProcessListener
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting

/**
 * Object used to create an initialized instance of [ForegroundProcessDetection]. Doing this in a
 * designated object is useful to facilitate testing.
 */
object ForegroundProcessDetectionInitializer {

  private val logger = Logger.getInstance(ForegroundProcessDetectionInitializer::class.java)

  @VisibleForTesting
  fun getDefaultForegroundProcessListener(
    parentDisposable: Disposable,
    deviceModel: DeviceModel,
    processModel: ProcessesModel,
  ): ForegroundProcessListener {

    // Variable used to keep track of the latest foreground process that showed up, but was not
    // found in the app inspection process list. If there is a race between foreground process
    // detection and app inspection processes, where the foreground process shows up before it's
    // available in app inspection, this allows us to recover once the process shows up in app
    // inspection.
    var missingForegroundProcess: ForegroundProcess? = null
    val lock = Any()

    val processListener =
      object : SimpleProcessListener() {
        override fun onProcessConnected(process: ProcessDescriptor) {
          synchronized(lock) {
            if (missingForegroundProcess?.pid == process.pid) {
              processModel.selectedProcess = process
              logger.info(
                "Foreground process restored to \"${process.name}\" " +
                  "on device \"${process.device.manufacturer} ${process.device.model}\" " +
                  "API ${process.device.apiLevel}"
              )
              missingForegroundProcess = null
            }
          }
        }

        override fun onProcessDisconnected(process: ProcessDescriptor) {}
      }

    processModel.addProcessListener(processListener)
    Disposer.register(parentDisposable) { processModel.removeProcessListener(processListener) }

    return ForegroundProcessListener { device, foregroundProcess, isDebuggable ->
      synchronized(lock) {
        if (isDebuggable) {
          missingForegroundProcess = null
          logger.info(
            "Process descriptor found for foreground process \"${foregroundProcess.processName}\" " +
              "on device \"${device.manufacturer} ${device.model} API ${device.apiLevel}\""
          )
        } else {
          missingForegroundProcess = foregroundProcess
          logger.info(
            "Process descriptor not found for foreground process \"${foregroundProcess.processName}\" " +
              "on device \"${device.manufacturer} ${device.model} API ${device.apiLevel}\""
          )
        }

        // set the foreground process to be the selected process.
        processModel.selectedProcess = foregroundProcess.matchToProcessDescriptor(processModel)
      }
    }
  }

  private fun getDefaultTransportClient(): TransportClient {
    // The following line has the side effect of starting the transport service if it has not been
    // already.
    // The consequence of not doing this is gRPC calls are never responded to.
    TransportService.getInstance()
    return TransportClient(TransportService.channelName)
  }

  fun initialize(
    parentDisposable: Disposable,
    project: Project,
    processModel: ProcessesModel,
    deviceModel: DeviceModel,
    coroutineScope: CoroutineScope,
    streamManager: TransportStreamManager,
    foregroundProcessListener: ForegroundProcessListener =
      getDefaultForegroundProcessListener(parentDisposable, deviceModel, processModel),
    transportClient: TransportClient = getDefaultTransportClient(),
    metrics: ForegroundProcessDetectionMetrics,
    layoutInspectorMetrics: LayoutInspectorMetrics = LayoutInspectorMetrics,
  ): ForegroundProcessDetection {
    val foregroundProcessDetection =
      ForegroundProcessDetectionImpl(
        parentDisposable,
        project,
        deviceModel,
        processModel,
        transportClient,
        layoutInspectorMetrics,
        metrics,
        coroutineScope,
        streamManager,
      )

    foregroundProcessDetection.addForegroundProcessListener(foregroundProcessListener)

    // TODO move inside ForegroundProcessDetection, when b/250404336 is resolved
    processModel.addSelectedProcessListeners {
      val selectedProcessDevice = processModel.selectedProcess?.device
      if (selectedProcessDevice != null && selectedProcessDevice != deviceModel.selectedDevice) {
        // If the selectedProcessDevice is different from the selectedDeviceModel.selectedDevice,
        // it means that the change of processModel.selectedProcess was not triggered by
        // ForegroundProcessDetection.
        // For example if the user deployed an app on a device from Studio.
        // When this happens, we should start polling the selectedProcessDevice.
        foregroundProcessDetection.startPollingDevice(selectedProcessDevice)
      }
    }

    return foregroundProcessDetection
  }
}

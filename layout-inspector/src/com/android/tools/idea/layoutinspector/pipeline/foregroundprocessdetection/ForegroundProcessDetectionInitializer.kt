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
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting

/**
* Object used to create an initialized instance of [ForegroundProcessDetection].
* Doing this in a designated object is useful to facilitate testing.
*/
object ForegroundProcessDetectionInitializer {

  private val logger = Logger.getInstance(ForegroundProcessDetectionInitializer::class.java)

  @VisibleForTesting
  fun getDefaultForegroundProcessListener(deviceModel: DeviceModel, processModel: ProcessesModel): ForegroundProcessListener {
    return object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        // There could be multiple projects open. Project1 with device1 selected, Project2 with device2 selected.
        // Because every event from the Transport is dispatched to every open project,
        // both Project1 and Project2 are going to receive events from device1 and device2.
        if (device != deviceModel.selectedDevice) {
          return
        }

        val foregroundProcessDescriptor = foregroundProcess.matchToProcessDescriptor(processModel)
        if (foregroundProcessDescriptor == null) {
          logger.info("Process descriptor not found for foreground process \"${foregroundProcess.processName}\" " +
                      "on device \"${device.manufacturer} ${device.model} API ${device.apiLevel}\""
          )
        }

        // set the foreground process to be the selected process.
        processModel.selectedProcess = foregroundProcessDescriptor
      }
    }
  }

  private fun getDefaultTransportClient(): TransportClient {
    // The following line has the side effect of starting the transport service if it has not been already.
    // The consequence of not doing this is gRPC calls are never responded to.
    TransportService.getInstance()
    return TransportClient(TransportService.channelName)
  }

  fun initialize(
    project: Project,
    processModel: ProcessesModel,
    deviceModel: DeviceModel,
    coroutineScope: CoroutineScope,
    foregroundProcessListener: ForegroundProcessListener = getDefaultForegroundProcessListener(deviceModel, processModel),
    transportClient: TransportClient = getDefaultTransportClient(),
    metrics: ForegroundProcessDetectionMetrics,
  ): ForegroundProcessDetection {
    val foregroundProcessDetection = ForegroundProcessDetection(
      project,
      deviceModel,
      transportClient,
      metrics,
      coroutineScope
    )

    foregroundProcessDetection.foregroundProcessListeners.add(foregroundProcessListener)

    processModel.addSelectedProcessListeners {
      val selectedProcessDevice = processModel.selectedProcess?.device
      if (selectedProcessDevice != null && selectedProcessDevice != deviceModel.selectedDevice) {
        // If the selectedProcessDevice is different from the selectedDeviceModel.selectedDevice,
        // it means that the change of processModel.selectedProcess was not triggered by ForegroundProcessDetection.
        // For example if the user deployed an app on a device from Studio.
        // When this happens, we should start polling the selectedProcessDevice.
        foregroundProcessDetection.startPollingDevice(selectedProcessDevice)
      }
    }

    return foregroundProcessDetection
  }
}
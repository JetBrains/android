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
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.manager.StreamConnected
import com.android.tools.idea.transport.manager.StreamDisconnected
import com.android.tools.idea.transport.manager.StreamEvent
import com.android.tools.idea.transport.manager.StreamEventQuery
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorTransportError
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import layout_inspector.LayoutInspector
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stops LayoutInspector.
 * If a device is selected, stops foreground process detection.
 * If a device is not selected, stops process inspection by setting the selected process to null.
 *
 * A process can be selected when a device does not support foreground process detection.
 *
 * This method also resets device-level DebugViewAttributes from the device.
 */
fun stopInspector(
  project: Project,
  deviceModel: DeviceModel,
  processesModel: ProcessesModel,
  foregroundProcessDetection: ForegroundProcessDetection?,
) {
  val selectedDevice = deviceModel.selectedDevice
  if (selectedDevice != null) {
    val debugViewAttributes = DebugViewAttributes.getInstance()
    if (debugViewAttributes.usePerDeviceSettings()) {
      debugViewAttributes.clear(project, selectedDevice)
    }
    foregroundProcessDetection?.stopPollingSelectedDevice()
  }
  else {
    processesModel.selectedProcess = null
  }
}

/**
 * This class contains information about a foreground process on an Android device.
 */
data class ForegroundProcess(val pid: Int, val processName: String)

/**
 * Match a [ForegroundProcess] with a [ProcessDescriptor].
 */
fun ForegroundProcess.matchToProcessDescriptor(processModel: ProcessesModel): ProcessDescriptor? {
  return processModel.processes.firstOrNull { it.pid == this.pid }
}

fun interface ForegroundProcessListener {
  fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess)
}

/**
 * This class is responsible for establishing a connection to the transport, sending commands to it and receiving events from it.
 *
 * The code that tracks the foreground process on the device is a library added to the transport's daemon, which runs on Android.
 * When it receives the start command, the library starts tracking the foreground process, it stops when it receives the stop command.
 * Information about the foreground process is sent by the transport as an event.
 *
 * @param deviceModel At any time reflects on which device we are polling for foreground process.
 */
class ForegroundProcessDetection(
  private val project: Project,
  private val deviceModel: DeviceModel,
  processModel: ProcessesModel,
  private val transportClient: TransportClient,
  private val layoutInspectorMetrics: LayoutInspectorMetrics,
  private val metrics: ForegroundProcessDetectionMetrics,
  scope: CoroutineScope,
  workDispatcher: CoroutineDispatcher = AndroidDispatchers.workerThread,
  @TestOnly private val onDeviceDisconnected: (DeviceDescriptor) -> Unit = {},
  @TestOnly private val pollingIntervalMs: Long = 2000) {

  companion object {
    private val logger = Logger.getInstance(ForegroundProcessDetection::class.java)

    /**
     * We are storing static references of [DeviceModel] because when multiple projects are open, they  need to coordinate with each other.
     *
     * When multiple projects are open, they all share the same device, on which a thread is running to do foreground process detection.
     * When a project asks the device to stop foreground process detection, it stops not only for that project, but for all the others too.
     *
     * On-device foreground process detection should be stopped only if the device is not the selected device on any [DeviceModel].
     *
     * This could be avoided by changing the communication protocol between Studio and device see b/257101182.
     */
    @VisibleForTesting
    val deviceModels = CopyOnWriteArrayList<DeviceModel>()

    fun addDeviceModel(deviceModel: DeviceModel) {
      deviceModels.add(deviceModel)
      logger.info("New device model added. Existing device models count: ${deviceModels.size}")
    }

    fun removeDeviceModel(deviceModel: DeviceModel) {
      deviceModels.remove(deviceModel)
      logger.info("Device model removed. Existing device models count: ${deviceModels.size}")
    }

    /**
     * Keeps track of the timestamp at connection for each device that was connected.
     * This is used to detect when b/250589069 happens.
     */
    private val connectTimestamps = mutableMapOf<DeviceDescriptor, Long>()
    private val loggedDevices = mutableSetOf<DeviceDescriptor>()

    private fun addTimeStamp(deviceDescriptor: DeviceDescriptor, newTimeStamp: Long, layoutInspectorMetrics: LayoutInspectorMetrics) {
      if (connectTimestamps.contains(deviceDescriptor)) {
        val prevTimeStamp = connectTimestamps[deviceDescriptor]!!
        // the previous timestamp is >= the new timestamp, this means that b/250589069 happened.
        if (prevTimeStamp >= newTimeStamp && !loggedDevices.contains(deviceDescriptor)) {
          logger.info(
            "Device re-connected \"${deviceDescriptor.manufacturer} ${deviceDescriptor.model} API${deviceDescriptor.apiLevel}\":" +
            "previous timestamp ($prevTimeStamp) >= new timestamp ($newTimeStamp)"
          )
          // log only once per device
          loggedDevices.add(deviceDescriptor)
          layoutInspectorMetrics.logTransportError(
            DynamicLayoutInspectorTransportError.Type.TRANSPORT_OLD_TIMESTAMP_BIGGER_THAN_NEW_TIMESTAMP,
            deviceDescriptor
          )
        }
        else {
          connectTimestamps[deviceDescriptor] = newTimeStamp
        }
      }
      else {
        connectTimestamps[deviceDescriptor] = newTimeStamp
      }
    }
  }

  val foregroundProcessListeners = mutableListOf<ForegroundProcessListener>()

  /**
   * Maps groupId to connected stream. Each stream corresponds to a device.
   * The groupId is the only information available when the stream disconnects.
   */
  private val connectedStreams = ConcurrentHashMap<Long, TransportStreamChannel>()

  private val handshakeExecutors = ConcurrentHashMap<DeviceDescriptor, HandshakeExecutor>()

  init {
    processModel.addSelectedProcessListeners {
      val selectedProcess = processModel.selectedProcess ?: return@addSelectedProcessListeners

      val device = if (selectedProcess.isRunning) {
        selectedProcess.device
      }
      else {
        return@addSelectedProcessListeners
      }

      // If there is a new selectedProcess, but the device does not support foreground process detection,
      // it means the process was selected by the user from the process picker (TODO verify this works) or by launching the app.
      // When this happens, initiate the handshake with the device again.
      // We don't know exactly all the configurations on which the handshake can fail (device in weird states),
      // this is our last resort to recover from false negatives.
      if (!deviceModel.supportsForegroundProcessDetection(device)) {
        scope.launch {
          initiateNewHandshake(device)
        }
      }
    }

    val manager = TransportStreamManager.createManager(transportClient.transportStub, workDispatcher)

    scope.launch {
      manager.streamActivityFlow()
        .collect { activity ->
          val streamChannel = activity.streamChannel
          val streamDevice = streamChannel.stream.device.toDeviceDescriptor()
          val stream =  streamChannel.stream
          if (activity is StreamConnected) {
            connectedStreams[streamChannel.stream.streamId] = streamChannel

            val timeRequest = Transport.TimeRequest.newBuilder().setStreamId(stream.streamId).build()
            val currentTime = activity.streamChannel.client.getCurrentTime(timeRequest).timestampNs

            addTimeStamp(streamDevice, currentTime, layoutInspectorMetrics)

            // start listening for LAYOUT_INSPECTOR_FOREGROUND_PROCESS events
            launch {
              streamChannel.eventFlow(
                StreamEventQuery(
                  eventKind = Common.Event.Kind.LAYOUT_INSPECTOR_FOREGROUND_PROCESS,
                  startTime = { currentTime }
                )
              ).collect { streamEvent ->
                val foregroundProcess = streamEvent.toForegroundProcess()
                if (foregroundProcess != null) {
                  foregroundProcessListeners.forEach { it.onNewProcess(streamDevice, foregroundProcess) }
                }
              }
            }

            val handshakeExecutor = HandshakeExecutor(
              streamDevice, stream, scope, workDispatcher, transportClient, metrics, pollingIntervalMs
            )

            // start listening for LAYOUT_INSPECTOR_TRACKING_FOREGROUND_PROCESS_SUPPORTED events
            launch {
              streamChannel.eventFlow(
                StreamEventQuery(
                  eventKind = Common.Event.Kind.LAYOUT_INSPECTOR_TRACKING_FOREGROUND_PROCESS_SUPPORTED,
                  startTime = { currentTime }
                )
              ).collect { streamEvent ->
                  if (streamEvent.event.hasLayoutInspectorTrackingForegroundProcessSupported()) {
                    val trackingForegroundProcessSupportedEvent = streamEvent.event.layoutInspectorTrackingForegroundProcessSupported
                    val supportType = trackingForegroundProcessSupportedEvent.supportType!!
                    when (supportType) {
                      LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNKNOWN -> {
                        handshakeExecutor.post(HandshakeState.UnknownSupported(trackingForegroundProcessSupportedEvent))
                      }
                      LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED -> {
                        handshakeExecutor.post(HandshakeState.Supported(trackingForegroundProcessSupportedEvent))

                        deviceModel.foregroundProcessDetectionSupportedDevices.add(streamDevice)

                        // If there are no devices connected, we can automatically connect to the first device.
                        // So the user doesn't have to handpick the device.
                        if (deviceModel.selectedDevice == null && deviceModel.devices.contains(streamDevice)) {
                          // TODO make sure this doesn't happen when the tool window is collapsed
                          startPollingDevice(streamDevice)
                        }
                      }
                      LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED -> {
                        handshakeExecutor.post(HandshakeState.NotSupported(trackingForegroundProcessSupportedEvent))

                        // the device is not added to DeviceModel#foregroundProcessDetectionSupportedDevices,
                        // so it will be handled in the UI by showing a process picker.
                      }
                      LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNRECOGNIZED -> {
                        throw RuntimeException("Unrecognized support type: $supportType")
                      }
                    }

                    logger.info(
                      "ForegroundProcessDetection handshake - " +
                      "device: \"${streamDevice.manufacturer} ${streamDevice.model} " +
                      "API${streamDevice.apiLevel}\" " +
                      "status: $supportType"
                    )
                  }
                }
            }


            handshakeExecutor.post(HandshakeState.Connected)
            handshakeExecutors[streamDevice] = handshakeExecutor
          }
          else if (activity is StreamDisconnected) {
            connectedStreams.remove(stream.streamId)
            deviceModel.foregroundProcessDetectionSupportedDevices.remove(streamDevice)

            val handler = handshakeExecutors.remove(streamDevice)
            handler?.post(HandshakeState.Disconnected)

            if (streamDevice.serial == deviceModel.selectedDevice?.serial) {
              // when a device is disconnected we still want to call [DebugViewAttributes#clear],
              // because this updates the state of the class. The flag will be turned off on the
              // device by the trap command, we want to reflect this state in DebugViewAttributes.
              val debugViewAttributes = DebugViewAttributes.getInstance()
              if (debugViewAttributes.usePerDeviceSettings()) {
                debugViewAttributes.clear(project, streamDevice)
              }
              deviceModel.selectedDevice = null
            }

            onDeviceDisconnected(streamDevice)
          }
        }
    }
  }

  /**
   * Start polling for foreground process on [newDevice].
   *
   * If we are already polling on another device, we send a stop command to that device
   * before sending a start command to the new device.
   */
  fun startPollingDevice(newDevice: DeviceDescriptor) {
    val selectedDevice = deviceModel.selectedDevice
    if (newDevice == selectedDevice) {
      return
    }

    val oldStream = connectedStreams.values.find { it.stream.device.serial == selectedDevice?.serial }
    val newStream = connectedStreams.values.find { it.stream.device.serial == newDevice.serial }

    if (oldStream != null) {
      sendStopOnDevicePollingCommand(oldStream.stream, selectedDevice!!)
    }

    if (newStream != null) {
      val isStreamSupported = deviceModel.supportsForegroundProcessDetection(newDevice)
      if (!isStreamSupported) {
        deviceModel.selectedDevice = null
      }
      else {
        sendStartOnDevicePollingCommand(newStream.stream)
        deviceModel.selectedDevice = newDevice
      }
    }
  }

  /**
   * Stop listening to foreground process events from [DeviceModel.selectedDevice].
   * Then sets [DeviceModel.selectedDevice] to null.
   */
  fun stopPollingSelectedDevice() {
    val selectedDevice = deviceModel.selectedDevice ?: return
    val transportStreamChannel = connectedStreams.values.find { it.stream.device.serial == selectedDevice.serial }
    if (transportStreamChannel != null) {
      sendStopOnDevicePollingCommand(transportStreamChannel.stream, selectedDevice)
    }
    deviceModel.selectedDevice = null
  }

  /**
   * Tell the device connected to this stream to start the on-device detection of foreground process.
   */
  private fun sendStartOnDevicePollingCommand(stream: Common.Stream) {
    transportClient.sendCommand(Commands.Command.CommandType.START_TRACKING_FOREGROUND_PROCESS, stream.streamId)
  }

  /**
   * Tell the device connected to this stream to stop the on-device detection of foreground process.
   */
  private fun sendStopOnDevicePollingCommand(stream: Common.Stream, deviceDescriptor: DeviceDescriptor) {
    if (shouldStopPollingDevice(deviceDescriptor)) {
      transportClient.sendCommand(Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS, stream.streamId)
    }
  }

  /**
   * The polling should be stopped on a device only if it's not the selected device on any other [DeviceModel].
   * There can be multiple [DeviceModel]s if there are multiple projects open in Studio.
   *
   * @see ForegroundProcessDetection.deviceModels
   */
  private fun shouldStopPollingDevice(selectedDevice: DeviceDescriptor): Boolean {
    val deviceModels = ForegroundProcessDetection.deviceModels
    val count = deviceModels.mapNotNull { it.selectedDevice }.count { it.serial == selectedDevice.serial }
    return count <= 1
  }

  /**
   * Initiates a new handshake. Only if [device] already executed the handshake that happens at connection time.
   */
  private suspend fun initiateNewHandshake(device: DeviceDescriptor) {
    val handshakeExecutor = handshakeExecutors[device]
    if (handshakeExecutor?.isHandshakeInProgress == false) {
      handshakeExecutor.post(HandshakeState.Connected)
    }
  }
}

/**
 * Send a command to the transport.
 */
internal fun TransportClient.sendCommand(commandType: Commands.Command.CommandType, streamId: Long) {
  val command = Commands.Command
    .newBuilder()
    .setType(commandType)
    .setStreamId(streamId)
    .build()

  transportStub.execute(
    Transport.ExecuteRequest.newBuilder().setCommand(command).build()
  )
}

private fun StreamEvent.toForegroundProcess(): ForegroundProcess? {
  return if (
    !event.hasLayoutInspectorForegroundProcess() ||
    event.layoutInspectorForegroundProcess.pid == null ||
    event.layoutInspectorForegroundProcess.processName == null
  ) {
    null
  }
  else {
    val foregroundProcessEvent = event.layoutInspectorForegroundProcess
    val pid = foregroundProcessEvent.pid.toInt()
    val packageName = foregroundProcessEvent.processName
    ForegroundProcess(pid, packageName)
  }
}

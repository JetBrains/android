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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.transport.FailedToStartServerException
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportDeviceManager
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.idea.transport.TransportService
import com.android.tools.idea.transport.manager.StreamConnected
import com.android.tools.idea.transport.manager.StreamDisconnected
import com.android.tools.idea.transport.manager.StreamEvent
import com.android.tools.idea.transport.manager.StreamEventQuery
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Agent
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import layout_inspector.LayoutInspector
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

/**
* Object used to create an initialized instance of [ForegroundProcessDetection].
* Doing this in a designated object is useful to facilitate testing.
*/
object ForegroundProcessDetectionInitializer {

  private val logger = Logger.getInstance(ForegroundProcessDetectionInitializer::class.java)

  @VisibleForTesting
  fun getDefaultForegroundProcessListener(processModel: ProcessesModel): ForegroundProcessListener {
    return object : ForegroundProcessListener {
      override fun onNewProcess(device: DeviceDescriptor, foregroundProcess: ForegroundProcess) {
        val foregroundProcessDescriptor = foregroundProcess.matchToProcessDescriptor(processModel)
        if (foregroundProcessDescriptor == null) {
          logger.info("Process descriptor not found for foreground process \"${foregroundProcess.processName}\"")
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
    foregroundProcessListener: ForegroundProcessListener = getDefaultForegroundProcessListener(processModel),
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

/**
 * Keeps track of the currently selected device.
 *
 * The selected device is controlled by [ForegroundProcessDetection],
 * and it is used by [SelectedDeviceAction].
 */
class DeviceModel(private val processesModel: ProcessesModel) {

  @TestOnly
  constructor(processesModel: ProcessesModel, foregroundProcessDetectionSupportedDeviceTest: Set<DeviceDescriptor>) : this(processesModel) {
    foregroundProcessDetectionSupportedDevices.addAll(foregroundProcessDetectionSupportedDeviceTest)
  }

  /**
   * The device on which the on-device library is polling for foreground process.
   * When null, it means that we are not polling on any device.
   *
   * [selectedDevice] should only be set by [ForegroundProcessDetection],
   * this is to make sure that there is consistency between the [selectedDevice] and the device we are polling on.
   */
  var selectedDevice: DeviceDescriptor? = null
    @VisibleForTesting
    set(value) {
      // each time the selected device changes, the selected process should be reset
      processesModel.selectedProcess = null
      newSelectedDeviceListeners.forEach { it.invoke(value) }
      field = value
    }

  val newSelectedDeviceListeners = mutableListOf<(DeviceDescriptor?) -> Unit>()

  /**
   * The set of connected devices that support foreground process detection.
   */
  internal val foregroundProcessDetectionSupportedDevices = mutableSetOf<DeviceDescriptor>()

  val devices: Set<DeviceDescriptor>
    get() {
      return processesModel.devices
    }

  val selectedProcess: ProcessDescriptor?
    get() {
      return processesModel.selectedProcess
    }

  val processes: Set<ProcessDescriptor>
    get() {
      return processesModel.processes
    }

  fun supportsForegroundProcessDetection(device: DeviceDescriptor): Boolean {
    return foregroundProcessDetectionSupportedDevices.contains(device)
  }
}

/**
 * Listener used to set the feature flag to true or false in the Transport Daemon.
 */
class TransportDeviceManagerListenerImpl : TransportDeviceManager.TransportDeviceManagerListener, ProjectManagerListener {

  override fun projectOpened(project: Project) {
    ApplicationManager.getApplication().messageBus.connect().subscribe(TransportDeviceManager.TOPIC, this)
  }

  override fun onPreTransportDaemonStart(device: Common.Device) { }
  override fun onTransportDaemonException(device: Common.Device, exception: Exception) { }
  override fun onTransportProxyCreationFail(device: Common.Device, exception: Exception) { }
  override fun onStartTransportDaemonServerFail(device: Common.Device, exception: FailedToStartServerException) { }

  override fun customizeProxyService(proxy: TransportProxy) { }
  override fun customizeAgentConfig(configBuilder: Agent.AgentConfig.Builder, runConfig: AndroidRunConfigurationBase?) { }

  override fun customizeDaemonConfig(configBuilder: Transport.DaemonConfig.Builder) {
    configBuilder
      .setLayoutInspectorConfig(
        configBuilder.layoutInspectorConfigBuilder.setAutoconnectEnabled(
          StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED.get()
        )
      )
  }
}

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
  private val transportClient: TransportClient,
  private val metrics: ForegroundProcessDetectionMetrics,
  scope: CoroutineScope,
  workDispatcher: CoroutineDispatcher = AndroidDispatchers.workerThread,
  @TestOnly private val onDeviceDisconnected: (DeviceDescriptor) -> Unit = {},
  @TestOnly private val pollingIntervalMs: Long = 2000) {

  private val logger = Logger.getInstance(ForegroundProcessDetection::class.java)

  val foregroundProcessListeners = mutableListOf<ForegroundProcessListener>()

  /**
   * Maps groupId to connected stream. Each stream corresponds to a device.
   * The groupId is the only information available when the stream disconnects.
   */
  private val connectedStreams = ConcurrentHashMap<Long, TransportStreamChannel>()

  private val handshakeExecutors = ConcurrentHashMap<DeviceDescriptor, HandshakeExecutor>()

  init {
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
    if (newDevice == deviceModel.selectedDevice) {
      return
    }

    val oldStream = connectedStreams.values.find { it.stream.device.serial == deviceModel.selectedDevice?.serial }
    val newStream = connectedStreams.values.find { it.stream.device.serial == newDevice.serial }

    if (oldStream != null) {
      sendStopOnDevicePollingCommand(oldStream.stream)
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
    val transportStreamChannel = connectedStreams.values.find { it.stream.device.serial == deviceModel.selectedDevice?.serial }
    if (transportStreamChannel != null) {
      sendStopOnDevicePollingCommand(transportStreamChannel.stream)
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
  private fun sendStopOnDevicePollingCommand(stream: Common.Stream) {
    transportClient.sendCommand(Commands.Command.CommandType.STOP_TRACKING_FOREGROUND_PROCESS, stream.streamId)
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

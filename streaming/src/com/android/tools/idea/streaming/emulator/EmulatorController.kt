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
package com.android.tools.idea.streaming.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.emulator.control.AudioPacket
import com.android.emulator.control.ClipData
import com.android.emulator.control.DisplayConfigurations
import com.android.emulator.control.DisplayMode
import com.android.emulator.control.DisplayModeValue
import com.android.emulator.control.EmulatorControllerGrpc
import com.android.emulator.control.EmulatorStatus
import com.android.emulator.control.ExtendedControlsStatus
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.MouseEvent
import com.android.emulator.control.Notification
import com.android.emulator.control.PaneEntry
import com.android.emulator.control.PhysicalModelValue
import com.android.emulator.control.RotationRadian
import com.android.emulator.control.SnapshotFilter
import com.android.emulator.control.SnapshotList
import com.android.emulator.control.SnapshotPackage
import com.android.emulator.control.SnapshotServiceGrpc
import com.android.emulator.control.ThemingStyle
import com.android.emulator.control.TouchEvent
import com.android.emulator.control.UiControllerGrpc
import com.android.emulator.control.Velocity
import com.android.emulator.control.VmRunState
import com.android.ide.common.util.Cancelable
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_GRPC_CALLS
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS
import com.android.tools.idea.io.grpc.CallCredentials
import com.android.tools.idea.io.grpc.ClientCall
import com.android.tools.idea.io.grpc.CompressorRegistry
import com.android.tools.idea.io.grpc.ConnectivityState
import com.android.tools.idea.io.grpc.DecompressorRegistry
import com.android.tools.idea.io.grpc.KnownLength
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.Metadata
import com.android.tools.idea.io.grpc.MethodDescriptor
import com.android.tools.idea.io.grpc.MethodDescriptor.Marshaller
import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.idea.io.grpc.stub.ClientCallStreamObserver
import com.android.tools.idea.io.grpc.stub.ClientCalls
import com.android.tools.idea.io.grpc.stub.ClientResponseObserver
import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.idea.protobuf.CodedInputStream
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.protobuf.ExtensionRegistryLite
import com.android.tools.idea.protobuf.InvalidProtocolBufferException
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.protobuf.UnsafeByteOperations
import com.android.tools.idea.protobuf.WireFormat
import com.android.tools.idea.streaming.emulator.RuntimeConfigurationOverrider.getRuntimeConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import java.io.IOException
import java.io.InputStream
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Controls a running Emulator.
 */
class EmulatorController(val emulatorId: EmulatorId, parentDisposable: Disposable) : Disposable {
  private val imageResponseMarshaller = ImageResponseMarshaller()
  private val streamScreenshotMethod = EmulatorControllerGrpc.getStreamScreenshotMethod().toBuilder(
      EmulatorControllerGrpc.getStreamScreenshotMethod().requestMarshaller, imageResponseMarshaller).build()
  private var channel: ManagedChannel? = null
  @Volatile private var emulatorControllerStubInternal: EmulatorControllerGrpc.EmulatorControllerStub? = null
  @Volatile private var snapshotServiceStubInternal: SnapshotServiceGrpc.SnapshotServiceStub? = null
  @Volatile private var uiControllerStubInternal: UiControllerGrpc.UiControllerStub? = null
  @Volatile private var emulatorConfigInternal: EmulatorConfiguration? = null
  @Volatile internal var skinDefinition: SkinDefinition? = null
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private var connectionStateInternal = AtomicReference(ConnectionState.NOT_INITIALIZED)
  private val emulatorState = AtomicReference(EmulatorState.RUNNING)
  private val connectionStateListeners: ConcurrentList<ConnectionStateListener> = ContainerUtil.createConcurrentList()
  private val connectivityStateWatcher = object : Runnable {
    override fun run() {
      if (connectionState == ConnectionState.DISCONNECTED) {
        return // DISCONNECTED state is final.
      }
      val ch = channel ?: return
      val state = ch.getState(false)
      when (state) {
        ConnectivityState.CONNECTING -> connectionState = ConnectionState.CONNECTING
        ConnectivityState.SHUTDOWN -> connectionState = ConnectionState.DISCONNECTED
        else -> {}
      }
      ch.notifyWhenStateChanged(state, this)
    }
  }
  private val keyboardEventQueue = ArrayDeque<Pair<KeyboardEvent, StreamObserver<Empty>>>()
  @GuardedBy("keyboardEventQueue")
  private var keyboardEventInFlight = false

  var emulatorConfig: EmulatorConfiguration
    get() {
      return emulatorConfigInternal ?: throwNotYetConnected()
    }
    private inline set(value) {
      emulatorConfigInternal = value
    }

  var connectionState: ConnectionState
    get() {
      return connectionStateInternal.get()
    }
    private set(value) {
      val oldValue = connectionStateInternal.getAndSet(value)
      if (oldValue != value) {
        if (value == ConnectionState.DISCONNECTED) {
          if (oldValue == ConnectionState.CONNECTED) {
            LOG.info("Disconnected from ${emulatorConfig.avdName} (${emulatorId.serialPort})")
          }
          else {
            LOG.warn("Unable to connect ${emulatorConfig.avdName} (${emulatorId.serialPort})")
          }
        }
        for (listener in connectionStateListeners) {
          listener.connectionStateChanged(this, value)
        }
      }
    }

  /**
   * Returns true if [shutdown] has been called.
   */
  val isShuttingDown
    get() = emulatorState.get() != EmulatorState.RUNNING

  var emulatorControllerStub: EmulatorControllerGrpc.EmulatorControllerStub
    get() {
      return emulatorControllerStubInternal ?: throwNotYetConnected()
    }
    private inline set(stub) {
      emulatorControllerStubInternal = stub
    }

  var snapshotServiceStub: SnapshotServiceGrpc.SnapshotServiceStub
    get() {
      return snapshotServiceStubInternal ?: throwNotYetConnected()
    }
    private inline set(stub) {
      snapshotServiceStubInternal = stub
    }

  var uiControllerStub: UiControllerGrpc.UiControllerStub
    get() {
      return uiControllerStubInternal ?: throwNotYetConnected()
    }
    private inline set(stub) {
      uiControllerStubInternal = stub
    }

  init {
    Disposer.register(parentDisposable, this)
  }

  @AnyThread
  fun addConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.add(listener)
  }

  @AnyThread
  fun removeConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.remove(listener)
  }

  /**
   * Establishes a connection to the Emulator. The process of establishing a connection is partially
   * asynchronous, but the synchronous part of this method also takes considerable time.
   */
  @Slow
  fun connect() {
    val maxInboundMessageSize: Int
    val config = EmulatorConfiguration.readAvdDefinition(emulatorId.avdId, emulatorId.avdFolder)
    if (config == null) {
      // The error has already been logged.
      connectionState = ConnectionState.DISCONNECTED
      return
    }
    emulatorConfig = config
    skinDefinition = SkinDefinitionCache.getInstance().getSkinDefinition(config.skinFolder)

    maxInboundMessageSize = config.displayWidth * config.displayHeight * 3 + 100 // Three bytes per pixel.

    connectGrpc(maxInboundMessageSize)
    sendKeepAlive()
  }

  /**
   * Establishes a gRPC connection to the Emulator.
   *
   * @param maxInboundMessageSize: size of maximum inbound gRPC message, default to 4 MiB.
   */
  @Slow
  fun connectGrpc(maxInboundMessageSize: Int = 4 * 1024 * 1024) {
    connectionState = ConnectionState.CONNECTING
    val channel = getRuntimeConfiguration()
      .newGrpcChannelBuilder("localhost", emulatorId.grpcPort)
      .usePlaintext() // TODO: Add support for TLS encryption.
      .maxInboundMessageSize(maxInboundMessageSize)
      .compressorRegistry(CompressorRegistry.newEmptyInstance()) // Disable data compression.
      .decompressorRegistry(DecompressorRegistry.emptyInstance())
      .build()
    this.channel = channel

    val token = emulatorId.grpcToken
    if (token == null) {
      emulatorControllerStub = EmulatorControllerGrpc.newStub(channel)
      snapshotServiceStub = SnapshotServiceGrpc.newStub(channel)
      uiControllerStub = UiControllerGrpc.newStub(channel)
    }
    else {
      val credentials = TokenCallCredentials(token)
      emulatorControllerStub = EmulatorControllerGrpc.newStub(channel).withCallCredentials(credentials)
      snapshotServiceStub = SnapshotServiceGrpc.newStub(channel).withCallCredentials(credentials)
      uiControllerStub = UiControllerGrpc.newStub(channel).withCallCredentials(credentials)
    }

    channel.notifyWhenStateChanged(channel.getState(false), connectivityStateWatcher)
  }

  /**
   * Sends a shutdown command to the emulator. Subsequent [shutdown] calls are ignored.
   */
  fun shutdown() {
    if (emulatorState.compareAndSet(EmulatorState.RUNNING, EmulatorState.SHUTDOWN_REQUESTED) &&
        connectionState == ConnectionState.CONNECTED) {
      sendShutdown()
    }
  }

  private fun sendShutdown() {
    if (emulatorState.compareAndSet(EmulatorState.SHUTDOWN_REQUESTED, EmulatorState.SHUTDOWN_SENT)) {
      alarm.cancelAllRequests()
      val vmRunState = VmRunState.newBuilder().setState(VmRunState.RunState.SHUTDOWN).build()
      setVmState(vmRunState)
    }
  }

  /**
   * Pushes audio packets into the emulator microphone.
   *
   * Usually multiple packages need to be sent to push one audio file, including several AudioPackets
   * that the injected audio file will be divided into. The implementation of the stream observer must
   * handle asynchronous events correctly, especially when pushing big files. This is usually done by
   * overwriting [ClientResponseObserver.beforeStart] and calling
   * [io.grpc.stub.CallStreamObserver.setOnReadyHandler] from there.
   *
   * @param streamObserver a client stream observer to handle events
   * @return a StreamObserver that can be used to trigger the push
   */
  fun injectAudio(streamObserver: ClientResponseObserver<AudioPacket, Empty>) : StreamObserver<AudioPacket>{
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("injectAudio()")
    }
    return emulatorControllerStub.injectAudio(
        DelegatingClientResponseObserver(streamObserver, EmulatorControllerGrpc.getInjectAudioMethod()))
  }

  /**
   * Sets contents of the clipboard.
   */
  fun setClipboard(clipData: ClipData, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      // Don't log the actual clipboard contents to protect user privacy.
      val clipDataForLogging = shortDebugString(clipData.toBuilder().setText("<clipboard contents>").build())
      LOG.info("setClipboard($clipDataForLogging)")
    }
    emulatorControllerStub.setClipboard(clipData, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetClipboardMethod()))
  }

  /**
   * Streams contents of the clipboard.
   */
  fun streamClipboard(streamObserver: StreamObserver<ClipData>): Cancelable {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("streamClipboard()")
    }
    val method = EmulatorControllerGrpc.getStreamClipboardMethod()
    val call = emulatorControllerStub.channel.newCall(method, emulatorControllerStub.callOptions)
    ClientCalls.asyncServerStreamingCall(call, EMPTY_PROTO, DelegatingStreamObserver(streamObserver, method))
    return CancelableClientCall(call)
  }

  /**
   * Sets the size of the primary display of a resizable AVD.
   */
  fun setDisplayMode(displayModeValue: DisplayModeValue, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    val displayMode = DisplayMode.newBuilder().setValue(displayModeValue).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setDisplayMode(${shortDebugString(displayMode)})")
    }
    emulatorControllerStub.setDisplayMode(displayMode,
                                          DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetDisplayModeMethod()))
  }

  /**
   * Sends a [KeyboardEvent] to the Emulator.
   */
  fun sendKey(keyboardEvent: KeyboardEvent, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("sendKey(${shortDebugString(keyboardEvent)})")
    }
    // Non-streaming gRPC calls don't guarantee in-order delivery. To make sure that the keyboard
    // events don't arrive out of order, we don't issue a new sendKey call until the previous one
    // is completed.
    synchronized(keyboardEventQueue) {
      if (keyboardEventInFlight) {
        keyboardEventQueue.add(Pair(keyboardEvent, streamObserver))
        return
      }
      keyboardEventInFlight = true
    }

    doSendKeyboardEvent(keyboardEvent, streamObserver)
  }

  private fun doSendKeyboardEvent(keyboardEvent: KeyboardEvent, streamObserver: StreamObserver<Empty>) {
    val observer = object : DelegatingStreamObserver<KeyboardEvent, Empty>(streamObserver, EmulatorControllerGrpc.getSendKeyMethod()) {
      override fun onCompleted() {
        super.onCompleted()
        val item: Pair<KeyboardEvent, StreamObserver<Empty>>?
        synchronized(keyboardEventQueue) {
          item = keyboardEventQueue.removeFirstOrNull()
          if (item == null) {
            keyboardEventInFlight = false
          }
          else {
            doSendKeyboardEvent(item.first, item.second)
          }
        }
      }
    }

    emulatorControllerStub.sendKey(keyboardEvent, observer)
  }

  /**
   * Sends a [MouseEvent] to the Emulator.
   */
  fun sendMouse(mouseEvent: MouseEvent, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS.get()) {
      LOG.info("sendMouse(${shortDebugString(mouseEvent)})")
    }
    emulatorControllerStub.sendMouse(mouseEvent, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSendMouseMethod()))
  }

  /**
   * Sends a [TouchEvent] to the Emulator.
   */
  fun sendTouch(touchEvent: TouchEvent, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS.get()) {
      LOG.info("sendTouch(${shortDebugString(touchEvent)})")
    }
    emulatorControllerStub.sendTouch(touchEvent, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSendTouchMethod()))
  }

  /**
   * Streams emulator notifications.
   */
  fun streamNotification(streamObserver: StreamObserver<Notification>): Cancelable {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("streamNotification()")
    }
    val method = EmulatorControllerGrpc.getStreamNotificationMethod()
    val call = emulatorControllerStub.channel.newCall(method, emulatorControllerStub.callOptions)
    ClientCalls.asyncServerStreamingCall(call, EMPTY_PROTO, DelegatingStreamObserver(streamObserver, method))
    return CancelableClientCall(call)
  }

  /**
   * Retrieves a physical model value.
   */
  fun getPhysicalModel(physicalType: PhysicalModelValue.PhysicalType, streamObserver: StreamObserver<PhysicalModelValue>) {
    val modelValue = PhysicalModelValue.newBuilder().setTarget(physicalType).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getPhysicalModel(${shortDebugString(modelValue)})")
    }
    emulatorControllerStub.getPhysicalModel(
        modelValue, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetPhysicalModelMethod()))
  }

  /**
   * Sets a physical model value.
   */
  fun setPhysicalModel(modelValue: PhysicalModelValue, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setPhysicalModel(${shortDebugString(modelValue)})")
    }
    emulatorControllerStub.setPhysicalModel(
        modelValue, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetPhysicalModelMethod()))
  }

  /**
   * Retrieves a screenshot of an Emulator display.
   */
  fun getScreenshot(imageFormat: ImageFormat, streamObserver: StreamObserver<Image>) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getScreenshot(${shortDebugString(imageFormat)})")
    }
    emulatorControllerStub.getScreenshot(
        imageFormat, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetScreenshotMethod()))
  }

  /**
   * Streams a series of screenshots.
   *
   * **Note**: The value returned by the [Image.getImage] method of the response object cannot be used
   * outside of the [StreamObserver.onNext] method because it is backed by a mutable reusable byte array.
   */
  fun streamScreenshot(imageFormat: ImageFormat, streamObserver: StreamObserver<Image>): Cancelable {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("streamScreenshot(${shortDebugString(imageFormat)})")
    }
    val call = emulatorControllerStub.channel.newCall(streamScreenshotMethod, emulatorControllerStub.callOptions)
    ClientCalls.asyncServerStreamingCall(call, imageFormat, DelegatingStreamObserver(streamObserver, streamScreenshotMethod))
    return CancelableClientCall(call);
  }

  /**
   * Retrieves the status of the emulator.
   */
  fun getStatus(streamObserver: StreamObserver<EmulatorStatus>) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getStatus()")
    }
    emulatorControllerStub.getStatus(EMPTY_PROTO, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetStatusMethod()))
  }

  /**
   * Sets a virtual machine state.
   */
  fun setVmState(vmState: VmRunState, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setVmState(${shortDebugString(vmState)})")
    }
    emulatorControllerStub.setVmState(vmState, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetVmStateMethod()))
  }

  /**
   * Retrieves configurations of all displays.
   */
  fun getDisplayConfigurations(streamObserver: StreamObserver<DisplayConfigurations>) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getDisplayConfigurations()")
    }
    emulatorControllerStub.getDisplayConfigurations(
        EMPTY_PROTO, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetDisplayConfigurationsMethod()))
  }

  /**
   * Creates, modifies, or deletes configurable secondary displays.
   */
  fun setDisplayConfigurations(displayConfigurations: DisplayConfigurations,
                               streamObserver: StreamObserver<DisplayConfigurations> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setDisplayConfigurations(${shortDebugString(displayConfigurations)})")
    }
    emulatorControllerStub.setDisplayConfigurations(
        displayConfigurations, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetDisplayConfigurationsMethod()))
  }

  /**
   * Changes orientation of the virtual scene camera.
   */
  fun rotateVirtualSceneCamera(cameraRotation: RotationRadian, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("rotateVirtualSceneCamera(${shortDebugString(cameraRotation)})")
    }
    emulatorControllerStub.rotateVirtualSceneCamera(
        cameraRotation, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getRotateVirtualSceneCameraMethod()))
  }

  /**
   * Changes velocity of the virtual scene camera.
   */
  fun setVirtualSceneCameraVelocity(cameraVelocity: Velocity, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setVirtualSceneCameraVelocity(${shortDebugString(cameraVelocity)})")
    }
    emulatorControllerStub.setVirtualSceneCameraVelocity(
        cameraVelocity, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetVirtualSceneCameraVelocityMethod()))
  }

  /**
   * Lists existing snapshots. Only the snapshots compatible with the running emulator are returned.
   *
   * @param snapshotFilter determines whether all or only compatible snapshots are returned
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case)
   */
  fun listSnapshots(snapshotFilter: SnapshotFilter, streamObserver: StreamObserver<SnapshotList> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("listSnapshots()")
    }
    snapshotServiceStub.listSnapshots(
        snapshotFilter, DelegatingStreamObserver(streamObserver, SnapshotServiceGrpc.getListSnapshotsMethod()))
  }

  /**
   * Loads a snapshot in the emulator.
   *
   * @param snapshotId the ID of the snapshot to load
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case)
   */
  fun loadSnapshot(snapshotId: String, streamObserver: StreamObserver<SnapshotPackage> = getEmptyObserver()) {
    val snapshot = SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("loadSnapshot(${shortDebugString(snapshot)})")
    }
    snapshotServiceStub.loadSnapshot(snapshot, DelegatingStreamObserver(streamObserver, SnapshotServiceGrpc.getLoadSnapshotMethod()))
  }

  /**
   * Pushes snapshot packages into the emulator.
   *
   * Usually multiple packages need to be sent to push one snapshot file, including one header and one or more
   * payload packages. The implementation of the stream observer must handle asynchronous events correctly,
   * especially when pushing big files. This is usually done by overwriting [ClientResponseObserver.beforeStart]
   * and calling [io.grpc.stub.CallStreamObserver.setOnReadyHandler] from there.
   *
   * @param streamObserver a client stream observer to handle events
   * @return a StreamObserver that can be used to trigger the push
   */
  fun pushSnapshot(streamObserver: ClientResponseObserver<SnapshotPackage, SnapshotPackage>): StreamObserver<SnapshotPackage> {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("pushSnapshot()")
    }
    return snapshotServiceStub.pushSnapshot(DelegatingClientResponseObserver(streamObserver, SnapshotServiceGrpc.getPushSnapshotMethod()))
  }

  /**
   * Creates a snapshot of the current emulator state.
   *
   * @param snapshotId the ID of the snapshot to create
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case)
   */
  fun saveSnapshot(snapshotId: String, streamObserver: StreamObserver<SnapshotPackage> = getEmptyObserver()) {
    val snapshot = SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("saveSnapshot(${shortDebugString(snapshot)})")
    }
    snapshotServiceStub.saveSnapshot(snapshot, DelegatingStreamObserver(streamObserver, SnapshotServiceGrpc.getSaveSnapshotMethod()))
  }

  /**
   * Delete a snapshot in the emulator.
   *
   * @param snapshotId the ID of the snapshot to delete
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case)
   */
  fun deleteSnapshot(snapshotId: String, streamObserver: StreamObserver<SnapshotPackage> = getEmptyObserver()) {
    val snapshot = SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("deleteSnapshot(${shortDebugString(snapshot)})")
    }
    snapshotServiceStub.deleteSnapshot(snapshot, DelegatingStreamObserver(streamObserver, SnapshotServiceGrpc.getSaveSnapshotMethod()))
  }

  /**
   * Shows the extended controls of the emulator.
   *
   * @param pane identifies the window position and the pane to open
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case)
   */
  fun showExtendedControls(pane: PaneEntry, streamObserver: StreamObserver<ExtendedControlsStatus> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("showExtendedControls(${shortDebugString(pane)})")
    }
    uiControllerStub.showExtendedControls(pane, DelegatingStreamObserver(streamObserver, UiControllerGrpc.getShowExtendedControlsMethod()))
  }

  /**
   * Closes the extended controls of the emulator.
   *
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case)
   */
  fun closeExtendedControls(streamObserver: StreamObserver<ExtendedControlsStatus> = getEmptyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("closeExtendedControls()")
    }
    uiControllerStub.closeExtendedControls(EMPTY_PROTO,
                                           DelegatingStreamObserver(streamObserver, UiControllerGrpc.getCloseExtendedControlsMethod()))
  }

  /**
   * Sets the UI style for the extended controls of the emulator.
   *
   * @param style the style to set
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case)
   */
  fun setUiTheme(style: ThemingStyle.Style, streamObserver: StreamObserver<Empty> = getEmptyObserver()) {
    val themingStyle = ThemingStyle.newBuilder().setStyle(style).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setUiTheme(${shortDebugString(themingStyle)})")
    }
    uiControllerStub.setUiTheme(themingStyle, DelegatingStreamObserver(streamObserver, UiControllerGrpc.getSetUiThemeMethod()))
  }

  private fun sendKeepAlive() {
    val responseObserver = object : EmptyStreamObserver<VmRunState>() {
      override fun onNext(response: VmRunState) {
        connectionState = ConnectionState.CONNECTED
        if (emulatorState.get() == EmulatorState.SHUTDOWN_REQUESTED) {
          sendShutdown()
        }
        else {
          alarm.addRequest(::sendKeepAlive, KEEP_ALIVE_INTERVAL_MILLIS)
        }
      }

      override fun onError(t: Throwable) {
        if (t is StatusRuntimeException && t.status.code == Status.Code.UNAVAILABLE || connectionState != ConnectionState.CONNECTED) {
          connectionState = ConnectionState.DISCONNECTED
        }
      }
    }

    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getVmState()")
    }
    val timeout = if (connectionState == ConnectionState.CONNECTED) 3L else 15L
    emulatorControllerStub.withDeadlineAfter(timeout, TimeUnit.SECONDS)
      .getVmState(EMPTY_PROTO, DelegatingStreamObserver(responseObserver, EmulatorControllerGrpc.getGetVmStateMethod()))
  }

  private fun throwNotYetConnected(): Nothing {
    throw IllegalStateException("Not yet connected to the Emulator")
  }

  override fun dispose() {
    channel?.shutdown()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as EmulatorController

    return emulatorId == other.emulatorId
  }

  override fun hashCode(): Int {
    return emulatorId.hashCode()
  }

  /**
   * The state of the [EmulatorController].
   */
  enum class ConnectionState {
    NOT_INITIALIZED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
  }

  enum class EmulatorState {
    RUNNING,
    SHUTDOWN_REQUESTED,
    SHUTDOWN_SENT
  }

  inner class CancelableClientCall(private val call: ClientCall<*, *>) : Cancelable {

    override fun cancel() {
      if (connectionState == ConnectionState.CONNECTED) {
        call.cancel("Canceled by consumer", null)
      }
    }
  }

  open inner class DelegatingStreamObserver<RequestT, ResponseT>(
    open val delegate: StreamObserver<in ResponseT>?,
    val method: MethodDescriptor<in RequestT, in ResponseT>
  ) : StreamObserver<ResponseT> {

    override fun onNext(response: ResponseT) {
      delegate?.onNext(response)
    }

    override fun onError(t: Throwable) {
      if (!(t is StatusRuntimeException && t.status.code == Status.Code.CANCELLED) && channel?.isShutdown == false) {
        LOG.warn("${method.fullMethodName} call failed - ${t.message}")
      }

      delegate?.onError(t)

      if (t is StatusRuntimeException && t.status.code == Status.Code.UNAVAILABLE) {
        connectionState = ConnectionState.DISCONNECTED
      }
    }

    override fun onCompleted() {
      delegate?.onCompleted()
    }
  }

  inner class DelegatingClientResponseObserver<RequestT, ResponseT>(
    override val delegate: ClientResponseObserver<RequestT, ResponseT>?,
    method: MethodDescriptor<in RequestT, in ResponseT>
  ) : DelegatingStreamObserver<RequestT, ResponseT>(delegate, method), ClientResponseObserver<RequestT, ResponseT> {

    override fun beforeStart(requestStream: ClientCallStreamObserver<RequestT>?) {
      delegate?.beforeStart(requestStream)
    }
  }

  /**
   * Defines interface for an object that receives notifications when the state of the Emulator
   * connection changes.
   */
  interface ConnectionStateListener {
    /**
     * Called when the state of the Emulator connection changes.
     */
    @AnyThread
    fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState)
  }

  private class TokenCallCredentials(private val token: String) : CallCredentials() {

    override fun applyRequestMetadata(requestInfo: RequestInfo, executor: Executor, applier: MetadataApplier) {
      executor.execute {
        try {
          val headers = Metadata()
          headers.put(AUTHORIZATION_METADATA_KEY, "Bearer $token")
          applier.apply(headers)
        }
        catch (e: Throwable) {
          thisLogger().error(e)
          applier.fail(Status.UNAUTHENTICATED.withCause(e))
        }
      }
    }

    override fun thisUsesUnstableApi() {
    }
  }
}

/**
 * Marshaller for the [Image] objects that implements custom deserialization, which, unlike the standard
 * one, doesn't allocate short-lived humongous objects (b/180151949).
 */
private class ImageResponseMarshaller : Marshaller<Image> {
  private val reusableBuffer = ThreadLocal<Reference<ByteArray>>()

  override fun stream(response: Image): InputStream {
    throw UnsupportedOperationException() // This marshaller is never used for serialization.
  }

  override fun parse(stream: InputStream): Image {
    try {
      val codedStream = createCodedInputStream(stream) ?: return Image.getDefaultInstance()
      return parseImage(codedStream)
    } catch (e: InvalidProtocolBufferException) {
      throw Status.INTERNAL.withDescription("Invalid protobuf byte sequence").withCause(e).asRuntimeException()
    }
  }

  @Throws(InvalidProtocolBufferException::class)
  private fun createCodedInputStream(stream: InputStream): CodedInputStream? {
    val codedStream: CodedInputStream
    try {
      if (stream is KnownLength) {
        val size = stream.available()
        if (size == 0) {
          return null
        }

        var buf = reusableBuffer.get()?.get()
        if (buf == null || buf.size < size) {
          buf = ByteArray(size)
          reusableBuffer.set(WeakReference(buf))
        }

        var remaining = size
        while (remaining > 0) {
          val position = size - remaining
          val count = stream.read(buf, position, remaining)
          if (count < 0) {
            break
          }
          remaining -= count
        }

        if (remaining != 0) {
          val position = size - remaining
          throw RuntimeException("Inaccurate size: $size != $position")
        }
        codedStream = UnsafeByteOperations.unsafeWrap(buf, 0, size).newCodedInput()
        codedStream.enableAliasing(true)
      }
      else {
        codedStream = CodedInputStream.newInstance(stream)
      }
    }
    catch (e: IOException) {
      throw InvalidProtocolBufferException(e)
    }
    // Remove the size limit restriction for parsing since the CodedInputStream is pre-created.
    codedStream.setSizeLimit(Int.MAX_VALUE)
    return codedStream
  }

  @Throws(InvalidProtocolBufferException::class)
  private fun parseImage(input: CodedInputStream): Image {
    val builder = Image.newBuilder()

    try {
      while (true) {
        when (val tag = input.readTag()) {
          0 -> break
          FORMAT_FIELD_TAG -> builder.format = input.readMessage(ImageFormat.parser(), EMPTY_REGISTRY) as ImageFormat
          IMAGE_FIELD_TAG -> builder.image = input.readBytes()
          SEQ_FIELD_TAG-> builder.seq = input.readUInt32()
          TIMESTAMPUS_FIELD_TAG -> builder.timestampUs = input.readUInt64()
          else -> if (!input.skipField(tag)) break
        }
      }
      input.checkLastTagWas(0)
      return builder.build()
    }
    catch (e: InvalidProtocolBufferException) {
      throw e.setUnfinishedMessage(builder.build())
    }
    catch (e: IOException) {
      throw InvalidProtocolBufferException(e).setUnfinishedMessage(builder.build())
    }
  }
}

private const val FORMAT_FIELD_TAG = Image.FORMAT_FIELD_NUMBER shl 3 or WireFormat.WIRETYPE_LENGTH_DELIMITED
private const val IMAGE_FIELD_TAG = Image.IMAGE_FIELD_NUMBER shl 3 or WireFormat.WIRETYPE_LENGTH_DELIMITED
private const val SEQ_FIELD_TAG = Image.SEQ_FIELD_NUMBER shl 3 or WireFormat.WIRETYPE_VARINT
private const val TIMESTAMPUS_FIELD_TAG = Image.TIMESTAMPUS_FIELD_NUMBER shl 3 or WireFormat.WIRETYPE_VARINT

private val EMPTY_REGISTRY = ExtensionRegistryLite.getEmptyRegistry()

private val AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
private val KEEP_ALIVE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(2)
private val LOG = Logger.getInstance(EmulatorController::class.java)
private val EMPTY_OBSERVER = EmptyStreamObserver<Any>()
private val EMPTY_PROTO = Empty.getDefaultInstance()

@Suppress("UNCHECKED_CAST")
fun <T> getEmptyObserver(): StreamObserver<T> {
  return EMPTY_OBSERVER as StreamObserver<T>
}

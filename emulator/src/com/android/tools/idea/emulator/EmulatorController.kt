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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.Slow
import com.android.emulator.control.ClipData
import com.android.emulator.control.EmulatorControllerGrpc
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.MouseEvent
import com.android.emulator.control.PhysicalModelValue
import com.android.emulator.control.SnapshotPackage
import com.android.emulator.control.SnapshotServiceGrpc
import com.android.emulator.control.VmRunState
import com.android.ide.common.util.Cancelable
import com.android.tools.idea.emulator.RuntimeConfigurationOverrider.getRuntimeConfiguration
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_GRPC_CALLS
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import io.grpc.CallCredentials
import io.grpc.CompressorRegistry
import io.grpc.ConnectivityState
import io.grpc.DecompressorRegistry
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientCalls
import io.grpc.stub.ClientResponseObserver
import io.grpc.stub.StreamObserver
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Controls a running Emulator.
 */
class EmulatorController(val emulatorId: EmulatorId, parentDisposable: Disposable) : Disposable {
  private var channel: ManagedChannel? = null
  @Volatile private var emulatorControllerStub: EmulatorControllerGrpc.EmulatorControllerStub? = null
  @Volatile private var snapshotServiceStub: SnapshotServiceGrpc.SnapshotServiceStub? = null
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
      if (connectionStateInternal.getAndSet(value) != value) {
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

  private var emulatorController: EmulatorControllerGrpc.EmulatorControllerStub
    get() {
      return emulatorControllerStub ?: throwNotYetConnected()
    }
    private inline set(stub) {
      emulatorControllerStub = stub
    }

  private var snapshotService: SnapshotServiceGrpc.SnapshotServiceStub
    get() {
      return snapshotServiceStub ?: throwNotYetConnected()
    }
    private inline set(stub) {
      snapshotServiceStub = stub
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

    // TODO: Change 4 to 3 after b/150494232 is fixed.
    maxInboundMessageSize = config.displayWidth * config.displayHeight * 4 + 100

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
      emulatorController = EmulatorControllerGrpc.newStub(channel).withDeadlineAfter(20, TimeUnit.SECONDS)
      snapshotService = SnapshotServiceGrpc.newStub(channel).withDeadlineAfter(20, TimeUnit.SECONDS)
    }
    else {
      val credentials = TokenCallCredentials(token)
      emulatorController = EmulatorControllerGrpc.newStub(channel).withCallCredentials(credentials)
      snapshotService = SnapshotServiceGrpc.newStub(channel).withCallCredentials(credentials)
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
   * Sets contents of the clipboard.
   */
  fun setClipboard(clipData: ClipData, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setClipboard(${shortDebugString(clipData)})")
    }
    emulatorController.setClipboard(clipData, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetClipboardMethod()))
  }

  /**
   * Streams contents of the clipboard.
   */
  fun streamClipboard(streamObserver: StreamObserver<ClipData>): Cancelable? {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("streamClipboard()")
    }
    val method = EmulatorControllerGrpc.getStreamClipboardMethod()
    val call = emulatorController.channel.newCall(method, emulatorController.callOptions)
    ClientCalls.asyncServerStreamingCall(call, Empty.getDefaultInstance(), DelegatingStreamObserver(streamObserver, method))
    return object : Cancelable {
      override fun cancel() {
        call.cancel("Canceled by consumer", null)
      }
    }
  }

  /**
   * Sends a [KeyboardEvent] to the Emulator.
   */
  fun sendKey(keyboardEvent: KeyboardEvent, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("sendKey(${shortDebugString(keyboardEvent)})")
    }
    emulatorController.sendKey(keyboardEvent, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSendKeyMethod()))
  }

  /**
   * Sends a [MouseEvent] to the Emulator.
   */
  fun sendMouse(mouseEvent: MouseEvent, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS.get()) {
      LOG.info("sendMouse(${shortDebugString(mouseEvent)})")
    }
    emulatorController.sendMouse(mouseEvent, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSendMouseMethod()))
  }

  /**
   * Retrieves a physical model value.
   */
  fun getPhysicalModel(physicalType: PhysicalModelValue.PhysicalType, streamObserver: StreamObserver<PhysicalModelValue>) {
    val modelValue = PhysicalModelValue.newBuilder().setTarget(physicalType).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getPhysicalModel(${shortDebugString(modelValue)})")
    }
    emulatorController.getPhysicalModel(
        modelValue, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetPhysicalModelMethod()))
  }

  /**
   * Sets a physical model value.
   */
  fun setPhysicalModel(modelValue: PhysicalModelValue, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setPhysicalModel(${shortDebugString(modelValue)})")
    }
    emulatorController.setPhysicalModel(
        modelValue, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetPhysicalModelMethod()))
  }

  /**
   * Retrieves a screenshot of an Emulator display.
   */
  fun getScreenshot(imageFormat: ImageFormat, streamObserver: StreamObserver<Image>) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getScreenshot(${shortDebugString(imageFormat)})")
    }
    emulatorController.getScreenshot(imageFormat, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getGetScreenshotMethod()))
  }

  /**
   * Streams a series of screenshots.
   */
  fun streamScreenshot(imageFormat: ImageFormat, streamObserver: StreamObserver<Image>): Cancelable? {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("streamScreenshot(${shortDebugString(imageFormat)})")
    }
    val method = EmulatorControllerGrpc.getStreamScreenshotMethod()
    val call = emulatorController.channel.newCall(method, emulatorController.callOptions)
    ClientCalls.asyncServerStreamingCall(call, imageFormat, DelegatingStreamObserver(streamObserver, method))
    return object : Cancelable {
      override fun cancel() {
        call.cancel("Canceled by consumer", null)
      }
    }
  }

  /**
   * Sets a virtual machine state.
   */
  fun setVmState(vmState: VmRunState, streamObserver: StreamObserver<Empty> = getDummyObserver()) {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("setVmModel(${shortDebugString(vmState)})")
    }
    emulatorController.setVmState(vmState, DelegatingStreamObserver(streamObserver, EmulatorControllerGrpc.getSetVmStateMethod()))
  }

  private fun sendKeepAlive() {
    val responseObserver = object : DummyStreamObserver<VmRunState>() {
      override fun onNext(response: VmRunState) {
        connectionState = ConnectionState.CONNECTED
        if (emulatorState.get() == EmulatorState.SHUTDOWN_REQUESTED) {
          sendShutdown()
        }
        else {
          alarm.addRequest({ sendKeepAlive() }, KEEP_ALIVE_INTERVAL_MILLIS)
        }
      }

      override fun onError(t: Throwable) {
        connectionState = ConnectionState.DISCONNECTED
      }
    }

    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("getVmState()")
    }
    val timeout = if (connectionState == ConnectionState.CONNECTED) 3L else 15L
    emulatorController.withDeadlineAfter(timeout, TimeUnit.SECONDS)
        .getVmState(Empty.getDefaultInstance(), DelegatingStreamObserver(responseObserver, EmulatorControllerGrpc.getGetVmStateMethod()))
  }

  fun saveSnapshot(snapshotId: String, streamObserver: StreamObserver<SnapshotPackage> = getDummyObserver()) {
    val snapshot = SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("saveSnapshot(${shortDebugString(snapshot)})")
    }
    snapshotService.saveSnapshot(snapshot, DelegatingStreamObserver(streamObserver, SnapshotServiceGrpc.getSaveSnapshotMethod()))
  }

  /**
   * Loads a snapshot in the emulator.
   *
   * @param snapshotId a snapshot ID in the emulator.
   * @param streamObserver a stream observer to observe the response stream (which contains only 1 message in this case).
   */
  fun loadSnapshot(snapshotId: String, streamObserver: StreamObserver<SnapshotPackage> = getDummyObserver()) {
    val snapshot = SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build()
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("loadSnapshot(${shortDebugString(snapshot)})")
    }
    snapshotService.loadSnapshot(snapshot, DelegatingStreamObserver(streamObserver, SnapshotServiceGrpc.getLoadSnapshotMethod()))
  }

  /**
   * Pushes snapshot packages into the emulator.
   *
   * Usually multiple packages need to be sent to push one snapshot file, including one header and one or more
   * payload packages. The implementation of the stream observer must handle asynchronous events correctly,
   * especially when pushing big files. This is usually done by overwriting [ClientResponseObserver.beforeStart]
   * and calling [io.grpc.stub.CallStreamObserver.setOnReadyHandler] from there.
   *
   * @param streamObserver a client stream observer to handle events.
   * @return a StreamObserver that can be used to trigger the push.
   */
  fun pushSnapshot(streamObserver: ClientResponseObserver<SnapshotPackage, SnapshotPackage>): StreamObserver<SnapshotPackage> {
    if (EMBEDDED_EMULATOR_TRACE_GRPC_CALLS.get()) {
      LOG.info("pushSnapshot()")
    }
    return snapshotService.pushSnapshot(DelegatingClientResponseObserver(streamObserver, SnapshotServiceGrpc.getPushSnapshotMethod()))
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

  private open inner class DelegatingStreamObserver<RequestT, ResponseT>(
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

  private open inner class DelegatingClientResponseObserver<RequestT, ResponseT>(
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
          applier.fail(Status.UNAUTHENTICATED.withCause(e))
        }
      }
    }

    override fun thisUsesUnstableApi() {
    }
  }
}

private val AUTHORIZATION_METADATA_KEY = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
private val KEEP_ALIVE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(2)
private val LOG = Logger.getInstance(EmulatorController::class.java)
private val DUMMY_OBSERVER = DummyStreamObserver<Any>()

@Suppress("UNCHECKED_CAST")
fun <T> getDummyObserver(): StreamObserver<T> {
  return DUMMY_OBSERVER as StreamObserver<T>
}

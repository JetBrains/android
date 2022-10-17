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

import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.ClipData
import com.android.emulator.control.DisplayConfiguration
import com.android.emulator.control.DisplayConfigurations
import com.android.emulator.control.EmulatorControllerGrpc
import com.android.emulator.control.EmulatorStatus
import com.android.emulator.control.ExtendedControlsStatus
import com.android.emulator.control.FoldedDisplay
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.ImageFormat.ImgFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.MouseEvent
import com.android.emulator.control.Notification
import com.android.emulator.control.Notification.EventType
import com.android.emulator.control.PaneEntry
import com.android.emulator.control.PhysicalModelValue
import com.android.emulator.control.Rotation
import com.android.emulator.control.Rotation.SkinRotation
import com.android.emulator.control.RotationRadian
import com.android.emulator.control.SnapshotDetails
import com.android.emulator.control.SnapshotFilter
import com.android.emulator.control.SnapshotList
import com.android.emulator.control.SnapshotPackage
import com.android.emulator.control.SnapshotServiceGrpc
import com.android.emulator.control.ThemingStyle
import com.android.emulator.control.TouchEvent
import com.android.emulator.control.UiControllerGrpc
import com.android.emulator.control.Velocity
import com.android.emulator.control.VmRunState
import com.android.emulator.snapshot.SnapshotOuterClass.Snapshot
import com.android.io.writeImage
import com.android.testutils.TestUtils
import com.android.tools.adtui.ImageUtils.rotateByQuadrants
import com.android.tools.idea.io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import com.android.tools.idea.io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import com.android.tools.idea.io.grpc.Metadata
import com.android.tools.idea.io.grpc.Server
import com.android.tools.idea.io.grpc.ServerCall
import com.android.tools.idea.io.grpc.ServerCallHandler
import com.android.tools.idea.io.grpc.ServerInterceptor
import com.android.tools.idea.io.grpc.ServerInterceptors
import com.android.tools.idea.io.grpc.Status
import com.android.tools.idea.io.grpc.StatusRuntimeException
import com.android.tools.idea.io.grpc.inprocess.InProcessServerBuilder
import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.CodedOutputStream
import com.android.tools.idea.protobuf.Empty
import com.android.tools.idea.protobuf.MessageOrBuilder
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.ui.UIUtil
import org.junit.Assert.fail
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.RenderingHints.KEY_ANTIALIASING
import java.awt.RenderingHints.KEY_RENDERING
import java.awt.RenderingHints.VALUE_ANTIALIAS_ON
import java.awt.RenderingHints.VALUE_RENDER_QUALITY
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.CopyOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import com.android.emulator.control.DisplayMode as DisplayModeMessage
import com.android.emulator.snapshot.SnapshotOuterClass.Image as SnapshotImage

/**
 * Fake emulator for use in tests. Provides in-process gRPC services.
 */
class FakeEmulator(val avdFolder: Path, val grpcPort: Int, registrationDirectory: Path, standalone: Boolean = false) {

  val avdId = StringUtil.trimExtensions(avdFolder.fileName.toString())
  private val registration: String
  private val registrationFile = registrationDirectory.resolve("pid_${grpcPort + 12345}.ini")
  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FakeEmulatorControllerService", 1)
  private var grpcServer = createGrpcServer()
  private val lifeCycleLock = Object()
  private var startTime = 0L

  private val config = EmulatorConfiguration.readAvdDefinition(avdId, avdFolder)!!

  @Volatile var displayRotation: SkinRotation = SkinRotation.PORTRAIT
  private var foldedDisplay: FoldedDisplay? = null
  private var screenshotStreamRequest: ImageFormat? = null
  @Volatile private var screenshotStreamObserver: StreamObserver<Image>? = null
  @Volatile private var clipboardStreamObserver: StreamObserver<ClipData>? = null
  @Volatile private var notificationStreamObserver: StreamObserver<Notification>? = null
  private var displays = listOf(DisplayConfiguration.newBuilder().setWidth(config.displayWidth).setHeight(config.displayHeight).build())

  private val clipboardInternal = AtomicReference("")
  var clipboard: String
    get() = clipboardInternal.get()
    set(value) {
      val oldValue = clipboardInternal.getAndSet(value)
      if (value != oldValue) {
        executor.execute {
          clipboardStreamObserver?.let { sendStreamingResponse(it, ClipData.newBuilder().setText(value).build()) }
        }
      }
    }
  private var virtualSceneCameraActiveInternal = AtomicBoolean()
  var virtualSceneCameraActive: Boolean
    get() = virtualSceneCameraActiveInternal.get()
    set(value) {
      val oldValue = virtualSceneCameraActiveInternal.getAndSet(value)
      if (value != oldValue) {
        executor.execute {
          notificationStreamObserver?.let {
            sendStreamingResponse(it, createVirtualSceneCameraNotification(value))
          }
        }
      }
    }
  var displayMode = config.displayModes.firstOrNull { it.width == config.displayWidth && it.height == config.displayHeight }

  @Volatile var extendedControlsVisible = false

  private var frameNumber = 0
  /** Ids of snapshots that were created by calling the [createIncompatibleSnapshot] method. */
  private val incompatibleSnapshots = ContainerUtil.newConcurrentSet<String>()

  /** The ID of the last loaded snapshot. */
  private var lastLoadedSnapshot: String? = null

  val serialPort
    get() = grpcPort - 3000 // Just like a real emulator.

  val grpcCallLog = LinkedBlockingDeque<GrpcCallRecord>()
  private val grpcSemaphore = Semaphore(Int.MAX_VALUE)

  init {
    val embeddedFlags = if (standalone) {
      ""
    }
    else {
      """ "-qt-hide-window" "-idle-grpc-timeout" "300""""
    }

    registration = """
      port.serial=${serialPort}
      port.adb=${serialPort + 1}
      avd.name=${avdId}
      avd.dir=${avdFolder}
      avd.id=${avdId}
      cmdline="/emulator_home/fake_emulator" "-netdelay" "none" "-netspeed" "full" "-avd" "$avdId" $embeddedFlags
      grpc.port=${grpcPort}
      grpc.token=RmFrZSBnUlBDIHRva2Vu
      """.trimIndent()
  }

  /**
   * Starts the Emulator. The Emulator is fully initialized when the method returns.
   */
  fun start() {
    synchronized(lifeCycleLock) {
      val keysToExtract = setOf("fastboot.chosenSnapshotFile", "fastboot.forceChosenSnapshotBoot", "fastboot.forceFastBoot")
      val map = readKeyValueFile(avdFolder.resolve("config.ini"), keysToExtract)
      if (map != null) {
        val snapshotId = when {
          map["fastboot.forceFastBoot"] == "yes" -> "default_boot"
          map["fastboot.forceChosenSnapshotBoot"] == "yes" -> map["fastboot.chosenSnapshotFile"]
          else -> null
        }
        lastLoadedSnapshot = if (snapshotId == null || snapshotId in incompatibleSnapshots) null else snapshotId
      }

      startTime = System.currentTimeMillis()
      grpcCallLog.clear()
      grpcServer.start()
      Files.write(registrationFile, registration.toByteArray(UTF_8), CREATE_NEW)
    }
  }

  /**
   * Stops the Emulator. The Emulator is completely shut down when the method returns.
   */
  fun stop() {
    synchronized(lifeCycleLock) {
      if (startTime != 0L) {
        try {
          Files.delete(registrationFile)
        }
        catch (ignore: NoSuchFileException) {
        }
        grpcServer.shutdown()
        startTime = 0
      }
    }
  }

  /**
   * Simulates an emulator crash. The Emulator is terminated but the registration file in not deleted.
   */
  fun crash() {
    synchronized(lifeCycleLock) {
      if (startTime != 0L) {
        grpcServer.shutdownNow()
        startTime = 0
      }
    }
  }

  /**
   * Adds, removes, updates secondary displays.
   */
  fun changeSecondaryDisplays(secondaryDisplays: List<DisplayConfiguration>) {
    executor.execute {
      val newDisplays = ArrayList<DisplayConfiguration>(secondaryDisplays.size + 1)
      newDisplays.add(displays[0])
      for (display in secondaryDisplays) {
        if (display.display > 0) {
          newDisplays.add(display)
        }
      }
      if (newDisplays != displays) {
        displays = newDisplays
        val notificationObserver = notificationStreamObserver ?: return@execute
        val response = Notification.newBuilder().setEvent(EventType.DISPLAY_CONFIGURATIONS_CHANGED_UI).build()
        sendStreamingResponse(notificationObserver, response)
      }
    }
  }

  /**
   * Folds/unfolds the primary display.
   */
  fun setFoldedDisplay(foldedDisplay: FoldedDisplay?) {
    executor.execute {
      if (foldedDisplay != this.foldedDisplay) {
        this.foldedDisplay = foldedDisplay
        val screenshotObserver = screenshotStreamObserver ?: return@execute
        val request = screenshotStreamRequest ?: return@execute
        sendScreenshot(request, screenshotObserver)
      }
    }
  }

  /**
   * Waits for the next gRPC call while dispatching UI events. Returns the next gRPC call and removes
   * it from the queue of recorded calls. Throws TimeoutException if the call is not recorded within
   * the specified timeout.
   */
  @UiThread
  @Throws(TimeoutException::class)
  fun getNextGrpcCall(timeout: Long, unit: TimeUnit, filter: Predicate<GrpcCallRecord> = defaultCallFilter): GrpcCallRecord {
    val timeoutMillis = unit.toMillis(timeout)
    val deadline = System.currentTimeMillis() + timeoutMillis
    var waitUnit = ((timeoutMillis + 9) / 10).coerceAtMost(10)
    while (waitUnit > 0) {
      UIUtil.dispatchAllInvocationEvents()
      val call = grpcCallLog.poll(waitUnit, TimeUnit.MILLISECONDS)
      if (call != null && filter.test(call)) {
        return call
      }
      waitUnit = waitUnit.coerceAtMost(deadline - System.currentTimeMillis())
    }
    throw TimeoutException()
  }

  /**
   * Clears the gRPC call log.
   */
  @UiThread
  fun clearGrpcCallLog() {
    grpcCallLog.clear()
  }

  fun pauseGrpc() {
    grpcSemaphore.drainPermits()
  }

  fun resumeGrpc() {
    grpcSemaphore.release(Int.MAX_VALUE)
  }

  private fun createGrpcServer(): Server {
    return InProcessServerBuilder.forName(grpcServerName(grpcPort))
      .executor(AppExecutorUtil.createBoundedApplicationPoolExecutor("FakeEmulator-gRPC", 1))
      .addService(ServerInterceptors.intercept(EmulatorControllerService(executor), LoggingInterceptor()))
      .addService(ServerInterceptors.intercept(EmulatorSnapshotService(executor), LoggingInterceptor()))
      .addService(ServerInterceptors.intercept(UiControllerService(executor), LoggingInterceptor()))
      .build()
  }

  private fun drawDisplayImage(size: Dimension, displayId: Int): BufferedImage {
    val image = BufferedImage(size.width, size.height, TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.paint = Color.WHITE
    g.fillRect(0, 0, size.width, size.height) // Fill with white to avoid partial transparency due to antialiasing.
    val hints = RenderingHints(mapOf(KEY_ANTIALIASING to VALUE_ANTIALIAS_ON, KEY_RENDERING to VALUE_RENDER_QUALITY))
    g.setRenderingHints(hints)
    val n = 10
    val m = 10
    val w = size.width.toDouble() / n
    val h = size.height.toDouble() / m
    val colorScheme = COLOR_SCHEMES[displayId]
    val startColor1 = colorScheme.start1
    val endColor1 = colorScheme.end1
    val startColor2 = colorScheme.start2
    val endColor2 = colorScheme.end2
    for (i in 0 until n) {
      for (j in 0 until m) {
        val x = w * i
        val y = h * j
        val triangle1 = Path2D.Double().apply {
          moveTo(x, y)
          lineTo(x + w, y)
          lineTo(x, y + h)
          closePath()
        }
        val triangle2 = Path2D.Double().apply {
          moveTo(x + w, y + h)
          lineTo(x + w, y)
          lineTo(x, y + h)
          closePath()
        }
        g.paint = interpolate(startColor1, endColor1, i.toDouble() / (n - 1))
        g.fill(triangle1)
        g.paint = interpolate(startColor2, endColor2, j.toDouble() / (m - 1))
        g.fill(triangle2)
      }
    }
    g.dispose()
    return image
  }

  fun createSnapshot(snapshotId: String) {
    val snapshotFolder = avdFolder.resolve("snapshots").resolve(snapshotId)
    Files.createDirectories(snapshotFolder)

    val image = drawDisplayImage(config.displaySize, 0)
    val screenshotFile = snapshotFolder.resolve("screenshot.png")
    image.writeImage("PNG", screenshotFile)

    val snapshotMessage = Snapshot.newBuilder()
      .addImages(SnapshotImage.getDefaultInstance()) // Need an image for the snapshot to be considered valid.
      .setCreationTime(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
      .build()
    val snapshotFile = snapshotFolder.resolve("snapshot.pb")
    Files.newOutputStream(snapshotFile, CREATE).use { stream ->
      val codedStream = CodedOutputStream.newInstance(stream)
      snapshotMessage.writeTo(codedStream)
      codedStream.flush()
    }
  }

  fun createIncompatibleSnapshot(snapshotId: String) {
    createSnapshot(snapshotId)
    incompatibleSnapshots.add(snapshotId)
  }

  private fun sendEmptyResponse(responseObserver: StreamObserver<Empty>) {
    sendResponse(responseObserver, Empty.getDefaultInstance())
  }

  private fun <T> sendResponse(responseObserver: StreamObserver<T>, response: T) {
    try {
      responseObserver.onNext(response)
      responseObserver.onCompleted()
    }
    catch (e: StatusRuntimeException) {
      if (e.status.code != Status.Code.CANCELLED) {
        throw e
      }
    }
  }

  private fun <T> sendStreamingResponse(responseObserver: StreamObserver<T>, response: T) {
    try {
      responseObserver.onNext(response)
    }
    catch (e: StatusRuntimeException) {
      if (e.status.code != Status.Code.CANCELLED) {
        throw e
      }
    }
  }

  private fun sendScreenshot(request: ImageFormat, responseObserver: StreamObserver<Image>) {
    val displayId = request.display
    val size = getScaledAndRotatedDisplaySize(request.width, request.height, displayId)
    val image = drawDisplayImage(size, displayId)
    val rotatedImage = rotateByQuadrants(image, displayRotation.number)
    val imageBytes = ByteArray(rotatedImage.width * rotatedImage.height * 3)
    var i = 0
    for (y in 0 until rotatedImage.height) {
      for (x in 0 until rotatedImage.width) {
        val rgb = rotatedImage.getRGB(x, y)
        imageBytes[i++] = (rgb ushr 16).toByte()
        imageBytes[i++] = (rgb ushr 8).toByte()
        imageBytes[i++] = rgb.toByte()
      }
    }

    val imageFormat = ImageFormat.newBuilder()
      .setFormat(ImgFormat.RGB888)
      .setWidth(rotatedImage.width)
      .setHeight(rotatedImage.height)
      .setRotation(Rotation.newBuilder().setRotation(displayRotation))
    foldedDisplay?.let { imageFormat.foldedDisplay = it }
    displayMode?.let { imageFormat.displayMode = it.displayModeId }

    val response = Image.newBuilder()
      .setImage(ByteString.copyFrom(imageBytes))
      .setFormat(imageFormat)
      .setSeq(++frameNumber)
    sendStreamingResponse(responseObserver, response.build())
  }

  private fun getScaledAndRotatedDisplaySize(width: Int, height: Int, displayId: Int): Dimension {
    val display = displays.find { it.display == displayId } ?: throw IllegalArgumentException()
    var displayWidth = display.width
    var displayHeight = display.height
    if (displayId == PRIMARY_DISPLAY_ID) {
      displayMode?.let {
        displayWidth = it.width
        displayHeight = it.height
      }
      foldedDisplay?.let {
        displayWidth = it.width
        displayHeight = it.height
      }
    }
    val aspectRatio = displayHeight.toDouble() / displayWidth
    val w = if (width == 0) displayWidth else width
    val h = if (height == 0) displayHeight else height
    return if (displayRotation.number % 2 == 0) {
      Dimension(w.coerceAtMost((h / aspectRatio).toInt()), h.coerceAtMost((w * aspectRatio).toInt()))
    }
    else {
      Dimension(h.coerceAtMost((w / aspectRatio).toInt()), w.coerceAtMost((h * aspectRatio).toInt()))
    }
  }

  private fun createVirtualSceneCameraNotification(cameraActive: Boolean): Notification {
    val event = if (cameraActive) EventType.VIRTUAL_SCENE_CAMERA_ACTIVE else EventType.VIRTUAL_SCENE_CAMERA_INACTIVE
    return Notification.newBuilder().setEvent(event).build()
  }

  private inline fun <T> Semaphore.withPermit(action: () -> T): T {
    acquire()
    try {
      return action()
    } finally {
      release()
    }
  }

  private inner class EmulatorControllerService(
    private val executor: ExecutorService
  ) : EmulatorControllerGrpc.EmulatorControllerImplBase() {

    override fun setPhysicalModel(request: PhysicalModelValue, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        if (request.target == PhysicalModelValue.PhysicalType.ROTATION) {
          val zAngle = request.value.getData(2)
          displayRotation = SkinRotation.forNumber(((zAngle / 90).roundToInt() + 4) % 4)
        }
        sendEmptyResponse(responseObserver)
      }
    }

    override fun getStatus(request: Empty, responseObserver: StreamObserver<EmulatorStatus>) {
      executor.execute {
        val response = EmulatorStatus.newBuilder()
          .setUptime(System.currentTimeMillis() - startTime)
          .setBooted(true)
          .build()
        sendResponse(responseObserver, response)
      }
    }

    override fun setClipboard(request: ClipData, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        clipboardInternal.set(request.text)
        sendEmptyResponse(responseObserver)
      }
    }

    override fun streamClipboard(request: Empty, responseObserver: StreamObserver<ClipData>) {
      executor.execute {
        clipboardStreamObserver = responseObserver
        val response = ClipData.newBuilder().setText(clipboardInternal.get()).build()
        sendStreamingResponse(responseObserver, response)
      }
    }

    override fun streamNotification(request: Empty, responseObserver: StreamObserver<Notification>) {
      executor.execute {
        notificationStreamObserver = responseObserver
        val response = createVirtualSceneCameraNotification(virtualSceneCameraActive)
        sendStreamingResponse(responseObserver, response)
      }
    }

    override fun getDisplayConfigurations(request: Empty, responseObserver: StreamObserver<DisplayConfigurations>) {
      executor.execute {
        val response = DisplayConfigurations.newBuilder().addAllDisplays(displays).build()
        sendResponse(responseObserver, response)
      }
    }

    override fun setDisplayMode(request: DisplayModeMessage, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        val changed = displayMode?.displayModeId != request.value
        displayMode = config.displayModes.firstOrNull { it.displayModeId == request.value }
        sendEmptyResponse(responseObserver)
        if (changed) {
          val screenshotObserver = screenshotStreamObserver ?: return@execute
          val screenshotRequest = screenshotStreamRequest ?: return@execute
          sendScreenshot(screenshotRequest, screenshotObserver)
        }
      }
    }

    override fun sendKey(request: KeyboardEvent, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        sendEmptyResponse(responseObserver)
      }
    }

    override fun sendMouse(request: MouseEvent, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        sendEmptyResponse(responseObserver)
      }
    }

    override fun sendTouch(request: TouchEvent, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        sendEmptyResponse(responseObserver)
      }
    }

    override fun getVmState(request: Empty, responseObserver: StreamObserver<VmRunState>) {
      executor.execute {
        val response = VmRunState.newBuilder()
          .setState(VmRunState.RunState.RUNNING)
          .build()
        sendResponse(responseObserver, response)
      }
    }

    override fun setVmState(request: VmRunState, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        sendEmptyResponse(responseObserver)
        if (request.state == VmRunState.RunState.SHUTDOWN) {
          stop()
        }
      }
    }

    override fun rotateVirtualSceneCamera(request: RotationRadian, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        sendEmptyResponse(responseObserver)
      }
    }

    override fun setVirtualSceneCameraVelocity(request: Velocity, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        sendEmptyResponse(responseObserver)
      }
    }

    override fun getScreenshot(request: ImageFormat, responseObserver: StreamObserver<Image>) {
      executor.execute {
        val displayId = request.display
        val size = getScaledAndRotatedDisplaySize(request.width, request.height, displayId)
        val image = drawDisplayImage(size, displayId)
        val stream = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", stream)

        val imageFormat = ImageFormat.newBuilder()
          .setFormat(ImgFormat.PNG)
          .setWidth(image.width)
          .setHeight(image.height)
          .setRotation(Rotation.newBuilder().setRotation(displayRotation))
        displayMode?.let { imageFormat.displayMode = it.displayModeId }

        val response = Image.newBuilder()
          .setImage(ByteString.copyFrom(stream.toByteArray()))
          .setFormat(imageFormat)
        sendResponse(responseObserver, response.build())
      }
    }

    override fun streamScreenshot(request: ImageFormat, responseObserver: StreamObserver<Image>) {
      executor.execute {
        screenshotStreamObserver = responseObserver
        screenshotStreamRequest = request
        sendScreenshot(request, responseObserver)
      }
    }
  }

  private inner class EmulatorSnapshotService(private val executor: ExecutorService) : SnapshotServiceGrpc.SnapshotServiceImplBase() {

    override fun listSnapshots(request: SnapshotFilter, responseObserver: StreamObserver<SnapshotList>) {
      executor.execute {
        val response = SnapshotList.newBuilder()
        val snapshotsFolder = avdFolder.resolve("snapshots")
        try {
          Files.list(snapshotsFolder).use { stream ->
            stream.forEach { snapshotFolder ->
              val snapshotId = snapshotFolder.fileName.toString()
              val invalid = snapshotId in incompatibleSnapshots
              if (request.statusFilter == SnapshotFilter.LoadStatus.All || !invalid) {
                val snapshotProtoFile = snapshotFolder.resolve("snapshot.pb")
                val snapshotMessage = Files.newInputStream(snapshotProtoFile).use {
                  Snapshot.parseFrom(it)
                }
                val snapshotDetails = SnapshotDetails.newBuilder()
                snapshotDetails.snapshotId = snapshotId
                snapshotDetails.status = when {
                  invalid -> SnapshotDetails.LoadStatus.Incompatible
                  snapshotId == lastLoadedSnapshot -> SnapshotDetails.LoadStatus.Loaded
                  else -> SnapshotDetails.LoadStatus.Compatible
                }
                val details = Snapshot.newBuilder()
                details.logicalName = snapshotMessage.logicalName
                details.description = snapshotMessage.description
                details.rotation = snapshotMessage.rotation
                details.creationTime = snapshotMessage.creationTime
                snapshotDetails.setDetails(details)
                response.addSnapshots(snapshotDetails)
              }
            }
          }
        }
        catch (_: NoSuchFileException) {
          // The "snapshots" folder hasn't been created yet - ignore to return an empty snapshot list.
        }

        sendResponse(responseObserver, response.build())
      }
    }

    override fun loadSnapshot(request: SnapshotPackage, responseObserver: StreamObserver<SnapshotPackage>) {
      executor.execute {
        val success = request.snapshotId !in incompatibleSnapshots
        if (success) {
          lastLoadedSnapshot = request.snapshotId
        }
        sendResponse(responseObserver, SnapshotPackage.newBuilder().setSuccess(success).build())
      }
    }

    override fun pushSnapshot(responseObserver: StreamObserver<SnapshotPackage>): StreamObserver<SnapshotPackage> {
      return object : EmptyStreamObserver<SnapshotPackage>() {
        override fun onCompleted() {
          sendResponse(responseObserver, SnapshotPackage.newBuilder().setSuccess(true).build())
        }
      }
    }

    override fun saveSnapshot(request: SnapshotPackage, responseObserver: StreamObserver<SnapshotPackage>) {
      executor.execute {
        createSnapshot(request.snapshotId)
        sendResponse(responseObserver, SnapshotPackage.newBuilder().setSuccess(true).build())
      }
    }
  }

  private inner class UiControllerService(
    private val executor: ExecutorService
  ) : UiControllerGrpc.UiControllerImplBase() {

    override fun showExtendedControls(request: PaneEntry, responseObserver: StreamObserver<ExtendedControlsStatus>) {
      executor.execute {
        val changed = !extendedControlsVisible
        extendedControlsVisible = true
        sendResponse(responseObserver, ExtendedControlsStatus.newBuilder().setVisibilityChanged(changed).build())
      }
    }

    override fun closeExtendedControls(empty: Empty, responseObserver: StreamObserver<ExtendedControlsStatus>) {
      executor.execute {
        val changed = extendedControlsVisible
        extendedControlsVisible = false
        sendResponse(responseObserver, ExtendedControlsStatus.newBuilder().setVisibilityChanged(changed).build())
      }
    }

    override fun setUiTheme(themingStyle: ThemingStyle, responseObserver: StreamObserver<Empty>) {
      executor.execute {
        sendEmptyResponse(responseObserver)
      }
    }
  }

  private inner class LoggingInterceptor : ServerInterceptor {

    override fun <ReqT, RespT> interceptCall(call: ServerCall<ReqT, RespT>,
                                             headers: Metadata,
                                             handler: ServerCallHandler<ReqT, RespT>): ServerCall.Listener<ReqT> {
      val callRecord = GrpcCallRecord(call.methodDescriptor.fullMethodName)

      val forwardingCall = object : SimpleForwardingServerCall<ReqT, RespT>(call) {
        override fun sendMessage(response: RespT) {
          grpcSemaphore.withPermit {
            callRecord.responseMessageCounter.add(Unit)
            super.sendMessage(response)
          }
        }
      }
      return object : SimpleForwardingServerCallListener<ReqT>(handler.startCall(forwardingCall, headers)) {
        override fun onMessage(request: ReqT) {
          grpcSemaphore.withPermit {
            callRecord.request = request as MessageOrBuilder
            grpcCallLog.add(callRecord)
            super.onMessage(request)
          }
        }

        override fun onComplete() {
          super.onComplete()
          callRecord.completion.set(Unit)
        }

        override fun onCancel() {
          super.onCancel()
          callRecord.completion.cancel(false)
        }
      }
    }
  }

  class GrpcCallRecord(val methodName: String) {
    lateinit var request: MessageOrBuilder

    /** One element is added to this queue for every response message sent to the client. */
    val responseMessageCounter = LinkedBlockingDeque<Unit>()

    /** Completed or cancelled when the gRPC call is completed or cancelled. */
    val completion: SettableFuture<Unit> = SettableFuture.create()

    fun waitForResponse(timeout: Long, unit: TimeUnit) {
      responseMessageCounter.poll(timeout, unit)
    }

    private fun waitForCompletion(timeout: Long, unit: TimeUnit) {
      completion.get(timeout, unit)
    }

    fun waitForCancellation(timeout: Long, unit: TimeUnit) {
      try {
        waitForCompletion(timeout, unit)
        fail("The $methodName call was not cancelled")
      }
      catch (_: CancellationException) {
        // Expected.
      }
    }

    override fun toString(): String {
      return "$methodName(${shortDebugString(request)})"
    }
  }

  class CallFilter(private vararg val methodNamesToIgnore: String) : Predicate<GrpcCallRecord> {
    override fun test(call: GrpcCallRecord): Boolean {
      return call.methodName !in methodNamesToIgnore
    }

    fun or(vararg moreMethodNamesToIgnore: String): CallFilter {
      return CallFilter(*arrayOf(*methodNamesToIgnore) + arrayOf(*moreMethodNamesToIgnore))
    }
  }

  companion object {
    /**
     * Creates a fake "Pixel 3 XL" AVD. The skin path in config.ini is absolute.
     */
    @JvmStatic
    fun createPhoneAvd(parentFolder: Path, sdkFolder: Path = parentFolder.resolve("Sdk"), api: Int = 29): Path {
      val avdId = "Pixel_3_XL_API_$api"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val skinFolder = getSkinFolder("pixel_3_xl")
      val systemImage = "system-images/android-$api/google_apis/x86_64/"
      val systemImageFolder = sdkFolder.resolve(systemImage)

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=false
          abi.type=x86
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=800M
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=virtualscene
          hw.camera.front=emulated
          hw.cpu.arch=x86
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name=Pixel 3 XL
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=Portrait
          hw.keyboard=yes
          hw.lcd.density=480
          hw.lcd.height=2960
          hw.lcd.width=1440
          hw.mainKeys=no
          hw.ramSize=1536
          hw.sdCard=yes
          hw.sensors.orientation=yes
          hw.sensors.proximity=yes
          hw.trackBall=no
          image.sysdir.1=$systemImage
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.path=${avdFolder}/sdcard.img
          sdcard.size=512 MB
          showDeviceFrame=yes
          skin.dynamic=yes
          skin.name=${skinFolder.fileName}
          skin.path=${skinFolder}
          tag.display=Google APIs
          tag.id=google_apis
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86
          hw.cpu.model = qemu32
          hw.cpu.ncore = 4
          hw.lcd.density=480
          hw.lcd.height=2960
          hw.lcd.width=1440
          hw.ramSize = 1536
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = false
          hw.accelerometer = false
          hw.gyroscope = true
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = false
          android.sdk.root = $sdkFolder
          """.trimIndent()

      val sourceProperties = """
          Pkg.Desc=System Image x86_64 with Google APIs.
          Pkg.Revision=1
          AndroidVersion.ApiLevel=$api
          SystemImage.Abi=x86_64
          SystemImage.TagId=google_apis
          SystemImage.TagDisplay=Google APIs
          SystemImage.GpuSupport=true
          Addon.VendorId=google
          Addon.VendorDisplay=Google Inc.
          """.trimIndent()

      createSystemImage(systemImageFolder, sourceProperties)
      return createAvd(avdFolder, configIni, hardwareIni)
    }

    /**
     * Creates a fake "Nexus 10" AVD. The skin path in config.ini is relative.
     */
    @JvmStatic
    fun createTabletAvd(parentFolder: Path, sdkFolder: Path = parentFolder.resolve("Sdk"), api: Int = 29): Path {
      val avdId = "Nexus_10_API_$api"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val skinName = "nexus_10"
      val skinFolder = getSkinFolder(skinName)
      copyDir(skinFolder, Files.createDirectories(sdkFolder.resolve("skins")).resolve(skinName))
      val systemImage = "system-images/android-$api/google_apis_playstore/x86_64/"
      val systemImageFolder = sdkFolder.resolve(systemImage)

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=false
          abi.type=x86
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=800M
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=virtualscene
          hw.camera.front=emulated
          hw.cpu.arch=x86
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name=Nexus 10
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=landscape
          hw.keyboard=yes
          hw.lcd.density=320
          hw.lcd.height=1600
          hw.lcd.width=2560
          hw.mainKeys=no
          hw.ramSize=1536
          hw.sdCard=yes
          hw.sensors.orientation=yes
          hw.sensors.proximity=no
          hw.trackBall=no
          image.sysdir.1=$systemImage
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.path=${avdFolder}/sdcard.img
          sdcard.size=512M
          showDeviceFrame=yes
          skin.dynamic=yes
          skin.name=${skinName}
          skin.path=skins/${skinName}
          tag.display=Google APIs
          tag.id=google_apis_playstore
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86
          hw.cpu.model = qemu32
          hw.cpu.ncore = 4
          hw.lcd.density=320
          hw.lcd.height=2560
          hw.lcd.width=1600
          hw.ramSize = 2048
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = true
          hw.accelerometer = false
          hw.gyroscope = true
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = false
          android.sdk.root = $sdkFolder
          """.trimIndent()


      val sourceProperties = """
          Pkg.Desc=System Image x86_64 with Google Play.
          Pkg.Revision=1
          AndroidVersion.ApiLevel=$api
          SystemImage.Abi=x86_64
          SystemImage.TagId=google_apis_playstore
          SystemImage.TagDisplay=Google Play
          SystemImage.GpuSupport=true
          Addon.VendorId=google
          Addon.VendorDisplay=Google Inc.
          """.trimIndent()

      createSystemImage(systemImageFolder, sourceProperties)
      return createAvd(avdFolder, configIni, hardwareIni)
    }

    /**
     * Creates a fake "7.6 Fold-in with outer display" AVD.
     */
    @JvmStatic
    fun createFoldableAvd(parentFolder: Path, sdkFolder: Path = parentFolder.resolve("Sdk"), api: Int = 31): Path {
      val avdId = "7.6_Fold-in_with_outer_display_API_$api"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val systemImage = "system-images/android-$api/google_apis/x86_64/"
      val systemImageFolder = sdkFolder.resolve(systemImage)

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=false
          abi.type=x86
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=800M
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=virtualscene
          hw.camera.front=emulated
          hw.cpu.arch=x86
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name = 7.6in Foldable
          hw.displayRegion.0.1.height = 2208
          hw.displayRegion.0.1.width = 884
          hw.displayRegion.0.1.xOffset = 0
          hw.displayRegion.0.1.yOffset = 0
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=Portrait
          hw.keyboard=yes
          hw.lcd.density = 480
          hw.lcd.height = 2208
          hw.lcd.width = 1768
          hw.mainKeys=no
          hw.ramSize=1536
          hw.sdCard=yes
          hw.sensor.hinge = yes
          hw.sensor.hinge.areas = 884-0-1-2208
          hw.sensor.hinge.count = 1
          hw.sensor.hinge.defaults = 180
          hw.sensor.hinge.ranges = 0-180
          hw.sensor.hinge.sub_type = 1
          hw.sensor.hinge.type = 1
          hw.sensor.hinge_angles_posture_definitions = 0-30, 30-150, 150-180
          hw.sensor.posture_list = 1,2,3
          hw.sensors.orientation=yes
          hw.sensors.proximity=yes
          hw.trackBall=no
          image.sysdir.1=$systemImage
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.path=${avdFolder}/sdcard.img
          sdcard.size=512 MB
          showDeviceFrame=no
          skin.dynamic=yes
          skin.name = 1768x2208
          skin.path = _no_skin
          tag.display=Google APIs
          tag.id=google_apis
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86
          hw.cpu.model = qemu32
          hw.cpu.ncore = 4
          hw.lcd.width = 1768
          hw.lcd.height = 2208
          hw.lcd.density = 480
          hw.displayRegion.0.1.xOffset = 0
          hw.displayRegion.0.1.yOffset = 0
          hw.displayRegion.0.1.width = 884
          hw.displayRegion.0.1.height = 2208
          hw.ramSize = 1536
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = false
          hw.accelerometer = false
          hw.gyroscope = true
          hw.sensor.hinge = true
          hw.sensor.hinge.count = 1
          hw.sensor.hinge.type = 1
          hw.sensor.hinge.sub_type = 1
          hw.sensor.hinge.ranges = 0-180
          hw.sensor.hinge.defaults = 180
          hw.sensor.hinge.areas = 884-0-1-2208
          hw.sensor.posture_list = 1,2,3
          hw.sensor.hinge_angles_posture_definitions = 0-30, 30-150, 150-180
          hw.sensor.hinge.fold_to_displayRegion.0.1_at_posture = 1
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = false
          android.sdk.root = $sdkFolder
          """.trimIndent()


      val sourceProperties = """
          Pkg.Desc=System Image x86_64 with Google APIs.
          Pkg.Revision=1
          AndroidVersion.ApiLevel=$api
          SystemImage.Abi=x86_64
          SystemImage.TagId=google_apis
          SystemImage.TagDisplay=Google APIs
          SystemImage.GpuSupport=true
          Addon.VendorId=google
          Addon.VendorDisplay=Google Inc.
          """.trimIndent()

      createSystemImage(systemImageFolder, sourceProperties)
      return createAvd(avdFolder, configIni, hardwareIni)
    }

    /**
     * Creates a fake 7.4 "Rollable" AVD.
     */
    @JvmStatic
    fun createRollableAvd(parentFolder: Path, sdkFolder: Path = parentFolder.resolve("Sdk"), api: Int = 31): Path {
      val avdId = "7.4_Rollable_API_$api"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val systemImage = "system-images/android-$api/google_apis/x86_64/"
      val systemImageFolder = sdkFolder.resolve(systemImage)

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=false
          abi.type=x86
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=800M
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=virtualscene
          hw.camera.front=emulated
          hw.cpu.arch=x86_64
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name = 7.4in Rollable
          hw.displayRegion.0.1.height = 2428
          hw.displayRegion.0.1.width = 1080
          hw.displayRegion.0.1.xOffset = 0
          hw.displayRegion.0.1.yOffset = 0
          hw.displayRegion.0.2.height = 2428
          hw.displayRegion.0.2.width = 1366
          hw.displayRegion.0.2.xOffset = 0
          hw.displayRegion.0.2.yOffset = 0
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=Portrait
          hw.keyboard=yes
          hw.lcd.density = 420
          hw.lcd.height = 2428
          hw.lcd.width = 1600
          hw.mainKeys=no
          hw.ramSize=1536
          hw.sdCard=yes
          hw.sensor.hinge.type = 3
          hw.sensor.posture_list = 1, 2, 3
          hw.sensor.roll = yes
          hw.sensor.roll.count = 1
          hw.sensor.roll.defaults = 67.5
          hw.sensor.roll.direction = 1
          hw.sensor.roll.radius = 3
          hw.sensor.roll.ranges = 58.55-100
          hw.sensor.roll.resize_to_displayRegion.0.1_at_posture = 1
          hw.sensor.roll.resize_to_displayRegion.0.2_at_posture = 2
          hw.sensor.roll_percentages_posture_definitions = 58.55-76.45, 76.45-94.35, 94.35-100
          hw.sensors.orientation=yes
          hw.sensors.proximity=yes
          hw.trackBall=no
          image.sysdir.1=$systemImage
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.path=${avdFolder}/sdcard.img
          sdcard.size=512 MB
          showDeviceFrame=no
          skin.dynamic=yes
          skin.name = 1600x2428
          skin.path = _no_skin
          tag.display=Google APIs
          tag.id=google_apis
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86_64
          hw.cpu.ncore = 4
          hw.lcd.width = 1600
          hw.lcd.height = 2428
          hw.lcd.density = 420
          hw.displayRegion.0.1.xOffset = 0
          hw.displayRegion.0.1.yOffset = 0
          hw.displayRegion.0.1.width = 1080
          hw.displayRegion.0.1.height = 2428
          hw.displayRegion.0.2.xOffset = 0
          hw.displayRegion.0.2.yOffset = 0
          hw.displayRegion.0.2.width = 1366
          hw.displayRegion.0.2.height = 2428
          hw.ramSize = 1536
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = false
          hw.accelerometer = false
          hw.gyroscope = true
          hw.sensor.hinge = true
          hw.sensor.hinge.count = 0
          hw.sensor.hinge.type = 3
          hw.sensor.hinge.sub_type = 0
          hw.sensor.posture_list = 1, 2, 3
          hw.sensor.hinge.fold_to_displayRegion.0.1_at_posture = 1
          hw.sensor.roll = true
          hw.sensor.roll.count = 1
          hw.sensor.roll.radius = 3
          hw.sensor.roll.ranges = 58.55-100
          hw.sensor.roll.direction = 1
          hw.sensor.roll.defaults = 67.5
          hw.sensor.roll_percentages_posture_definitions = 58.55-76.45, 76.45-94.35, 94.35-100
          hw.sensor.roll.resize_to_displayRegion.0.1_at_posture = 1
          hw.sensor.roll.resize_to_displayRegion.0.2_at_posture = 2
          hw.sensor.roll.resize_to_displayRegion.0.3_at_posture = 6
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = false
          android.sdk.root = $sdkFolder
          """.trimIndent()

      val sourceProperties = """
          Pkg.Desc=System Image x86_64 with Google APIs.
          Pkg.Revision=1
          AndroidVersion.ApiLevel=$api
          SystemImage.Abi=x86_64
          SystemImage.TagId=google_apis
          SystemImage.TagDisplay=Google APIs
          SystemImage.GpuSupport=true
          Addon.VendorId=google
          Addon.VendorDisplay=Google Inc.
          """.trimIndent()

      createSystemImage(systemImageFolder, sourceProperties)
      return createAvd(avdFolder, configIni, hardwareIni)
    }

    /**
     * Creates a fake "Resizable" AVD.
     */
    @JvmStatic
    fun createResizableAvd(parentFolder: Path, sdkFolder: Path = parentFolder.resolve("Sdk"), api: Int = 32): Path {
      val avdId = "Resizable_API_$api"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val systemImage = "system-images/android-$api/google_apis/x86_64/"
      val systemImageFolder = sdkFolder.resolve(systemImage)

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=false
          abi.type=x86_64
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=6442450944
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=virtualscene
          hw.camera.front=emulated
          hw.cpu.arch=x86_64
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name = resizable
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=Portrait
          hw.keyboard=yes
          hw.lcd.density = 420
          hw.lcd.height = 2340
          hw.lcd.width = 1080
          hw.mainKeys=no
          hw.ramSize=1536
          hw.sdCard=yes
          hw.sensors.orientation=yes
          hw.sensors.proximity=no
          hw.trackBall=no
          hw.resizable.configs = phone-0-1080-2340-420, foldable-1-1768-2208-420, tablet-2-1920-1200-240, desktop-3-1920-1080-160
          image.sysdir.1 = $systemImage
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.path=${avdFolder}/sdcard.img
          sdcard.size=512M
          showDeviceFrame=yes
          skin.dynamic=yes
          skin.name=1080x2340
          skin.path=_no_skin
          tag.display=Google APIs
          tag.id=google_apis
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86_64
          hw.cpu.ncore = 4
          hw.lcd.width = 1080
          hw.lcd.height = 2340
          hw.lcd.density = 420
          hw.ramSize = 1536
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = true
          hw.accelerometer = false
          hw.gyroscope = true
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = true
          hw.sdCard.path = ${avdFolder}/sdcard.img
          android.sdk.root = $sdkFolder
          hw.initialOrientation = Portrait
          hw.device.name = resizable
          """.trimIndent()

      val sourceProperties = """
          Pkg.Desc=System Image x86_64 with Google APIs.
          Pkg.Revision=1
          AndroidVersion.ApiLevel=$api
          SystemImage.Abi=x86_64
          SystemImage.TagId=google_apis
          SystemImage.TagDisplay=Google APIs
          SystemImage.GpuSupport=true
          Addon.VendorId=google
          Addon.VendorDisplay=Google Inc.
          """.trimIndent()

      createSystemImage(systemImageFolder, sourceProperties)
      return createAvd(avdFolder, configIni, hardwareIni)
    }

    /**
     * Creates a fake "Android Wear Round" AVD. The skin path in config.ini is absolute.
     */
    @JvmStatic
    fun createWatchAvd(parentFolder: Path, sdkFolder: Path = parentFolder.resolve("Sdk"), api: Int = 30): Path {
      val avdId = "Android_Wear_Round_API_$api"
      val avdFolder = parentFolder.resolve("${avdId}.avd")
      val avdName = avdId.replace('_', ' ')
      val skinFolder = getSkinFolder("wearos_small_round")
      val systemImage = "system-images/android-$api/android-wear/x86/"
      val systemImageFolder = sdkFolder.resolve(systemImage)

      val configIni = """
          AvdId=${avdId}
          PlayStore.enabled=true
          abi.type=x86
          avd.ini.displayname=${avdName}
          avd.ini.encoding=UTF-8
          disk.dataPartition.size=2G
          hw.accelerometer=yes
          hw.arc=false
          hw.audioInput=yes
          hw.battery=yes
          hw.camera.back=None
          hw.camera.front=None
          hw.cpu.arch=x86
          hw.cpu.ncore=4
          hw.dPad=no
          hw.device.name=wear_round
          hw.gps=yes
          hw.gpu.enabled=yes
          hw.gpu.mode=auto
          hw.initialOrientation=Portrait
          hw.keyboard=yes
          hw.keyboard.lid=yes
          hw.lcd.density=240
          hw.lcd.height=320
          hw.lcd.width=320
          hw.mainKeys=yes
          hw.ramSize=512
          hw.sdCard=yes
          hw.sensors.orientation=yes
          hw.sensors.proximity=yes
          hw.trackBall=no
          image.sysdir.1=$systemImage
          runtime.network.latency=none
          runtime.network.speed=full
          sdcard.size=512M
          showDeviceFrame=yes
          skin.dynamic=yes
          skin.name=${skinFolder.fileName}
          skin.path=${skinFolder}
          tag.display=Wear OS
          tag.id=android-wear
          """.trimIndent()

      val hardwareIni = """
          hw.cpu.arch = x86
          hw.cpu.model = qemu32
          hw.cpu.ncore = 4
          hw.lcd.density=240
          hw.lcd.height=320
          hw.lcd.width=320
          hw.ramSize = 1536
          hw.screen = multi-touch
          hw.dPad = false
          hw.rotaryInput = false
          hw.gsmModem = true
          hw.gps = true
          hw.battery = false
          hw.accelerometer = false
          hw.gyroscope = true
          hw.audioInput = true
          hw.audioOutput = true
          hw.sdCard = true
          hw.sdCard.path = $avdFolder/sdcard.img
          android.sdk.root = $sdkFolder
          """.trimIndent()

      val sourceProperties = """
          Pkg.Desc=Android SDK Platform
          Pkg.UserSrc=false
          Pkg.Revision=8
          AndroidVersion.ApiLevel=$api
          SystemImage.Abi=x86
          SystemImage.GpuSupport=true
          SystemImage.TagId=android-wear
          SystemImage.TagDisplay=Wear OS
          """.trimIndent()

      createSystemImage(systemImageFolder, sourceProperties)
      return createAvd(avdFolder, configIni, hardwareIni)
    }

    private fun createSystemImage(systemImageFolder: Path, sourceProperties: String) {
      systemImageFolder.createDirectories()
      Files.write(systemImageFolder.resolve("source.properties"), sourceProperties.toByteArray(UTF_8))
    }

    @JvmStatic
    private fun createAvd(avdFolder: Path, configIni: String, hardwareIni: String): Path {
      avdFolder.createDirectories()
      Files.write(avdFolder.resolve("config.ini"), configIni.toByteArray(UTF_8))
      Files.write(avdFolder.resolve("hardware-qemu.ini"), hardwareIni.toByteArray(UTF_8))
      return avdFolder
    }

    @JvmStatic
    private fun copyDir(from: Path, to: Path) {
      val options = arrayOf<CopyOption>(StandardCopyOption.COPY_ATTRIBUTES)
      Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
          if (dir !== from || !Files.exists(to)) {
            val copy = to.resolve(from.relativize(dir).toString())
            Files.createDirectory(copy)
          }
          return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          val copy = to.resolve(from.relativize(file).toString())
          Files.copy(file, copy, *options)
          return FileVisitResult.CONTINUE
        }
      })
    }

    @JvmStatic
    fun getSkinFolder(skinName: String): Path = getRootSkinFolder().resolve(skinName)

    @JvmStatic
    fun getRootSkinFolder(): Path = TestUtils.resolveWorkspacePathUnchecked(DEVICE_ART_RESOURCES_DIR)

    @JvmStatic
    fun grpcServerName(port: Int) = "FakeEmulator@${port}"

    @JvmStatic
    val defaultCallFilter = CallFilter("android.emulation.control.EmulatorController/getVmState",
                                       "android.emulation.control.EmulatorController/getDisplayConfigurations",
                                       "android.emulation.control.EmulatorController/streamNotification")
  }
}

private class ColorScheme(val start1: Color, val end1: Color, val start2: Color, val end2: Color)

private val COLOR_SCHEMES = listOf(ColorScheme(Color(236, 112, 99), Color(250, 219, 216), Color(212, 230, 241), Color(84, 153, 199)),
                                   ColorScheme(Color(154, 236, 99), Color(230, 250, 216), Color(238, 212, 241), Color(188, 84, 199)),
                                   ColorScheme(Color(99, 222, 236), Color(216, 247, 250), Color(241, 223, 212), Color(199, 130, 84)),
                                   ColorScheme(Color(181, 99, 236), Color(236, 216, 250), Color(215, 241, 212), Color(95, 199, 84)))

private const val DEVICE_ART_RESOURCES_DIR = "tools/adt/idea/artwork/resources/device-art-resources"
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
package com.android.tools.idea.device

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.device.DeviceClient.AgentTerminationListener
import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.DeviceMirroringSettings
import com.android.tools.idea.emulator.DeviceMirroringSettingsListener
import com.android.tools.idea.emulator.PRIMARY_DISPLAY_ID
import com.android.tools.idea.emulator.isSameAspectRatio
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.android.tools.idea.emulator.scaled
import com.android.tools.idea.emulator.scaledUnbiased
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.geom.AffineTransform
import java.util.concurrent.CancellationException
import kotlin.math.min

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param disposableParent the disposable parent determining the lifespan of the view
 * @param deviceSerialNumber the serial number of the device
 * @param deviceAbi the application binary interface of the device
 * @param project the project associated with the view
 */
class DeviceView(
  disposableParent: Disposable,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  private val initialDisplayOrientation: Int,
  private val project: Project,
) : AbstractDisplayView(PRIMARY_DISPLAY_ID), Disposable, DeviceMirroringSettingsListener {

  val isConnected: Boolean
    get() = connectionState == ConnectionState.CONNECTED
  /** The difference between [displayOrientationQuadrants] and the orientation according to the DisplayInfo Android data structure. */
  override var displayOrientationQuadrants: Int = 0
    private set
  internal var displayOrientationCorrectionQuadrants: Int = 0
    private set

  private var connectionState = ConnectionState.INITIAL
    set(value) {
      if (field != value) {
        field = value
        for (listener in connectionStateListeners) {
          listener.connectionStateChanged(deviceSerialNumber, connectionState)
        }
      }
    }
  private var deviceClient: DeviceClient? = null
  internal val deviceController: DeviceController?
    get() = deviceClient?.deviceController
  private val videoDecoder: VideoDecoder?
    get() = deviceClient?.videoDecoder
  private var clipboardSynchronizer: DeviceClipboardSynchronizer? = null
  private val connectionStateListeners = mutableListOf<ConnectionStateListener>()

  override val deviceDisplaySize = Dimension()

  private val displayTransform = AffineTransform()
  private var disposed = false

  private var multiTouchMode = false
    set(value) {
      if (value != field) {
        field = value
        repaint()
        val point = lastTouchCoordinates
        if (point != null) {
          val action = if (value) MotionEventMessage.ACTION_POINTER_DOWN else MotionEventMessage.ACTION_POINTER_UP
          sendMotionEvent(point.x, point.y, action)
        }
      }
    }

  /**
   * Last coordinates of the mouse pointer while the first button was pressed.
   * Set to null when the first mouse button is released.
   */
  private var lastTouchCoordinates: Point? = null

  init {
    Disposer.register(disposableParent, this)

    addComponentListener(object : ComponentAdapter() {
      override fun componentShown(event: ComponentEvent) {
        if (realWidth > 0 && realHeight > 0 && connectionState == ConnectionState.INITIAL) {
          initializeAgentAsync(initialDisplayOrientation)
        }
      }
    })

    // Forward mouse & keyboard events.
    val mouseListener = MyMouseListener()
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)

    addKeyListener(MyKeyListener())

    project.messageBus.connect(this).subscribe(DeviceMirroringSettingsListener.TOPIC, this)
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val resized = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (resized && realWidth > 0 && realHeight > 0) {
      if (connectionState == ConnectionState.INITIAL) {
        initializeAgentAsync(initialDisplayOrientation)
      }
      else {
        updateVideoSize()
      }
    }
  }

  /** Starts asynchronous initialization of the Screen Sharing Agent. */
  private fun initializeAgentAsync(initialDisplayOrientation: Int) {
    connectionState = ConnectionState.CONNECTING
    val maxOutputSize = realSize
    AndroidCoroutineScope(this@DeviceView).launch { initializeAgent(maxOutputSize, initialDisplayOrientation) }
  }

  private suspend fun initializeAgent(maxOutputSize: Dimension, initialDisplayOrientation: Int) {
    try {
      val deviceClient = DeviceClient(this, deviceSerialNumber, deviceAbi, project)
      deviceClient.startAgentAndConnect(maxOutputSize, initialDisplayOrientation, MyFrameListener(), object : AgentTerminationListener {
        override fun agentTerminated(exitCode: Int) {
          disconnected(initialDisplayOrientation)
        }
      })
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (!disposed) {
          this.deviceClient = deviceClient
          if (DeviceMirroringSettings.getInstance().synchronizeClipboard) {
            clipboardSynchronizer = DeviceClipboardSynchronizer(deviceClient.deviceController)
          }
          repaint()
          updateVideoSize() // Update video size in case the view was resized during agent initialization.
        }
      }
    }
    catch (_: CancellationException) {
      // The view has been closed.
    }
    catch (e: Throwable) {
      disconnected(initialDisplayOrientation, e)
    }
  }

  private fun updateVideoSize() {
    val deviceClient = deviceClient ?: return
    val videoDecoder = deviceClient.videoDecoder
    if (videoDecoder.maxOutputSize != realSize) {
      videoDecoder.maxOutputSize = realSize
      deviceClient.deviceController.sendControlMessage(SetMaxVideoResolutionMessage(realWidth, realHeight))
    }
  }

  private fun disconnected(initialDisplayOrientation: Int, exception: Throwable? = null) {
    UIUtil.invokeLaterIfNeeded {
      if (disposed) {
        return@invokeLaterIfNeeded
      }
      val message: String
      val reconnector: Reconnector
      when (connectionState) {
        ConnectionState.CONNECTING -> {
          thisLogger().error("Failed to initialize the screen sharing agent", exception)
          message = "Failed to initialize the device agent. See the error log."
          reconnector = Reconnector("Retry", "Connecting to the device") { initializeAgentAsync(initialDisplayOrientation) }
        }

        ConnectionState.CONNECTED -> {
          message = "Lost connection to the device. See the error log."
          reconnector = Reconnector("Reconnect", "Attempting to reconnect") { initializeAgentAsync(UNKNOWN_ORIENTATION) }
        }

        else -> return@invokeLaterIfNeeded
      }

      deviceClient?.let { Disposer.dispose(it) }
      deviceClient = null
      connectionState = ConnectionState.DISCONNECTED
      showDisconnectedStateMessage(message, reconnector)
    }
  }

  override fun dispose() {
    disposed = true
  }

  override fun canZoom(): Boolean =
    connectionState == ConnectionState.CONNECTED

  override fun computeActualSize(): Dimension =
    computeActualSize(displayOrientationQuadrants)

  private fun computeActualSize(rotationQuadrants: Int): Dimension =
    deviceDisplaySize.rotatedByQuadrants(rotationQuadrants)

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    if (width == 0 || height == 0) {
      return
    }

    val decoder = videoDecoder ?: return
    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw device display.
    decoder.consumeDisplayFrame { displayFrame ->
      val image = displayFrame.image
      val rect = displayRectangle
      if (rect != null && !isSameAspectRatio(image.width, image.height, rect.width, rect.height, 0.01)) {
        zoom(ZoomType.FIT) // Dimensions of the display image changed - reset zoom level.
      }
      val rotatedDisplaySize = displayFrame.displaySize.rotatedByQuadrants(displayFrame.orientation)
      val scale = roundScale(min(realWidth.toDouble() / rotatedDisplaySize.width, realHeight.toDouble() / rotatedDisplaySize.height))
      val w = rotatedDisplaySize.width.scaled(scale).coerceAtMost(realWidth)
      val h = rotatedDisplaySize.height.scaled(scale).coerceAtMost(realHeight)
      val displayRect = Rectangle((realWidth - w) / 2, (realHeight - h) / 2, w, h)
      displayRectangle = displayRect
      if (displayRect.width == image.width && displayRect.height == image.height) {
        g.drawImage(image, null, displayRect.x, displayRect.y)
      }
      else {
        displayTransform.setToTranslation(displayRect.x.toDouble(), displayRect.y.toDouble())
        displayTransform.scale(displayRect.width.toDouble() / image.width, displayRect.height.toDouble() / image.height)
        g.drawImage(image, displayTransform, null)
      }

      deviceDisplaySize.size = displayFrame.displaySize
      displayOrientationQuadrants = displayFrame.orientation
      displayOrientationCorrectionQuadrants = displayFrame.orientationCorrection
      frameNumber = displayFrame.frameNumber
      notifyFrameListeners(displayRect, displayFrame.image)

      deviceClient?.apply {
        if (startTime != 0L) {
          val delay = System.currentTimeMillis() - startTime
          val pushDelay = pushEndTime - startTime
          val agentStartDelay = startAgentTime - startTime
          val connectionDelay = videoChannelConnectedTime - startTime
          val firstPacketDelay = firstPacketArrival - startTime
          println("Initialization took $delay ms, push took $pushDelay ms, agent was started after $agentStartDelay ms," +
                  " connected after $connectionDelay ms, first video packet arrived after $firstPacketDelay ms")
          startTime = 0L
        }
      }
    }

    if (multiTouchMode) {
      val displayRect = displayRectangle
      if (displayRect != null) {
        drawMultiTouchFeedback(g, displayRect, lastTouchCoordinates != null)
      }
    }
  }

  @UiThread
  override fun settingsChanged(settings: DeviceMirroringSettings) {
    val controller = deviceClient?.deviceController ?: return
    if (settings.synchronizeClipboard) {
      val synchronizer = clipboardSynchronizer
      if (synchronizer == null) {
        // Start clipboard synchronization.
        clipboardSynchronizer = DeviceClipboardSynchronizer(controller)
      }
      else {
        // Pass the new value of maxSyncedClipboardLength to the device.
        synchronizer.setDeviceClipboard()
      }
    }
    else {
      clipboardSynchronizer?.let {
        // Stop clipboard synchronization.
        Disposer.dispose(it)
        clipboardSynchronizer = null
      }
    }
  }

  override fun dispatchTouch(p: Point) {
    sendMotionEventDisplayCoordinates(p.x, p.y, MotionEventMessage.ACTION_DOWN)
  }

  override fun dispatchKey(keyCode: Int) {
    deviceController?.sendControlMessage(KeyEventMessage(ACTION_DOWN_AND_UP, keyCode, metaState = 0))
  }

  private fun sendMotionEvent(x: Int, y: Int, action: Int) {
    val displayRectangle = displayRectangle ?: return
    // Mouse pointer coordinates compensated for the device display rotation.
    val normalizedX: Int
    val normalizedY: Int
    val imageWidth: Int
    val imageHeight: Int
    when (displayOrientationQuadrants) {
      0 -> {
        normalizedX = x.scaled(screenScale) - displayRectangle.x
        normalizedY = y.scaled(screenScale) - displayRectangle.y
        imageWidth = displayRectangle.width
        imageHeight = displayRectangle.height
      }
      1 -> {
        normalizedX = displayRectangle.y + displayRectangle.height - y.scaled(screenScale)
        normalizedY = x.scaled(screenScale) - displayRectangle.x
        imageWidth = displayRectangle.height
        imageHeight = displayRectangle.width
      }
      2 -> {
        normalizedX = displayRectangle.x + displayRectangle.width - x.scaled(screenScale)
        normalizedY = displayRectangle.y + displayRectangle.height - y.scaled(screenScale)
        imageWidth = displayRectangle.width
        imageHeight = displayRectangle.height
      }
      3 -> {
        normalizedX = y.scaled(screenScale) - displayRectangle.y
        normalizedY = displayRectangle.x + displayRectangle.width - x.scaled(screenScale)
        imageWidth = displayRectangle.height
        imageHeight = displayRectangle.width
      }
      else -> {
        assert(false) { "Invalid display orientation: $displayOrientationQuadrants" }
        return
      }
    }
    // Device display coordinates.
    val displayX = normalizedX.scaledUnbiased(imageWidth, deviceDisplaySize.width)
    val displayY = normalizedY.scaledUnbiased(imageHeight, deviceDisplaySize.height)

    if (displayX in 0 until deviceDisplaySize.width && displayY in 0 until deviceDisplaySize.height) {
      // Within the bounds of the device display.
      sendMotionEventDisplayCoordinates(displayX, displayY, action)
    }
    else if (action == MotionEventMessage.ACTION_MOVE) {
      // Crossed the device display boundary while dragging.
      lastTouchCoordinates = null
      val adjustedX = displayX.coerceIn(0, deviceDisplaySize.width - 1)
      val adjustedY = displayY.coerceIn(0, deviceDisplaySize.height - 1)
      sendMotionEventDisplayCoordinates(adjustedX, adjustedY, action)
      sendMotionEventDisplayCoordinates(adjustedX, adjustedY, MotionEventMessage.ACTION_UP)
    }
  }

  private fun sendMotionEventDisplayCoordinates(displayX: Int, displayY: Int, action: Int) {
    val deviceController = deviceController ?: return
    val message = when {
      action == MotionEventMessage.ACTION_POINTER_DOWN || action == MotionEventMessage.ACTION_POINTER_UP ->
          MotionEventMessage(originalAndMirroredPointer(displayX, displayY),
                             action or (1 shl MotionEventMessage.ACTION_POINTER_INDEX_SHIFT), displayId)
      multiTouchMode -> MotionEventMessage(originalAndMirroredPointer(displayX, displayY), action, displayId)
      else -> MotionEventMessage(originalPointer(displayX, displayY), action, displayId)
    }

    deviceController.sendControlMessage(message)
  }

  private fun originalPointer(displayX: Int, displayY: Int): List<MotionEventMessage.Pointer> {
    return listOf(MotionEventMessage.Pointer(displayX, displayY, 0))
  }

  private fun originalAndMirroredPointer(displayX: Int, displayY: Int): List<MotionEventMessage.Pointer> {
    return listOf(MotionEventMessage.Pointer(displayX, displayY, 0),
                  MotionEventMessage.Pointer(deviceDisplaySize.width - displayX, deviceDisplaySize.height - displayY, 1))
  }

  private fun isInsideDisplay(event: MouseEvent) =
    displayRectangle?.contains(event.x * screenScale, event.y * screenScale) ?: false

  /** Adds a [listener] to receive callbacks when the state of the agent's connection changes. */
  @UiThread
  fun addConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.add(listener)
  }

  /** Removes a connection state listener. */
  @UiThread
  fun removeConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.remove(listener)
  }

  enum class ConnectionState { INITIAL, CONNECTING, CONNECTED, DISCONNECTED }

  /**
   * Listener of connection state changes.
   */
  interface ConnectionStateListener {
    /**
     * Called when the state of the device agent's connection changes.
     */
    @UiThread
    fun connectionStateChanged(deviceSerialNumber: String, connectionState: ConnectionState)
  }

  private inner class MyFrameListener : VideoDecoder.FrameListener {

    override fun onNewFrameAvailable() {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        if (connectionState == ConnectionState.CONNECTING) {
          hideDisconnectedStateMessage()
          connectionState = ConnectionState.CONNECTED
        }
        if (width != 0 && height != 0 && deviceClient != null) {
          repaint()
        }
      }
    }

    override fun onEndOfVideoStream() {
      disconnected(initialDisplayOrientation)
    }
  }

  private inner class MyKeyListener  : KeyAdapter() {

    override fun keyTyped(event: KeyEvent) {
      val c = event.keyChar
      if (c == CHAR_UNDEFINED || Character.isISOControl(c)) {
        return
      }
      val message = TextInputMessage(c.toString())
      deviceController?.sendControlMessage(message)
    }

    override fun keyPressed(event: KeyEvent) {
      if (event.keyCode == VK_CONTROL && event.modifiersEx == CTRL_DOWN_MASK) {
        multiTouchMode = true
        return
      }

      // The Tab character is passed to the device, but Shift+Tab is converted to Tab and processed locally.
      if (event.keyCode == VK_TAB && event.modifiersEx == SHIFT_DOWN_MASK) {
        val tabEvent = KeyEvent(event.source as Component, event.id, event.getWhen(), 0, event.keyCode, event.keyChar, event.keyLocation)
        traverseFocusLocally(tabEvent)
        return
      }

      if (event.modifiersEx != 0) {
        return
      }
      val deviceController = deviceController ?: return
      val keyCode = hostKeyCodeToDeviceKeyCode(event.keyCode)
      if (keyCode == AKEYCODE_UNKNOWN) {
        return
      }
      deviceController.sendControlMessage(KeyEventMessage(ACTION_DOWN_AND_UP, keyCode, 0))
    }

    override fun keyReleased(event: KeyEvent) {
      if (event.keyCode == VK_CONTROL) {
        multiTouchMode = false
      }
    }

    private fun hostKeyCodeToDeviceKeyCode(hostKeyCode: Int): Int {
      return when (hostKeyCode) {
        VK_BACK_SPACE -> AKEYCODE_DEL
        VK_DELETE -> if (SystemInfo.isMac) AKEYCODE_DEL else AKEYCODE_FORWARD_DEL
        VK_ENTER -> AKEYCODE_ENTER
        VK_ESCAPE -> AKEYCODE_ESCAPE
        VK_TAB -> AKEYCODE_TAB
        VK_LEFT, VK_KP_LEFT -> AKEYCODE_DPAD_LEFT
        VK_RIGHT, VK_KP_RIGHT -> AKEYCODE_DPAD_RIGHT
        VK_UP, VK_KP_UP -> AKEYCODE_DPAD_UP
        VK_DOWN, VK_KP_DOWN -> AKEYCODE_DPAD_DOWN
        VK_HOME -> AKEYCODE_MOVE_HOME
        VK_END -> AKEYCODE_MOVE_END
        VK_PAGE_UP -> AKEYCODE_PAGE_UP
        VK_PAGE_DOWN -> AKEYCODE_PAGE_DOWN
        else -> AKEYCODE_UNKNOWN
      }
    }
  }

  private inner class MyMouseListener : MouseAdapter() {

    override fun mousePressed(event: MouseEvent) {
      requestFocusInWindow()
      if (isInsideDisplay(event) && event.button == BUTTON1) {
        lastTouchCoordinates = Point(event.x, event.y)
        updateMultiTouchMode(event)
        sendMotionEvent(event.x, event.y, MotionEventMessage.ACTION_DOWN)
      }
    }

    override fun mouseReleased(event: MouseEvent) {
      if (event.button == BUTTON1) {
        lastTouchCoordinates = null
        updateMultiTouchMode(event)
        sendMotionEvent(event.x, event.y, MotionEventMessage.ACTION_UP)
      }
    }

    override fun mouseEntered(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    override fun mouseExited(event: MouseEvent) {
      if ((event.modifiersEx and BUTTON1_DOWN_MASK) != 0 && lastTouchCoordinates != null) {
        // Moving over the edge of the display view will terminate the ongoing dragging.
        sendMotionEvent(event.x, event.y, MotionEventMessage.ACTION_MOVE)
      }
      lastTouchCoordinates = null
      multiTouchMode = false
    }

    override fun mouseDragged(event: MouseEvent) {
      updateMultiTouchMode(event)
      if ((event.modifiersEx and BUTTON1_DOWN_MASK) != 0 && lastTouchCoordinates != null) {
        sendMotionEvent(event.x, event.y, MotionEventMessage.ACTION_MOVE)
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    private fun updateMultiTouchMode(event: MouseEvent) {
      val oldMultiTouchMode = multiTouchMode
      multiTouchMode = isInsideDisplay(event) && (event.modifiersEx and CTRL_DOWN_MASK) != 0
      if (multiTouchMode && oldMultiTouchMode) {
        repaint() // If multi-touch mode changed above, the repaint method was already called.
      }
    }
  }
}

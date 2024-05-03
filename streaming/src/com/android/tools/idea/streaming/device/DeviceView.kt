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
package com.android.tools.idea.streaming.device

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.ImageUtils.scale
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.DeviceMirroringSettingsListener
import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.core.constrainInside
import com.android.tools.idea.streaming.core.contains
import com.android.tools.idea.streaming.core.createShowLogHyperlinkListener
import com.android.tools.idea.streaming.core.getShowLogHyperlink
import com.android.tools.idea.streaming.core.location
import com.android.tools.idea.streaming.core.rotatedByQuadrants
import com.android.tools.idea.streaming.core.scaled
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.DeviceClient.AgentTerminationListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions.ACTION_COPY
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CUT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_DELETE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ESCAPE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_UP
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_LINE_END
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_LINE_START
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_NEXT_WORD
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_PREVIOUS_WORD
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TAB
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_END
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_START
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_PASTE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_REDO
import com.intellij.openapi.actionSystem.IdeActions.ACTION_SELECT_ALL
import com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.InputEvent.ALT_DOWN_MASK
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.awt.event.InputEvent.BUTTON2_DOWN_MASK
import java.awt.event.InputEvent.BUTTON3_DOWN_MASK
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.META_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import javax.swing.KeyStroke
import kotlin.math.absoluteValue
import kotlin.math.min

/**
 * A view of a mirrored device display.
 *
 * @param disposableParent the disposable parent determining the lifespan of the view
 * @param deviceClient the client for communicating with the device agent
 * @param initialDisplayOrientation initial orientation of the device display in quadrants counterclockwise
 * @param project the project associated with the view
 */
internal class DeviceView(
  disposableParent: Disposable,
  val deviceClient: DeviceClient,
  displayId: Int,
  private val initialDisplayOrientation: Int,
  private val project: Project,
) : AbstractDisplayView(displayId), DeviceMirroringSettingsListener {

  val isConnected: Boolean
    get() = connectionState == ConnectionState.CONNECTED

  override val deviceId: DeviceId = DeviceId.ofPhysicalDevice(deviceClient.deviceSerialNumber)

  /**
   * Orientation of the device display according to Android's
   * [Display.getRotation](https://developer.android.com/reference/android/view/Display#getRotation()).
   */
  override var displayOrientationQuadrants: Int = 0
    private set
  override val apiLevel: Int
    get() = deviceClient.deviceConfig.apiLevel

  private var connectionState = ConnectionState.INITIAL
    set(value) {
      if (field != value) {
        field = value
        UIUtil.invokeLaterIfNeeded {
          if (!disposed) {
            for (listener in connectionStateListeners) {
              listener.connectionStateChanged(deviceSerialNumber, connectionState)
            }
          }
        }
      }
    }

  internal val deviceController: DeviceController?
    get() = deviceClient.deviceController

  override val deviceDisplaySize = Dimension()

  private var clipboardSynchronizer: DeviceClipboardSynchronizer? = null
  private val connectionStateListeners = mutableListOf<ConnectionStateListener>()
  private val agentTerminationListener = object: AgentTerminationListener {
    override fun agentTerminated(exitCode: Int) { disconnected(initialDisplayOrientation, AgentTerminatedException(exitCode)) }
    override fun deviceDisconnected() { disconnected(initialDisplayOrientation) }
  }
  private val frameListener = MyFrameListener()
  private val displayTransform = AffineTransform()
  private var disposed = false
  private var maxVideoSize = Dimension()

  private var multiTouchMode = false
    set(value) {
      if (value != field) {
        field = value
        repaint()
        val point = lastTouchCoordinates
        if (point != null) {
          val action = if (value) MotionEventMessage.ACTION_POINTER_DOWN else MotionEventMessage.ACTION_POINTER_UP
          sendMotionEvent(point, action, 0)
        }
      }
    }
  /** Last coordinates of the mouse pointer while the first button is pressed, null when the first button is released. */
  private var lastTouchCoordinates: Point? = null
  /** Whether the last observed mouse event was in display. */
  private var wasInsideDisplay = false
  private var mouseHovering = false // Last mouse event was move without pressed buttons.
  private val repaintAlarm: Alarm = Alarm(this)
  private var highQualityRenderingRequested = false

  init {
    Disposer.register(disposableParent, this)

    addComponentListener(object : ComponentAdapter() {
      override fun componentShown(event: ComponentEvent) {
        if (physicalWidth > 0 && physicalHeight > 0 && connectionState == ConnectionState.INITIAL) {
          connectToAgentAsync(initialDisplayOrientation)
        }
      }
    })

    // Forward mouse & keyboard events.
    val mouseListener = MyMouseListener()
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)
    addMouseWheelListener(mouseListener)

    addKeyListener(MyKeyListener())

    project.messageBus.connect(this).subscribe(DeviceMirroringSettingsListener.TOPIC, this)
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val resized = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (resized && physicalWidth > 0 && physicalHeight > 0) {
      if (connectionState == ConnectionState.INITIAL) {
        connectToAgentAsync(initialDisplayOrientation)
      }
      else {
        updateVideoSize()
      }
    }
  }

  /** Starts asynchronous initialization of the Screen Sharing Agent. */
  private fun connectToAgentAsync(initialDisplayOrientation: Int) {
    frameNumber = 0u
    connectionState = ConnectionState.CONNECTING
    maxVideoSize = physicalSize
    AndroidCoroutineScope(this@DeviceView).launch {
      connectToAgent(maxVideoSize, initialDisplayOrientation)
    }
  }

  private suspend fun connectToAgent(maxOutputSize: Dimension, initialDisplayOrientation: Int) {
    try {
      deviceClient.addAgentTerminationListener(agentTerminationListener)
      if (displayId == PRIMARY_DISPLAY_ID) {
        deviceClient.establishAgentConnection(maxOutputSize, initialDisplayOrientation, startVideoStream = true, project)
      }
      else {
        deviceClient.waitUntilConnected()
        val videoDecoder = deviceClient.videoDecoder
        if (videoDecoder == null) {
          disconnected(initialDisplayOrientation, null)
          return
        }
        deviceClient.startVideoStream(project, displayId, maxOutputSize)
      }

      deviceClient.videoDecoder?.addFrameListener(displayId, frameListener)

      UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
        if (!disposed) {
          connected()
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
    val maxSize = physicalSize
    if (maxVideoSize != maxSize) {
      maxVideoSize = maxSize
      deviceClient.setMaxVideoResolution(project, displayId, maxSize)
    }
  }

  private fun connected() {
    if (connectionState == ConnectionState.CONNECTING) {
      hideLongRunningOperationIndicatorInstantly()
      hideDisconnectedStateMessage()
      connectionState = ConnectionState.CONNECTED
      if (displayId == PRIMARY_DISPLAY_ID) {
        if (DeviceMirroringSettings.getInstance().synchronizeClipboard) {
          startClipboardSynchronization()
        }
      }
    }
  }

  private fun disconnected(initialDisplayOrientation: Int, exception: Throwable? = null) {
    deviceClient.removeAgentTerminationListener(agentTerminationListener)
    if (displayId != PRIMARY_DISPLAY_ID) {
      return
    }
    deviceClient.streamingSessionTracker.streamingEnded()
    UIUtil.invokeLaterIfNeeded {
      if (disposed || connectionState == ConnectionState.DISCONNECTED) {
        return@invokeLaterIfNeeded
      }
      hideLongRunningOperationIndicatorInstantly()
      stopClipboardSynchronization()
      val message: String
      val reconnector: Reconnector
      when (frameNumber) {
        0u -> {
          thisLogger().error("Failed to initialize the screen sharing agent", exception)
          message = getConnectionErrorMessage(exception)
          reconnector = Reconnector("Retry", "Connecting to the device") { connectToAgentAsync(initialDisplayOrientation) }
        }

        else -> {
          message = getDisconnectionErrorMessage(exception)
          reconnector = Reconnector("Reconnect", "Attempting to reconnect") { connectToAgentAsync(UNKNOWN_ORIENTATION) }
        }
      }

      connectionState = ConnectionState.DISCONNECTED
      showDisconnectedStateMessage(message, createShowLogHyperlinkListener(), reconnector)
    }
  }

  private fun getConnectionErrorMessage(exception: Throwable?): String {
    return when ((exception as? AgentTerminatedException)?.exitCode) {
      AGENT_WEAK_VIDEO_ENCODER ->
          "The device may not have sufficient computing power for encoding display contents. See ${getShowLogHyperlink()} for details."
      AGENT_REPEATED_VIDEO_ENCODER_ERRORS ->
          "Repeated video encoder errors during initialization of the device agent. See ${getShowLogHyperlink()} for details."
      else ->
          (exception as? TimeoutException)?.message ?: "Failed to initialize the device agent. See ${getShowLogHyperlink()} for details."
    }
  }

  private fun getDisconnectionErrorMessage(exception: Throwable?): String {
    return when ((exception as? AgentTerminatedException)?.exitCode) {
      AGENT_WEAK_VIDEO_ENCODER ->
          "Repeated video encoder errors. The device may not have sufficient computing power for encoding display contents." +
          " See ${getShowLogHyperlink()} for details."
      AGENT_REPEATED_VIDEO_ENCODER_ERRORS -> "Repeated video encoder errors. See ${getShowLogHyperlink()} for details."
      else -> "Lost connection to the device. See ${getShowLogHyperlink()} for details."
    }
  }

  override fun dispose() {
    deviceClient.videoDecoder?.removeFrameListener(displayId, frameListener)
    deviceClient.stopVideoStream(project, displayId)
    deviceClient.removeAgentTerminationListener(agentTerminationListener)
    disposed = true
  }

  override fun canZoom(): Boolean =
    connectionState == ConnectionState.CONNECTED

  override fun computeActualSize(): Dimension =
    computeActualSize(displayOrientationQuadrants)

  private fun computeActualSize(rotationQuadrants: Int): Dimension =
    deviceDisplaySize.rotatedByQuadrants(rotationQuadrants)

  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)

    if (width == 0 || height == 0) {
      return
    }

    val decoder = deviceClient.videoDecoder ?: return
    val g = graphics.create() as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw device display.
    decoder.consumeDisplayFrame(displayId) { displayFrame ->
      if (frameNumber == 0u) {
        hideLongRunningOperationIndicatorInstantly()
      }
      repaintAlarm.cancelAllRequests()
      if (displayOrientationQuadrants != displayFrame.orientation ||
          deviceDisplaySize.width != 0 && deviceDisplaySize.width != displayFrame.displaySize.width ||
          deviceDisplaySize.height != 0 && deviceDisplaySize.height != displayFrame.displaySize.height) {
        zoom(ZoomType.FIT) // Orientation or dimensions of the display have changed - reset zoom level.
      }
      val rotatedDisplaySize = displayFrame.displaySize.rotatedByQuadrants(displayFrame.orientation)
      val maxSize = computeMaxImageSize()
      val scale = roundScale(min(maxSize.width.toDouble() / rotatedDisplaySize.width,
                                 maxSize.height.toDouble() / rotatedDisplaySize.height))
      val w = rotatedDisplaySize.width.scaled(scale).coerceAtMost(physicalWidth)
      val h = rotatedDisplaySize.height.scaled(scale).coerceAtMost(physicalHeight)
      val displayRect = Rectangle((physicalWidth - w) / 2, (physicalHeight - h) / 2, w, h)
      displayRectangle = displayRect
      val image = displayFrame.image
      if (displayRect.width == image.width && displayRect.height == image.height) {
        g.drawImage(image, null, displayRect.x, displayRect.y)
      }
      else {
        val xScale = displayRect.width.toDouble() / image.width
        val yScale = displayRect.height.toDouble() / image.height
        if (highQualityRenderingRequested && (xScale < 0.5 || yScale < 0.5)) {
          g.drawImage(scale(image, xScale, yScale), null, displayRect.x, displayRect.y)
        }
        else {
          displayTransform.setToTranslation(displayRect.x.toDouble(), displayRect.y.toDouble())
          displayTransform.scale(xScale, yScale)
          g.drawImage(image, displayTransform, null)
          repaintAlarm.addRequest(::requestHighQualityRepaint, 500)
        }
      }
      highQualityRenderingRequested = false

      deviceDisplaySize.size = displayFrame.displaySize
      displayOrientationQuadrants = displayFrame.orientation
      displayOrientationCorrectionQuadrants = displayFrame.orientationCorrection
      frameNumber = displayFrame.frameNumber
      notifyFrameListeners(displayRect, displayFrame.image)

      if (multiTouchMode) {
        // Render multi-touch visual feedback.
        drawMultiTouchFeedback(g, displayRect, lastTouchCoordinates != null)
      }
    }
  }

  private fun requestHighQualityRepaint() {
    highQualityRenderingRequested = true
    repaint()
  }

  @UiThread
  override fun settingsChanged(settings: DeviceMirroringSettings) {
    if (disposed || deviceController == null) {
      return
    }
    if (settings.synchronizeClipboard) {
      startClipboardSynchronization()
    }
    else {
      stopClipboardSynchronization()
    }
  }

  override fun hardwareInputStateChanged(event: AnActionEvent, enabled: Boolean) {
    super.hardwareInputStateChanged(event, enabled)
    updateMultiTouchMode(event.inputEvent!!)
  }

  private fun startClipboardSynchronization() {
    val synchronizer = clipboardSynchronizer
    if (synchronizer == null) {
      // Start clipboard synchronization.
      clipboardSynchronizer = DeviceClipboardSynchronizer(this, deviceClient)
    }
    else {
      // Pass the new value of maxSyncedClipboardLength to the device.
      synchronizer.setDeviceClipboard(forceSend = true)
    }
  }

  private fun stopClipboardSynchronization() {
    clipboardSynchronizer?.let {
      // Stop clipboard synchronization.
      Disposer.dispose(it)
      clipboardSynchronizer = null
    }
  }

  private fun sendMotionEvent(p: Point, action: Int, modifiers: Int, button: Int = 0, axisValues: Int2FloatOpenHashMap? = null) {
    val displayCoordinates = toDeviceDisplayCoordinates(p) ?: return

    if (displayCoordinates in deviceDisplaySize) {
      // Within the bounds of the device display.
      sendMotionEventDisplayCoordinates(displayCoordinates, action, modifiers, button, axisValues)
    }
    else if (action == MotionEventMessage.ACTION_MOVE) {
      // Crossed the device display boundary while dragging.
      lastTouchCoordinates = null
      val adjusted = displayCoordinates.constrainInside(deviceDisplaySize)
      sendMotionEventDisplayCoordinates(adjusted, action, modifiers, button)
      sendMotionEventDisplayCoordinates(adjusted, MotionEventMessage.ACTION_UP, modifiers, button)
    }
  }

  private fun sendMotionEventDisplayCoordinates(
      point: Point, action: Int, modifiers: Int, button: Int, axisValues: Int2FloatOpenHashMap? = null) {
    if (!isConnected) {
      return
    }
    val buttonState =
        (if (modifiers and BUTTON1_DOWN_MASK != 0) MotionEventMessage.BUTTON_PRIMARY else 0) or
        (if (modifiers and BUTTON2_DOWN_MASK != 0) MotionEventMessage.BUTTON_TERTIARY else 0) or
        (if (modifiers and BUTTON3_DOWN_MASK != 0) MotionEventMessage.BUTTON_SECONDARY else 0)
    val androidActionButton = when (button) {
      MouseEvent.BUTTON1 -> MotionEventMessage.BUTTON_PRIMARY
      MouseEvent.BUTTON2 -> MotionEventMessage.BUTTON_TERTIARY
      MouseEvent.BUTTON3 -> MotionEventMessage.BUTTON_SECONDARY
      else -> 0
    }
    val message = when {
      action == MotionEventMessage.ACTION_POINTER_DOWN || action == MotionEventMessage.ACTION_POINTER_UP ->
          MotionEventMessage(originalAndMirroredPointer(point), action or (1 shl MotionEventMessage.ACTION_POINTER_INDEX_SHIFT), 0, 0,
                             displayId)
      isHardwareInputEnabled() && (action == MotionEventMessage.ACTION_DOWN || action == MotionEventMessage.ACTION_UP) ->
          MotionEventMessage(originalPointer(point, axisValues), action, buttonState, androidActionButton, displayId)
      isHardwareInputEnabled() -> MotionEventMessage(originalPointer(point, axisValues), action, buttonState, 0, displayId)
      multiTouchMode -> MotionEventMessage(originalAndMirroredPointer(point), action, 0, 0, displayId)
      else -> MotionEventMessage(originalPointer(point, axisValues), action, 0, 0, displayId)
    }
    deviceController?.sendControlMessage(message)
  }

  private fun originalPointer(p: Point, axisValues: Int2FloatOpenHashMap?): List<MotionEventMessage.Pointer> {
    return listOf(MotionEventMessage.Pointer(p.x, p.y, 0, axisValues))
  }

  private fun originalAndMirroredPointer(p: Point): List<MotionEventMessage.Pointer> {
    return listOf(MotionEventMessage.Pointer(p.x, p.y, 0),
                  MotionEventMessage.Pointer(deviceDisplaySize.width - p.x, deviceDisplaySize.height - p.y, 1))
  }

  private fun isInsideDisplay(event: MouseEvent) =
    displayRectangle?.contains(event.x * screenScale, event.y * screenScale) ?: false

  /**
   * Adds a [listener] to receive callbacks when the state of the agent's connection changes.
   * The added listener immediately receives a call with the current connection state.
   */
  @UiThread
  fun addConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.add(listener)
    listener.connectionStateChanged(deviceSerialNumber, connectionState)
  }

  /** Removes a connection state listener. */
  @UiThread
  fun removeConnectionStateListener(listener: ConnectionStateListener) {
    connectionStateListeners.remove(listener)
  }

  enum class ConnectionState { INITIAL, CONNECTING, CONNECTED, DISCONNECTED }

  /** Listener of connection state changes. */
  interface ConnectionStateListener {
    /** Called when the state of the device agent's connection changes. */
    @UiThread
    fun connectionStateChanged(deviceSerialNumber: String, connectionState: ConnectionState)
  }

  private inner class MyFrameListener : VideoDecoder.FrameListener {

    override fun onNewFrameAvailable() {
      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        connected()
        if (width != 0 && height != 0) {
          repaint()
        }
      }
    }

    override fun onEndOfVideoStream() {
    }
  }

  override val hardwareInput = object : HardwareInput() {
    override fun sendToDevice(id: Int, keyCode: Int, modifiersEx: Int) {
      if (!isConnected) return
      val action = when (id) {
        KEY_PRESSED -> ACTION_DOWN
        KEY_RELEASED -> ACTION_UP
        else -> return
      }
      val metaState = modifiersToMetaState(modifiersEx)
      val akeycode = VK_TO_AKEYCODE[keyCode] ?: return
      deviceController?.sendControlMessage(KeyEventMessage(action, akeycode, metaState))
    }
  }

  private inner class MyKeyListener  : KeyAdapter() {

    var cachedKeyStrokeMap: Map<KeyStroke, AndroidKeyStroke>? = null
    private val keyStrokeMap: Map<KeyStroke, AndroidKeyStroke>
      get() {
        var map = cachedKeyStrokeMap
        if (map == null) {
          map = buildKeyStrokeMap()
          cachedKeyStrokeMap = map
        }
        return map
      }

    init {
      addFocusListener(object : FocusAdapter() {
        override fun focusGained(event: FocusEvent) {
          cachedKeyStrokeMap = null // Keyboard shortcuts may have changed while the view didn't have focus.
        }
      })
    }

    override fun keyTyped(event: KeyEvent) {
      if (!isConnected) {
        return
      }
      if (isHardwareInputEnabled()) {
        return
      }
      if (event.isControlDown || event.isMetaDown || (!SystemInfo.isMac && event.isAltDown)) {
        return
      }
      val c = event.keyChar
      if (c == CHAR_UNDEFINED || c.isISOControl()) {
        return
      }
      val message = TextInputMessage(c.toString())
      deviceController?.sendControlMessage(message)
      event.consume()
    }

    override fun keyPressed(event: KeyEvent) {
      keyPressedOrReleased(event)
    }

    override fun keyReleased(event: KeyEvent) {
      keyPressedOrReleased(event)
    }

    private fun keyPressedOrReleased(event: KeyEvent) {
      updateMultiTouchMode(event)

      if (!isConnected) {
        return
      }

      if (isHardwareInputEnabled()) {
        hardwareInput.forwardEvent(event)
        return
      }

      if (event.id == KEY_PRESSED) {
        val androidKeyStroke = hostKeyStrokeToAndroidKeyStroke(event.keyCode, event.modifiersEx)
        if (androidKeyStroke != null) {
          deviceController?.sendKeyStroke(androidKeyStroke)
          event.consume()
        }
      }
    }

    private fun hostKeyStrokeToAndroidKeyStroke(hostKeyCode: Int, modifiers: Int): AndroidKeyStroke? {
      val canonicalKeyCode = when (hostKeyCode) {
        VK_KP_LEFT -> VK_LEFT
        VK_KP_RIGHT -> VK_RIGHT
        VK_KP_UP -> VK_UP
        VK_KP_DOWN -> VK_DOWN
        else -> hostKeyCode
      }

      return keyStrokeMap[KeyStroke.getKeyStroke(canonicalKeyCode, modifiers)]
    }

    private fun buildKeyStrokeMap(): Map<KeyStroke, AndroidKeyStroke> {
      return mutableMapOf<KeyStroke, AndroidKeyStroke>().apply {
        addKeyStrokesForAction(ACTION_COPY, AndroidKeyStroke(AKEYCODE_COPY))
        addKeyStrokesForAction(ACTION_CUT, AndroidKeyStroke(AKEYCODE_CUT))
        addKeyStrokesForAction(ACTION_DELETE, AndroidKeyStroke(AKEYCODE_FORWARD_DEL))
        addKeyStrokesForAction(ACTION_PASTE, AndroidKeyStroke(AKEYCODE_PASTE))
        addKeyStrokesForAction(ACTION_SELECT_ALL, AndroidKeyStroke(AKEYCODE_A, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_EDITOR_ENTER, AndroidKeyStroke(AKEYCODE_ENTER))
        addKeyStrokesForAction(ACTION_EDITOR_ESCAPE, AndroidKeyStroke(AKEYCODE_ESCAPE))
        addKeyStrokesForAction(ACTION_EDITOR_BACKSPACE, AndroidKeyStroke(AKEYCODE_DEL))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_LEFT, AndroidKeyStroke(AKEYCODE_DPAD_LEFT))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_RIGHT, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_LEFT, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_UP, AndroidKeyStroke(AKEYCODE_DPAD_UP))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_DOWN, AndroidKeyStroke(AKEYCODE_DPAD_DOWN))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_UP, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_DOWN, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_PREVIOUS_WORD, AndroidKeyStroke(AKEYCODE_DPAD_LEFT, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_EDITOR_NEXT_WORD, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_LEFT, AMETA_CTRL_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_NEXT_WORD_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT, AMETA_CTRL_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_LINE_START, AndroidKeyStroke(AKEYCODE_MOVE_HOME))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_LINE_END, AndroidKeyStroke(AKEYCODE_MOVE_END))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_UP, AndroidKeyStroke(AKEYCODE_PAGE_UP))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_DOWN, AndroidKeyStroke(AKEYCODE_PAGE_DOWN))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_PAGE_UP, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_PAGE_DOWN, AMETA_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_TAB, AndroidKeyStroke(AKEYCODE_TAB))
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_START, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_END, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_START_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_CTRL_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_END_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_CTRL_SHIFT_ON))
        addKeyStrokesForAction(ACTION_UNDO, AndroidKeyStroke(AKEYCODE_Z, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_REDO, AndroidKeyStroke(AKEYCODE_Z, AMETA_CTRL_SHIFT_ON))
      }
    }

    private fun MutableMap<KeyStroke, AndroidKeyStroke>.addKeyStrokesForAction(actionId: String, androidKeyStroke: AndroidKeyStroke) {
      for (keyStroke in KeymapUtil.getKeyStrokes(KeymapUtil.getActiveKeymapShortcuts(actionId))) {
        put(keyStroke, androidKeyStroke)
      }
    }
  }

  private inner class MyMouseListener : MouseAdapter() {
    override fun mousePressed(event: MouseEvent) {
      requestFocusInWindow()
      if (!isInsideDisplay(event)) return
      terminateHovering(event)
      if (event.button != MouseEvent.BUTTON1 && !isHardwareInputEnabled()) return
      lastTouchCoordinates = event.location
      updateMultiTouchMode(event)
      sendMotionEvent(event.location, MotionEventMessage.ACTION_DOWN, event.modifiersEx, button = event.button)
    }

    override fun mouseReleased(event: MouseEvent) {
      if (event.button != MouseEvent.BUTTON1 && !isHardwareInputEnabled()) return
      lastTouchCoordinates = null
      updateMultiTouchMode(event)
      sendMotionEvent(event.location, MotionEventMessage.ACTION_UP, event.modifiersEx, button = event.button)
    }

    override fun mouseEntered(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    override fun mouseExited(event: MouseEvent) {
      if ((event.modifiersEx and (BUTTON1_DOWN_MASK or BUTTON2_DOWN_MASK or BUTTON3_DOWN_MASK)) != 0 && lastTouchCoordinates != null) {
        // Moving over the edge of the display view will terminate the ongoing dragging.
        sendMotionEvent(event.location, MotionEventMessage.ACTION_MOVE, event.modifiersEx)
      }
      lastTouchCoordinates = null
      updateMultiTouchMode(event)
    }

    override fun mouseDragged(event: MouseEvent) {
      updateMultiTouchMode(event)
      if ((event.modifiersEx and (BUTTON1_DOWN_MASK or BUTTON2_DOWN_MASK or BUTTON3_DOWN_MASK)) != 0 && lastTouchCoordinates != null) {
        sendMotionEvent(event.location, MotionEventMessage.ACTION_MOVE, event.modifiersEx)
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      updateMultiTouchMode(event)
      if (!multiTouchMode) {
        sendMotionEvent(event.location, MotionEventMessage.ACTION_HOVER_MOVE, event.modifiersEx)
        mouseHovering = true
      }
    }

    override fun mouseWheelMoved(event: MouseWheelEvent) {
      if (!isInsideDisplay(event)) {
        return
      }
      terminateHovering(event)
      // AWT fakes shift being held down for horizontal scrolling.
      val axis = if (event.isShiftDown) MotionEventMessage.AXIS_HSCROLL else MotionEventMessage.AXIS_VSCROLL
      // Android vertical scroll direction is reversed.
      val direction = ((event.preciseWheelRotation > 0) xor
                       (axis == MotionEventMessage.AXIS_VSCROLL) xor
                       (displayOrientationCorrectionQuadrants == 2)).toSign()
      // Behavior is undefined if we send a value outside [-1.0,1.0], so if we wind up with more than that, send it
      // as multiple sequential MotionEvents.
      // See https://developer.android.com/reference/android/view/MotionEvent#AXIS_HSCROLL and
      // https://developer.android.com/reference/android/view/MotionEvent#AXIS_VSCROLL
      var remainingRotation = event.getNormalizedScrollAmount().absoluteValue
      while (remainingRotation > 0) {
        val scrollAmount = remainingRotation.coerceAtMost(1.0) * direction
        val axisValues = Int2FloatOpenHashMap(1)
        axisValues.put(axis, scrollAmount.toFloat())
        sendMotionEvent(event.location, MotionEventMessage.ACTION_SCROLL, event.modifiersEx, axisValues = axisValues)
        remainingRotation -= 1
      }
    }

    private fun terminateHovering(event: MouseEvent) {
      if (mouseHovering) {
        val savedMultiTouchMode = multiTouchMode
        multiTouchMode = false
        sendMotionEvent(event.location, MotionEventMessage.ACTION_HOVER_EXIT, 0)
        multiTouchMode = savedMultiTouchMode
        mouseHovering = false
      }
    }

    private fun MouseWheelEvent.getNormalizedScrollAmount(): Double {
      return when (scrollType) {
        MouseWheelEvent.WHEEL_UNIT_SCROLL -> preciseWheelRotation * scrollAmount * ANDROID_SCROLL_ADJUSTMENT_FACTOR
        MouseWheelEvent.WHEEL_BLOCK_SCROLL -> 1.0
        /** Wheel events generated by touch screens have scrollType = 3 that is neither WHEEL_UNIT_SCROLL, nor WHEEL_BLOCK_SCROLL. */
        else -> preciseWheelRotation * scrollAmount / scale * TOUCH_SCREEN_ADJUSTMENT_FACTOR
      }
    }

    /** Converts true to 1 and false to -1. */
    private fun Boolean.toSign(): Int = if (this) 1 else -1
  }

  private fun updateMultiTouchMode(event: InputEvent) {
    val oldMultiTouchMode = multiTouchMode
    if (event is MouseEvent) {
      wasInsideDisplay = isInsideDisplay(event)
    }
    multiTouchMode = wasInsideDisplay && (event.modifiersEx and CTRL_DOWN_MASK) != 0 && !isHardwareInputEnabled()
    if (multiTouchMode && oldMultiTouchMode) {
      repaint() // If multi-touch mode changed above, the repaint method was already called.
    }
  }

  companion object {
    /**
     * This is how much we want to adjust the mouse scroll for Android. This number was chosen by
     * trying different numbers until scrolling felt usable.
     */
    @VisibleForTesting
    internal const val ANDROID_SCROLL_ADJUSTMENT_FACTOR = 0.125f
    private const val TOUCH_SCREEN_ADJUSTMENT_FACTOR = 0.02f

    private fun modifiersToMetaState(modifiers: Int): Int {
      return modifierToMetaState(modifiers, SHIFT_DOWN_MASK, AMETA_SHIFT_ON) or
        modifierToMetaState(modifiers, CTRL_DOWN_MASK, AMETA_CTRL_ON) or
        modifierToMetaState(modifiers, META_DOWN_MASK, AMETA_META_ON) or
        modifierToMetaState(modifiers, ALT_DOWN_MASK, AMETA_ALT_ON)
    }

    private fun modifierToMetaState(modifiers: Int, modifierMask: Int, metaState: Int) =
      if ((modifiers and modifierMask) != 0) metaState else 0
  }
}

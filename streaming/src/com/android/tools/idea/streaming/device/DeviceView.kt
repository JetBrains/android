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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.streaming.AbstractDisplayView
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.DeviceMirroringSettingsListener
import com.android.tools.idea.streaming.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.constrainInside
import com.android.tools.idea.streaming.contains
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.DeviceClient.AgentTerminationListener
import com.android.tools.idea.streaming.location
import com.android.tools.idea.streaming.rotatedByQuadrants
import com.android.tools.idea.streaming.scaled
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions.ACTION_COPY
import com.intellij.openapi.actionSystem.IdeActions.ACTION_CUT
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
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_END
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_START
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION
import com.intellij.openapi.actionSystem.IdeActions.ACTION_PASTE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_SELECT_ALL
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.UIUtil
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import kotlinx.coroutines.launch
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
import java.awt.event.InputEvent.ALT_DOWN_MASK
import java.awt.event.InputEvent.BUTTON1_DOWN_MASK
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.META_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_CONTROL
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import java.util.concurrent.CancellationException
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
  private val deviceClient: DeviceClient,
  private val initialDisplayOrientation: Int,
  private val project: Project,
) : AbstractDisplayView(PRIMARY_DISPLAY_ID), Disposable, DeviceMirroringSettingsListener {

  val isConnected: Boolean
    get() = connectionState == ConnectionState.CONNECTED

  override val deviceSerialNumber: String
    get() = deviceClient.deviceSerialNumber

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

  internal val deviceController: DeviceController?
    get() = deviceClient.deviceController

  override val deviceDisplaySize = Dimension()

  private var clipboardSynchronizer: DeviceClipboardSynchronizer? = null
  private val connectionStateListeners = mutableListOf<ConnectionStateListener>()
  private val agentTerminationListener = object: AgentTerminationListener {
    override fun agentTerminated(exitCode: Int) { disconnected(initialDisplayOrientation) }
    override fun deviceDisconnected() { disconnected(initialDisplayOrientation) }
  }
  private val frameListener = MyFrameListener()
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
          sendMotionEvent(point, action)
        }
      }
    }
  /** Last coordinates of the mouse pointer while the first button is pressed, null when the first button is released. */
  private var lastTouchCoordinates: Point? = null

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
    connectionState = ConnectionState.CONNECTING
    val maxOutputSize = physicalSize
    AndroidCoroutineScope(this@DeviceView).launch {
      connectToAgent(maxOutputSize, initialDisplayOrientation)
    }
  }

  private suspend fun connectToAgent(maxOutputSize: Dimension, initialDisplayOrientation: Int) {
    try {
      deviceClient.addAgentTerminationListener(agentTerminationListener)
      deviceClient.establishAgentConnection(maxOutputSize, initialDisplayOrientation, startVideoStream = true)
      val videoDecoder = deviceClient.videoDecoder ?: return
      videoDecoder.addFrameListener(frameListener)

      UIUtil.invokeLaterIfNeeded { // This is safe because this code doesn't touch PSI or VFS.
        if (!disposed) {
          connected()
          if (DeviceMirroringSettings.getInstance().synchronizeClipboard) {
            startClipboardSynchronization()
            clipboardSynchronizer = DeviceClipboardSynchronizer(this, deviceClient)
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
    val videoDecoder = deviceClient.videoDecoder ?: return
    if (videoDecoder.maxOutputSize != physicalSize) {
      videoDecoder.maxOutputSize = physicalSize
      deviceController?.sendControlMessage(SetMaxVideoResolutionMessage(physicalWidth, physicalHeight))
    }
  }

  private fun connected() {
    if (connectionState == ConnectionState.CONNECTING) {
      hideDisconnectedStateMessage()
      connectionState = ConnectionState.CONNECTED
    }
  }

  private fun disconnected(initialDisplayOrientation: Int, exception: Throwable? = null) {
    deviceClient.removeAgentTerminationListener(agentTerminationListener)
    UIUtil.invokeLaterIfNeeded {
      if (disposed) {
        return@invokeLaterIfNeeded
      }
      stopClipboardSynchronization()
      val message: String
      val reconnector: Reconnector
      when (connectionState) {
        ConnectionState.CONNECTING -> {
          thisLogger().error("Failed to initialize the screen sharing agent", exception)
          message = "Failed to initialize the device agent. See the error log."
          reconnector = Reconnector("Retry", "Connecting to the device") { connectToAgentAsync(initialDisplayOrientation) }
        }

        ConnectionState.CONNECTED -> {
          message = "Lost connection to the device. See the error log."
          reconnector = Reconnector("Reconnect", "Attempting to reconnect") { connectToAgentAsync(UNKNOWN_ORIENTATION) }
        }

        else -> return@invokeLaterIfNeeded
      }

      connectionState = ConnectionState.DISCONNECTED
      showDisconnectedStateMessage(message, reconnector)
    }
  }

  override fun dispose() {
    deviceClient.stopVideoStream()
    deviceClient.removeAgentTerminationListener(agentTerminationListener)
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

    val decoder = deviceClient.videoDecoder ?: return
    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw device display.
    decoder.consumeDisplayFrame { displayFrame ->
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
        displayTransform.setToTranslation(displayRect.x.toDouble(), displayRect.y.toDouble())
        displayTransform.scale(displayRect.width.toDouble() / image.width, displayRect.height.toDouble() / image.height)
        g.drawImage(image, displayTransform, null)
      }

      deviceDisplaySize.size = displayFrame.displaySize
      displayOrientationQuadrants = displayFrame.orientation
      displayOrientationCorrectionQuadrants = displayFrame.orientationCorrection
      frameNumber = displayFrame.frameNumber
      notifyFrameListeners(displayRect, displayFrame.image)

      with (deviceClient) {
        if (startTime != 0L) {
          val delay = System.currentTimeMillis() - startTime
          val pushDelay = pushEndTime - startTime
          val agentStartDelay = startAgentTime - startTime
          val connectionDelay = channelConnectedTime - startTime
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
    if (deviceController == null) {
      return
    }
    if (settings.synchronizeClipboard) {
      startClipboardSynchronization()
    }
    else {
      stopClipboardSynchronization()
    }
  }

  private fun startClipboardSynchronization() {
    val synchronizer = clipboardSynchronizer
    if (synchronizer == null) {
      // Start clipboard synchronization.
      clipboardSynchronizer = DeviceClipboardSynchronizer(this, deviceClient)
    }
    else {
      // Pass the new value of maxSyncedClipboardLength to the device.
      synchronizer.setDeviceClipboard()
    }
  }

  private fun stopClipboardSynchronization() {
    clipboardSynchronizer?.let {
      // Stop clipboard synchronization.
      Disposer.dispose(it)
      clipboardSynchronizer = null
    }
  }

  private fun sendMotionEvent(p: Point, action: Int, axisValues: Int2FloatOpenHashMap? = null) {
    val displayCoordinates = toDeviceDisplayCoordinates(p) ?: return

    if (displayCoordinates in deviceDisplaySize) {
      // Within the bounds of the device display.
      sendMotionEventDisplayCoordinates(displayCoordinates, action, axisValues)
    }
    else if (action == MotionEventMessage.ACTION_MOVE) {
      // Crossed the device display boundary while dragging.
      lastTouchCoordinates = null
      val adjusted = displayCoordinates.constrainInside(deviceDisplaySize)
      sendMotionEventDisplayCoordinates(adjusted, action)
      sendMotionEventDisplayCoordinates(adjusted, MotionEventMessage.ACTION_UP)
    }
  }

  private fun sendMotionEventDisplayCoordinates(p: Point, action: Int, axisValues: Int2FloatOpenHashMap? = null) {
    if (!isConnected) {
      return
    }
    val message = when {
      action == MotionEventMessage.ACTION_POINTER_DOWN || action == MotionEventMessage.ACTION_POINTER_UP ->
          MotionEventMessage(originalAndMirroredPointer(p),action or (1 shl MotionEventMessage.ACTION_POINTER_INDEX_SHIFT), displayId)
      multiTouchMode -> MotionEventMessage(originalAndMirroredPointer(p), action, displayId)
      else -> MotionEventMessage(originalPointer(p, axisValues), action, displayId)
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
    @AnyThread
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
      val c = event.keyChar
      if (c == CHAR_UNDEFINED || Character.isISOControl(c)) {
        return
      }
      val message = TextInputMessage(c.toString())
      deviceController?.sendControlMessage(message)
    }

    override fun keyPressed(event: KeyEvent) {
      keyPressedOrReleased(event)
    }

    override fun keyReleased(event: KeyEvent) {
      keyPressedOrReleased(event)
    }

    private fun keyPressedOrReleased(event: KeyEvent) {
      val keyCode = event.keyCode
      val modifiers = event.modifiersEx

      if (keyCode == VK_CONTROL) {
        if (modifiers == CTRL_DOWN_MASK) {
          multiTouchMode = true
        }
        else if ((modifiers and CTRL_DOWN_MASK) == 0) {
          multiTouchMode = false
        }
      }

      // The Tab character is passed to the device, but Shift+Tab is converted to Tab and processed locally.
      if (keyCode == VK_TAB && modifiers == SHIFT_DOWN_MASK) {
        if (event.id == KEY_PRESSED) {
          val tabEvent = KeyEvent(event.component, event.id, event.getWhen(), 0, keyCode, event.keyChar, event.keyLocation)
          traverseFocusLocally(tabEvent)
        }
        return
      }

      if (!isConnected) {
        return
      }
      val androidKeyStroke = hostKeyStrokeToAndroidKeyStroke(keyCode, modifiers)
      if (androidKeyStroke == null) {
        if (modifiers == 0) {
          val androidKeyCode = hostKeyCodeToDeviceKeyCode(keyCode)
          if (androidKeyCode != AKEYCODE_UNKNOWN) {
            val action = if (event.id == KEY_PRESSED) ACTION_DOWN else ACTION_UP
            deviceController?.sendControlMessage(KeyEventMessage(action, androidKeyCode, modifiersToMetaState(modifiers)))
          }
        }
      }
      else if (event.id == KEY_PRESSED) {
        deviceController?.sendKeyStroke(androidKeyStroke)
      }
      event.consume()
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

    private fun hostKeyCodeToDeviceKeyCode(hostKeyCode: Int): Int {
      return when (hostKeyCode) {
        VK_BACK_SPACE -> AKEYCODE_DEL
        VK_DELETE -> if (SystemInfo.isMac) AKEYCODE_DEL else AKEYCODE_FORWARD_DEL
        VK_ENTER -> AKEYCODE_ENTER
        VK_ESCAPE -> AKEYCODE_ESCAPE
        VK_TAB -> AKEYCODE_TAB
        else -> AKEYCODE_UNKNOWN
      }
    }

    private fun modifiersToMetaState(modifiers: Int): Int {
      return modifierToMetaState(modifiers, SHIFT_DOWN_MASK,  AMETA_SHIFT_ON) or
             modifierToMetaState(modifiers, CTRL_DOWN_MASK,  AMETA_CTRL_ON) or
             modifierToMetaState(modifiers, META_DOWN_MASK,  AMETA_META_ON) or
             modifierToMetaState(modifiers, ALT_DOWN_MASK,  AMETA_ALT_ON)
    }

    private fun modifierToMetaState(modifiers: Int, modifierMask: Int, metaState: Int) =
        if ((modifiers and modifierMask) != 0) metaState else 0

    private fun buildKeyStrokeMap(): Map<KeyStroke, AndroidKeyStroke> {
      return mutableMapOf<KeyStroke, AndroidKeyStroke>().apply {
        addKeyStrokesForAction(ACTION_CUT, AndroidKeyStroke(AKEYCODE_CUT))
        addKeyStrokesForAction(ACTION_COPY, AndroidKeyStroke(AKEYCODE_COPY))
        addKeyStrokesForAction(ACTION_PASTE, AndroidKeyStroke(AKEYCODE_PASTE))
        addKeyStrokesForAction(ACTION_SELECT_ALL, AndroidKeyStroke(AKEYCODE_A, AMETA_CTRL_ON))
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
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_START, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_END, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_CTRL_ON))
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_START_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_CTRL_SHIFT_ON))
        addKeyStrokesForAction(ACTION_EDITOR_TEXT_END_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_CTRL_SHIFT_ON))
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
      if (isInsideDisplay(event) && event.button == BUTTON1) {
        event.location.let {
          lastTouchCoordinates = it
          updateMultiTouchMode(event)
          sendMotionEvent(it, MotionEventMessage.ACTION_DOWN)
        }
      }
    }

    override fun mouseReleased(event: MouseEvent) {
      if (event.button == BUTTON1) {
        lastTouchCoordinates = null
        updateMultiTouchMode(event)
        sendMotionEvent(event.location, MotionEventMessage.ACTION_UP)
      }
    }

    override fun mouseEntered(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    override fun mouseExited(event: MouseEvent) {
      if ((event.modifiersEx and BUTTON1_DOWN_MASK) != 0 && lastTouchCoordinates != null) {
        // Moving over the edge of the display view will terminate the ongoing dragging.
        sendMotionEvent(event.location, MotionEventMessage.ACTION_MOVE)
      }
      lastTouchCoordinates = null
      multiTouchMode = false
    }

    override fun mouseDragged(event: MouseEvent) {
      updateMultiTouchMode(event)
      if ((event.modifiersEx and BUTTON1_DOWN_MASK) != 0 && lastTouchCoordinates != null) {
        sendMotionEvent(event.location, MotionEventMessage.ACTION_MOVE)
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    override fun mouseWheelMoved(event: MouseWheelEvent) {
      if (!isInsideDisplay(event)) return
      // Java fakes shift being held down for horizontal scrolling.
      val axis = if (event.isShiftDown) MotionEventMessage.AXIS_HSCROLL else MotionEventMessage.AXIS_VSCROLL
      // Android scroll direction is reversed, but only vertically.
      val direction = if ((axis == MotionEventMessage.AXIS_VSCROLL) xor (event.preciseWheelRotation > 0)) 1 else -1
      // Behavior is undefined if we send a value outside [-1.0,1.0], so if we wind up with more than that, send it
      // as multiple sequential MotionEvents.
      // See https://developer.android.com/reference/android/view/MotionEvent#AXIS_HSCROLL and
      // https://developer.android.com/reference/android/view/MotionEvent#AXIS_VSCROLL
      var remainingRotation = event.getNormalizedScrollAmount()
      while (remainingRotation > 0) {
        val scrollAmount = remainingRotation.coerceAtMost(1.0f) * direction
        val axisValues = Int2FloatOpenHashMap(1)
        axisValues.put(axis, scrollAmount)
        sendMotionEvent(event.location, MotionEventMessage.ACTION_SCROLL, axisValues)
        remainingRotation -= 1
      }
    }

    private fun MouseWheelEvent.getNormalizedScrollAmount(): Float {
      if (scrollType != MouseWheelEvent.WHEEL_UNIT_SCROLL) return 1.0f
      return (preciseWheelRotation * scrollAmount).absoluteValue.toFloat() * ANDROID_SCROLL_ADJUSTMENT_FACTOR
    }

    private fun updateMultiTouchMode(event: MouseEvent) {
      val oldMultiTouchMode = multiTouchMode
      multiTouchMode = isInsideDisplay(event) && (event.modifiersEx and CTRL_DOWN_MASK) != 0
      if (multiTouchMode && oldMultiTouchMode) {
        repaint() // If multi-touch mode changed above, the repaint method was already called.
      }
    }
  }

  companion object {
    // This is how much we want to adjust the mouse scroll for Android. This number was chosen by
    // trying different numbers until scrolling felt usable.
    @VisibleForTesting
    internal const val ANDROID_SCROLL_ADJUSTMENT_FACTOR = 0.125f
  }
}

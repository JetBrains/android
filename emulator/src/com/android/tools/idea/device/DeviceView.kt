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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.device.DeviceClient.AgentTerminationListener
import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.DeviceMirroringSettings
import com.android.tools.idea.emulator.DeviceMirroringSettingsListener
import com.android.tools.idea.emulator.PRIMARY_DISPLAY_ID
import com.android.tools.idea.emulator.constrainInside
import com.android.tools.idea.emulator.contains
import com.android.tools.idea.emulator.location
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.android.tools.idea.emulator.scaled
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
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.geom.AffineTransform
import java.util.concurrent.CancellationException
import javax.swing.KeyStroke
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
  private val deviceName: String,
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

  private var cachedKeyStrokeMap: Map<KeyStroke, AndroidKeyStroke>? = null
  private val keyStrokeMap: Map<KeyStroke, AndroidKeyStroke>
    get() {
      var map = cachedKeyStrokeMap
      if (map == null) {
        map = buildKeystrokeMap()
        cachedKeyStrokeMap = map
      }
      return map
    }

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
    addFocusListener(object : FocusAdapter() {
      override fun focusGained(event: FocusEvent) {
        cachedKeyStrokeMap = null // Keyboard shortcuts may have changed while the view didn't have focus.
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
      val deviceClient = DeviceClient(this, deviceSerialNumber, deviceAbi, deviceName, project)
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
      if (displayOrientationQuadrants != displayFrame.orientation ||
          deviceDisplaySize.width != 0 && deviceDisplaySize.width != displayFrame.displaySize.width ||
          deviceDisplaySize.height != 0 && deviceDisplaySize.height != displayFrame.displaySize.height) {
        zoom(ZoomType.FIT) // Orientation or dimensions of the display have changed - reset zoom level.
      }
      val rotatedDisplaySize = displayFrame.displaySize.rotatedByQuadrants(displayFrame.orientation)
      val scale = roundScale(min(realWidth.toDouble() / rotatedDisplaySize.width, realHeight.toDouble() / rotatedDisplaySize.height))
      val w = rotatedDisplaySize.width.scaled(scale).coerceAtMost(realWidth)
      val h = rotatedDisplaySize.height.scaled(scale).coerceAtMost(realHeight)
      val displayRect = Rectangle((realWidth - w) / 2, (realHeight - h) / 2, w, h)
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

  private fun sendMotionEvent(p: Point, action: Int) {
    val displayCoordinates = toDeviceDisplayCoordinates(p) ?: return

    if (displayCoordinates in deviceDisplaySize) {
      // Within the bounds of the device display.
      sendMotionEventDisplayCoordinates(displayCoordinates, action)
    }
    else if (action == MotionEventMessage.ACTION_MOVE) {
      // Crossed the device display boundary while dragging.
      lastTouchCoordinates = null
      val adjusted = displayCoordinates.constrainInside(deviceDisplaySize)
      sendMotionEventDisplayCoordinates(adjusted, action)
      sendMotionEventDisplayCoordinates(adjusted, MotionEventMessage.ACTION_UP)
    }
  }

  private fun sendMotionEventDisplayCoordinates(p: Point, action: Int) {
    val deviceController = deviceController ?: return
    val message = when {
      action == MotionEventMessage.ACTION_POINTER_DOWN || action == MotionEventMessage.ACTION_POINTER_UP ->
        MotionEventMessage(originalAndMirroredPointer(p),action or (1 shl MotionEventMessage.ACTION_POINTER_INDEX_SHIFT), displayId)
      multiTouchMode -> MotionEventMessage(originalAndMirroredPointer(p), action, displayId)
      else -> MotionEventMessage(originalPointer(p), action, displayId)
    }

    deviceController.sendControlMessage(message)
  }

  private fun originalPointer(p: Point): List<MotionEventMessage.Pointer> {
    return listOf(MotionEventMessage.Pointer(p.x, p.y, 0))
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

  private fun buildKeystrokeMap(): Map<KeyStroke, AndroidKeyStroke> {
    return mutableMapOf<KeyStroke, AndroidKeyStroke>().apply {
      addKeystrokesForAction(ACTION_CUT, AndroidKeyStroke(AKEYCODE_CUT))
      addKeystrokesForAction(ACTION_CUT, AndroidKeyStroke(AKEYCODE_CUT))
      addKeystrokesForAction(ACTION_COPY, AndroidKeyStroke(AKEYCODE_COPY))
      addKeystrokesForAction(ACTION_PASTE, AndroidKeyStroke(AKEYCODE_PASTE))
      addKeystrokesForAction(ACTION_SELECT_ALL, AndroidKeyStroke(AKEYCODE_A, AMETA_CTRL_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_LEFT, AndroidKeyStroke(AKEYCODE_DPAD_LEFT))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_RIGHT, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_LEFT, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_UP, AndroidKeyStroke(AKEYCODE_DPAD_UP))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_DOWN, AndroidKeyStroke(AKEYCODE_DPAD_DOWN))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_UP, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_DOWN, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_PREVIOUS_WORD, AndroidKeyStroke(AKEYCODE_DPAD_LEFT, AMETA_CTRL_ON))
      addKeystrokesForAction(ACTION_EDITOR_NEXT_WORD, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT, AMETA_CTRL_ON))
      addKeystrokesForAction(ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_LEFT, AMETA_CTRL_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_NEXT_WORD_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_DPAD_RIGHT, AMETA_CTRL_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_LINE_START, AndroidKeyStroke(AKEYCODE_MOVE_HOME))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_LINE_END, AndroidKeyStroke(AKEYCODE_MOVE_END))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_UP, AndroidKeyStroke(AKEYCODE_PAGE_UP))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_DOWN, AndroidKeyStroke(AKEYCODE_PAGE_DOWN))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_PAGE_UP, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_PAGE_DOWN, AMETA_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_TEXT_START, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_CTRL_ON))
      addKeystrokesForAction(ACTION_EDITOR_TEXT_END, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_CTRL_ON))
      addKeystrokesForAction(ACTION_EDITOR_TEXT_START_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_HOME, AMETA_CTRL_SHIFT_ON))
      addKeystrokesForAction(ACTION_EDITOR_TEXT_END_WITH_SELECTION, AndroidKeyStroke(AKEYCODE_MOVE_END, AMETA_CTRL_SHIFT_ON))
    }
  }

  private fun MutableMap<KeyStroke, AndroidKeyStroke>.addKeystrokesForAction(actionId: String, androidKeystroke: AndroidKeyStroke) {
    for (keyStroke in KeymapUtil.getKeyStrokes(KeymapUtil.getActiveKeymapShortcuts(actionId))) {
      put(keyStroke, androidKeystroke)
    }
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
          val tabEvent =
              KeyEvent(event.source as Component, event.id, event.getWhen(), 0, keyCode, event.keyChar, event.keyLocation)
          traverseFocusLocally(tabEvent)
        }
        return
      }

      val deviceController = deviceController ?: return
      val androidKeystroke = hostKeyStrokeToAndroidKeyStroke(keyCode, modifiers)
      if (androidKeystroke == null) {
        if (modifiers == 0) {
          val androidKeyCode = hostKeyCodeToDeviceKeyCode(keyCode)
          if (androidKeyCode != AKEYCODE_UNKNOWN) {
            val action = if (event.id == KEY_PRESSED) ACTION_DOWN else ACTION_UP
            deviceController.sendControlMessage(KeyEventMessage(action, androidKeyCode, modifiersToMetaState(modifiers)))
          }
        }
      }
      else if (event.id == KEY_PRESSED) {
        deviceController.sendKeystroke(androidKeystroke)
      }
    }

    private fun hostKeyStrokeToAndroidKeyStroke(hostKeyCode: Int, modifiers: Int): AndroidKeyStroke? {
      val canonicalKeyCode = when (hostKeyCode) {
        KeyEvent.VK_KP_LEFT -> KeyEvent.VK_LEFT
        KeyEvent.VK_KP_RIGHT -> KeyEvent.VK_RIGHT
        KeyEvent.VK_KP_UP -> KeyEvent.VK_UP
        KeyEvent.VK_KP_DOWN -> KeyEvent.VK_DOWN
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

    private fun updateMultiTouchMode(event: MouseEvent) {
      val oldMultiTouchMode = multiTouchMode
      multiTouchMode = isInsideDisplay(event) && (event.modifiersEx and CTRL_DOWN_MASK) != 0
      if (multiTouchMode && oldMultiTouchMode) {
        repaint() // If multi-touch mode changed above, the repaint method was already called.
      }
    }
  }
}

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

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.emulator.control.ClipData
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.KeyboardEvent.KeyEventType
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_ACTIVE
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_INACTIVE
import com.android.emulator.control.Rotation.SkinRotation
import com.android.emulator.control.ThemingStyle
import com.android.ide.common.util.Cancelable
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.Empty
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.ide.DataManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_D
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_E
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
import java.awt.event.KeyEvent.VK_Q
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_S
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.KeyEvent.VK_W
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.awt.image.MemoryImageSource
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import com.android.emulator.control.Image as ImageMessage
import com.android.emulator.control.MouseEvent as MouseEventMessage
import com.android.emulator.control.Notification as EmulatorNotification

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param parentDisposable the disposable parent
 * @param emulator the handle of the Emulator
 * @param deviceFrameVisible controls visibility of the device frame
 */
class EmulatorView(
  parentDisposable: Disposable,
  val emulator: EmulatorController,
  deviceFrameVisible: Boolean
) : JPanel(BorderLayout()), ComponentListener, ConnectionStateListener, Zoomable, Disposable {

  private var disconnectedStateLabel: JLabel
  private var screenshotImage: Image? = null
  private var screenshotShape = DisplayShape(0, 0, SkinRotation.PORTRAIT)
  private var displayRectangle: Rectangle? = null
  private var skinLayout: SkinLayout? = null
  private val displayTransform = AffineTransform()
  /** Count of received display frames. */
  @VisibleForTesting
  var frameNumber = 0
    private set
  /** Time of the last frame update in milliseconds since epoch. */
  @VisibleForTesting
  var frameTimestampMillis = 0L
    private set

  private var screenshotFeed: Cancelable? = null
  @Volatile
  private var screenshotReceiver: ScreenshotReceiver? = null

  private var clipboardFeed: Cancelable? = null
  @Volatile
  private var clipboardReceiver: ClipboardReceiver? = null

  private var notificationFeed: Cancelable? = null
  @Volatile
  private var notificationReceiver: NotificationReceiver? = null

  var displayRotation: SkinRotation
    get() = screenshotShape.rotation
    set(value) {
      if (value != screenshotShape.rotation && deviceFrameVisible) {
        requestScreenshotFeed(value)
      }
    }

  var deviceFrameVisible: Boolean = deviceFrameVisible
    set(value) {
      if (field != value) {
        field = value
        requestScreenshotFeed()
      }
    }

  private val emulatorConfig
    get() = emulator.emulatorConfig

  private val connected
    get() = emulator.connectionState == ConnectionState.CONNECTED

  private var screenScale = 0.0 // Scale factor of the host screen.
    get() {
      if (field == 0.0) {
        field = graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
      }
      return field
    }

  /** Width in physical pixels. */
  private val realWidth
    get() = width.scaled(screenScale)

  /** Height in physical pixels. */
  private val realHeight
    get() = height.scaled(screenScale)

  /** Size in physical pixels. */
  private val realSize
    get() = Dimension(realWidth, realHeight)

  override val screenScalingFactor
    get() = screenScale

  override val scale: Double
    get() = computeScaleToFit(realSize, screenshotShape.rotation)

  private var virtualSceneCameraActive = false
    set(value) {
      if (value != field) {
        field = value
        if (value) {
          if (isFocusOwner) {
            showVirtualSceneCameraPrompt()
          }
        }
        else {
          virtualSceneCameraOperating = false
          hideVirtualSceneCameraPrompt()
        }
      }
    }

  private var virtualSceneCameraOperating = false
    set(value) {
      if (value != field) {
        field = value
        if (value) {
          startOperatingVirtualSceneCamera()
        }
        else {
          stopOperatingVirtualSceneCamera()
        }
      }
    }

  private var virtualSceneCameraOperatingDisposable: Disposable? = null
  private val virtualSceneCameraOrientation = Point()
  private val virtualSceneCameraReferencePoint = Point()

  init {
    Disposer.register(parentDisposable, this)

    disconnectedStateLabel = JLabel()
    disconnectedStateLabel.horizontalAlignment = SwingConstants.CENTER
    disconnectedStateLabel.font = disconnectedStateLabel.font.deriveFont(disconnectedStateLabel.font.size * 1.2F)

    isFocusable = true // Must be focusable to receive keyboard events.
    focusTraversalKeysEnabled = false // Receive focus traversal keys to send them to the emulator.

    emulator.addConnectionStateListener(this)
    addComponentListener(this)

    // Forward mouse & keyboard events.
    val mouseListener = object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 1)
      }

      override fun mouseReleased(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 0)
      }

      override fun mouseClicked(event: MouseEvent) {
        requestFocusInWindow()
      }

      override fun mouseDragged(event: MouseEvent) {
        if (!virtualSceneCameraActive) {
          sendMouseEvent(event.x, event.y, 1)
        }
      }
    }
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)

    addKeyListener(object : KeyAdapter() {
      override fun keyTyped(event: KeyEvent) {
        if (virtualSceneCameraOperating) {
          return
        }

        val c = event.keyChar
        if (c == CHAR_UNDEFINED || Character.isISOControl(c)) {
          return
        }

        val keyboardEvent = KeyboardEvent.newBuilder().setText(c.toString()).build()
        emulator.sendKey(keyboardEvent)
      }

      override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == VK_SHIFT && event.modifiersEx == SHIFT_DOWN_MASK && virtualSceneCameraActive) {
          virtualSceneCameraOperating = true
          return
        }

        if (virtualSceneCameraOperating) {
          val point =
            when (event.keyCode) {
              VK_LEFT, VK_KP_LEFT -> Point(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS, 0)
              VK_RIGHT, VK_KP_RIGHT -> Point(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS, 0)
              VK_UP, VK_KP_UP -> Point(0, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS)
              VK_DOWN, VK_KP_DOWN -> Point(0, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS)
              VK_HOME -> Point(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS)
              VK_END -> Point(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS)
              VK_PAGE_UP -> Point(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS)
              VK_PAGE_DOWN -> Point(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS)
              else -> null
            }
          if (point != null) {
            virtualSceneCameraOrientation.translate(point.x, point.y)
            virtualSceneCameraReferencePoint.translate(-point.x, -point.y)
            val mouseEvent = MouseEventMessage.newBuilder()
              .setX(virtualSceneCameraOrientation.x)
              .setY(virtualSceneCameraOrientation.y)
              .build()
            emulator.sendMouse(mouseEvent)
          }
          else {
            val keyName =
              when (event.keyCode) {
                VK_Q -> "KeyQ"
                VK_W -> "KeyW"
                VK_E -> "KeyE"
                VK_A -> "KeyA"
                VK_S -> "KeyS"
                VK_D -> "KeyD"
                else -> return
              }
            emulator.sendKey(createHardwareKeyEvent(keyName, eventType = KeyEventType.keydown))
          }
          return
        }

        // The Tab character is passed to the emulator, but Shift+Tab is converted to Tab and processed locally.
        if (event.keyCode == VK_TAB && event.modifiersEx == SHIFT_DOWN_MASK) {
          val tabEvent = KeyEvent(event.source as Component, event.id, event.getWhen(), 0, event.keyCode, event.keyChar, event.keyLocation)
          traverseFocusLocally(tabEvent)
          return
        }

        if (event.modifiersEx != 0) {
          return
        }
        val keyName =
          when (event.keyCode) {
            VK_BACK_SPACE -> "Backspace"
            VK_DELETE -> if (SystemInfo.isMac) "Backspace" else "Delete"
            VK_ENTER -> "Enter"
            VK_ESCAPE -> "Escape"
            VK_TAB -> "Tab"
            VK_LEFT, VK_KP_LEFT -> "ArrowLeft"
            VK_RIGHT, VK_KP_RIGHT -> "ArrowRight"
            VK_UP, VK_KP_UP -> "ArrowUp"
            VK_DOWN, VK_KP_DOWN -> "ArrowDown"
            VK_HOME -> "Home"
            VK_END -> "End"
            VK_PAGE_UP -> "PageUp"
            VK_PAGE_DOWN -> "PageDown"
            else -> return
          }
        emulator.sendKey(createHardwareKeyEvent(keyName))
      }

      override fun keyReleased(event: KeyEvent) {
        if (event.keyCode == VK_SHIFT) {
          virtualSceneCameraOperating = false
        }
        else if (virtualSceneCameraOperating) {
          val keyName =
            when (event.keyCode) {
              VK_Q -> "KeyQ"
              VK_W -> "KeyW"
              VK_E -> "KeyE"
              VK_A -> "KeyA"
              VK_S -> "KeyS"
              VK_D -> "KeyD"
              else -> return
            }
          emulator.sendKey(createHardwareKeyEvent(keyName, eventType = KeyEventType.keyup))
        }
      }
    })

    addFocusListener(object : FocusAdapter() {
      override fun focusGained(event: FocusEvent) {
        if (connected) {
          setDeviceClipboardAndListenToChanges()
        }
        if (virtualSceneCameraActive) {
          showVirtualSceneCameraPrompt()
        }
      }

      override fun focusLost(event: FocusEvent) {
        cancelClipboardFeed()
        hideVirtualSceneCameraPrompt()
      }
    })

    if (StudioFlags.EMBEDDED_EMULATOR_EXTENDED_CONTROLS.get()) {
      val connection = ApplicationManager.getApplication().messageBus.connect(this)
      connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
        if (connected) {
          val style = if (StartupUiUtil.isUnderDarcula()) ThemingStyle.Style.DARK else ThemingStyle.Style.LIGHT
          emulator.setUiTheme(style)
        }
      })
    }

    updateConnectionState(emulator.connectionState)
  }

  override fun dispose() {
    cancelNotificationFeed()
    cancelClipboardFeed()
    cancelScreenshotFeed()
    removeComponentListener(this)
    emulator.removeConnectionStateListener(this)
  }

  override fun zoom(type: ZoomType): Boolean {
    val scaledSize = computeZoomedSize(type)
    if (scaledSize == preferredSize) {
      return false
    }
    preferredSize = scaledSize
    revalidate()
    return true
  }

  override fun canZoomIn(): Boolean {
    return connected && computeZoomedSize(ZoomType.IN) != explicitlySetPreferredSize
  }

  override fun canZoomOut(): Boolean {
    return connected && computeZoomedSize(ZoomType.OUT) != explicitlySetPreferredSize
  }

  override fun canZoomToActual(): Boolean {
    return connected && computeZoomedSize(ZoomType.ACTUAL) != explicitlySetPreferredSize
  }

  override fun canZoomToFit(): Boolean {
    return connected && isPreferredSizeSet
  }

  internal val explicitlySetPreferredSize: Dimension?
    get() = if (isPreferredSizeSet) preferredSize else null

  /**
   * Computes the preferred size in virtual pixels after the given zoom operation.
   * The preferred size is null for zoom to fit.
   */
  private fun computeZoomedSize(zoomType: ZoomType): Dimension? {
    val newScale = when (zoomType) {
      ZoomType.IN -> {
        min(ZoomType.zoomIn((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0, MAX_SCALE)
      }
      ZoomType.OUT -> {
        max(ZoomType.zoomOut((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0, computeScaleToFitInParent())
      }
      ZoomType.ACTUAL -> {
        1.0
      }
      ZoomType.FIT -> {
        return null
      }
      else -> throw IllegalArgumentException("Unsupported zoom type $zoomType")
    }
    val scaledSize = computeScaledSize(newScale, screenshotShape.rotation)
    val availableSize = computeAvailableSize()
    if (scaledSize.width <= availableSize.width && scaledSize.height <= availableSize.height) {
      return null
    }
    return scaledSize.scaled(1 / screenScale)
  }

  private fun computeScaleToFitInParent() = computeScaleToFit(computeAvailableSize(), screenshotShape.rotation)

  private fun computeAvailableSize(): Dimension {
    val insets = parent.insets
    return Dimension((parent.width - insets.left - insets.right).scaled(screenScale),
                     (parent.height - insets.top - insets.bottom).scaled(screenScale))
  }

  private fun computeScaleToFit(availableSize: Dimension, rotation: SkinRotation): Double {
    return computeScaleToFit(computeActualSize(rotation), availableSize)
  }

  private fun computeScaleToFit(actualSize: Dimension, availableSize: Dimension): Double {
    val scale = min(availableSize.width.toDouble() / actualSize.width, availableSize.height.toDouble() / actualSize.height)
    return if (scale <= 1.0) scale else floor(scale)
  }

  private fun computeScaledSize(scale: Double, rotation: SkinRotation): Dimension {
    return computeActualSize(rotation).scaled(scale)
  }

  private fun computeActualSize(rotation: SkinRotation): Dimension {
    val skin = emulator.skinDefinition
    return if (skin != null && deviceFrameVisible) {
      skin.getRotatedFrameSize(rotation, emulator.emulatorConfig.displaySize)
    }
    else {
      computeRotatedDisplaySize(emulatorConfig, rotation)
    }
  }

  private fun computeRotatedDisplaySize(config: EmulatorConfiguration, rotation: SkinRotation): Dimension {
    return if (rotation.is90Degrees) {
      Dimension(config.displayHeight, config.displayWidth)
    }
    else {
      Dimension(config.displayWidth, config.displayHeight)
    }
  }

  /**
   * Processes a focus traversal key event by passing it to the keyboard focus manager.
   */
  private fun traverseFocusLocally(event: KeyEvent) {
    if (!focusTraversalKeysEnabled) {
      focusTraversalKeysEnabled = true
      try {
        getCurrentKeyboardFocusManager().processKeyEvent(this, event)
      }
      finally {
        focusTraversalKeysEnabled = false
      }
    }
  }

  private fun sendMouseEvent(x: Int, y: Int, button: Int) {
    val displayRect = this.displayRectangle ?: return // Null displayRectangle means that Emulator screen is not displayed.
    val physicalX = x * screenScale // Coordinate is physical screen pixels.
    val physicalY = y * screenScale
    if (!displayRect.contains(physicalX, physicalY)) {
      return // Outside of the display rectangle.
    }
    val normalizedX = (physicalX - displayRect.x) / displayRect.width - 0.5  // X relative to display center in [-0.5, 0.5) range.
    val normalizedY = (physicalY - displayRect.y) / displayRect.height - 0.5 // Y relative to display center in [-0.5, 0.5) range.
    val deviceDisplayWidth = emulatorConfig.displayWidth
    val deviceDisplayHeight = emulatorConfig.displayHeight
    val displayX: Int
    val displayY: Int
    when (screenshotShape.rotation) {
      SkinRotation.PORTRAIT -> {
        displayX = ((0.5 + normalizedX) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 + normalizedY) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.LANDSCAPE -> {
        displayX = ((0.5 - normalizedY) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 + normalizedX) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.REVERSE_PORTRAIT -> {
        displayX = ((0.5 - normalizedX) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 - normalizedY) * deviceDisplayHeight).roundToInt()
      }
      SkinRotation.REVERSE_LANDSCAPE -> {
        displayX = ((0.5 + normalizedY) * deviceDisplayWidth).roundToInt()
        displayY = ((0.5 - normalizedX) * deviceDisplayHeight).roundToInt()
      }
      else -> {
        return
      }
    }
    val mouseEvent = MouseEventMessage.newBuilder()
      .setX(displayX.coerceIn(0, deviceDisplayWidth))
      .setY(displayY.coerceIn(0, deviceDisplayHeight))
      .setButtons(button)
      .build()
    emulator.sendMouse(mouseEvent)
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    invokeLaterInAnyModalityState {
      updateConnectionState(connectionState)
    }
  }

  private fun updateConnectionState(connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      remove(disconnectedStateLabel)
      if (isVisible) {
        if (screenshotFeed == null) {
          requestScreenshotFeed()
        }
        if (isFocusOwner) {
          setDeviceClipboardAndListenToChanges()
        }
        if (notificationFeed == null) {
          requestNotificationFeed()
        }
      }
    }
    else if (connectionState == ConnectionState.DISCONNECTED) {
      screenshotImage = null
      hideLongRunningOperationIndicator()
      disconnectedStateLabel.text = "Disconnected from the Emulator"
      add(disconnectedStateLabel)
    }

    revalidate()
    repaint()
  }

  private fun setDeviceClipboardAndListenToChanges() {
    val text = getClipboardText()
    if (text.isEmpty()) {
      requestClipboardFeed()
    }
    else {
      emulator.setClipboard(ClipData.newBuilder().setText(text).build(), object : EmptyStreamObserver<Empty>() {
        override fun onCompleted() {
          requestClipboardFeed()
        }

        override fun onError(t: Throwable) {
          if (t is StatusRuntimeException && t.status.code == Status.Code.UNIMPLEMENTED) {
            notifyEmulatorIsOutOfDate()
          }
        }
      })
    }
  }

  private fun getClipboardText(): String {
    val synchronizer = ClipboardSynchronizer.getInstance()
    return if (synchronizer.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
      synchronizer.getData(DataFlavor.stringFlavor) as String? ?: ""
    }
    else {
      ""
    }
  }

  private fun notifyEmulatorIsOutOfDate() {
    if (emulatorOutOfDateNotificationShown) {
      return
    }
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this)) ?: return
    val title = "Emulator is out of date"
    val message = "Please update the Android Emulator"
    val notification =
        EMULATOR_NOTIFICATION_GROUP.createNotification(title, XmlStringUtil.wrapInHtml(message), NotificationType.WARNING, null)
    notification.collapseActionsDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
    notification.addAction(object : NotificationAction("Check for updates") {
      override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        notification.expire()
        val action = ActionManager.getInstance().getAction("CheckForUpdate")
        ActionUtil.performActionDumbAware(action, event)
      }
    })
    notification.notify(project)
    emulatorOutOfDateNotificationShown = true
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val displayImage = screenshotImage ?: return
    val skin = skinLayout ?: return
    assert(screenshotShape.width != 0)
    assert(screenshotShape.height != 0)
    val displayRect = computeDisplayRectangle(skin)
    displayRectangle = displayRect

    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw display.
    displayTransform.setToTranslation(displayRect.x.toDouble(), displayRect.y.toDouble())
    displayTransform.scale(displayRect.width.toDouble() / screenshotShape.width, displayRect.height.toDouble() / screenshotShape.height)
    g.drawImage(displayImage, displayTransform, null)

    if (deviceFrameVisible) {
      // Draw device frame and mask.
      skin.drawFrameAndMask(g, displayRect)
    }
  }

  private fun computeDisplayRectangle(skin: SkinLayout): Rectangle {
    // The roundSlightly call below is used to avoid scaling by a factor that only slightly differs from 1.
    return if (deviceFrameVisible) {
      val frameRectangle = skin.frameRectangle
      val scale = roundSlightly(min(realWidth.toDouble() / frameRectangle.width, realHeight.toDouble() / frameRectangle.height))
      val fw = frameRectangle.width.scaled(scale)
      val fh = frameRectangle.height.scaled(scale)
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((realWidth - fw) / 2 - frameRectangle.x.scaled(scale), (realHeight - fh) / 2 - frameRectangle.y.scaled(scale), w, h)
    }
    else {
      val scale = roundSlightly(min(realWidth.toDouble() / screenshotShape.width, realHeight.toDouble() / screenshotShape.height))
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((realWidth - w) / 2, (realHeight - h) / 2, w, h)
    }
  }

  /** Rounds the given value to a multiple of 1.0/128. */
  private fun roundSlightly(value: Double): Double {
    return round(value * 128) / 128
  }

  private fun requestScreenshotFeed() {
    requestScreenshotFeed(screenshotShape.rotation)
  }

  private fun requestScreenshotFeed(rotation: SkinRotation) {
    cancelScreenshotFeed()
    if (width != 0 && height != 0 && connected) {
      if (screenshotImage == null) {
        screenshotShape = DisplayShape(0, 0, emulator.emulatorConfig.initialOrientation)
      }
      val rotatedDisplaySize = computeRotatedDisplaySize(emulatorConfig, rotation)
      val actualSize = computeActualSize(rotation)

      // Limit the size of the received screenshots to the size of the device display to avoid wasting gRPC resources.
      val scaleX = (realSize.width.toDouble() / actualSize.width).coerceAtMost(1.0)
      val scaleY = (realSize.height.toDouble() / actualSize.height).coerceAtMost(1.0)
      val w = rotatedDisplaySize.width.scaledDown(scaleX)
      val h = rotatedDisplaySize.height.scaledDown(scaleY)

      val imageFormat = ImageFormat.newBuilder()
        .setFormat(ImageFormat.ImgFormat.RGB888)
        .setWidth(w)
        .setHeight(h)
        .build()
      val receiver = ScreenshotReceiver(rotation, screenshotShape)
      screenshotReceiver = receiver
      screenshotFeed = emulator.streamScreenshot(imageFormat, receiver)
    }
  }

  private fun cancelScreenshotFeed() {
    screenshotReceiver = null
    screenshotFeed?.cancel()
    screenshotFeed = null
  }

  private fun requestClipboardFeed() {
    cancelClipboardFeed()
    if (connected) {
      val receiver = ClipboardReceiver()
      clipboardReceiver = receiver
      clipboardFeed = emulator.streamClipboard(receiver)
    }
  }

  private fun cancelClipboardFeed() {
    clipboardReceiver = null
    clipboardFeed?.cancel()
    clipboardFeed = null
  }

  private fun requestNotificationFeed() {
    if (!StudioFlags.EMBEDDED_EMULATOR_VIRTUAL_SCENE_CAMERA.get()) {
      return
    }
    cancelNotificationFeed()
    if (connected) {
      val receiver = NotificationReceiver()
      notificationReceiver = receiver
      notificationFeed = emulator.streamNotification(receiver)
    }
  }

  private fun cancelNotificationFeed() {
    notificationReceiver = null
    notificationFeed?.cancel()
    notificationFeed = null
  }

  override fun componentResized(event: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentShown(event: ComponentEvent) {
    requestScreenshotFeed()
    requestNotificationFeed()
  }

  override fun componentHidden(event: ComponentEvent) {
    cancelClipboardFeed()
  }

  override fun componentMoved(event: ComponentEvent) {
  }

  fun showLongRunningOperationIndicator(text: String) {
    findLoadingPanel()?.apply {
      setLoadingText(text)
      startLoading()
    }
  }

  fun hideLongRunningOperationIndicator() {
    findLoadingPanel()?.stopLoading()
  }

  fun hideLongRunningOperationIndicatorInstantly() {
    findLoadingPanel()?.stopLoadingInstantly()
  }

  private fun showVirtualSceneCameraPrompt() {
    findNotificationHolderPanel()?.showNotification("Hold Shift to control camera")
  }

  private fun hideVirtualSceneCameraPrompt() {
    findNotificationHolderPanel()?.hideNotification()
  }

  private fun startOperatingVirtualSceneCamera() {
    findNotificationHolderPanel()?.showNotification("Move camera with WASDQE keys, rotate with mouse or arrow keys")
    val disposable = Disposer.newDisposable("Virtual scene camera operation")
    virtualSceneCameraOperatingDisposable = disposable
    virtualSceneCameraOrientation.move(0, 0)
    val glass = IdeGlassPaneUtil.find(this)
    val cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.MOVE)
    val rootPane = glass.rootPane
    val scale = 360.0 * 5 / min(rootPane.width, rootPane.height)
    UIUtil.setCursor(rootPane, cursor)
    glass.setCursor(cursor, this)
    val point = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(point, rootPane)
    virtualSceneCameraReferencePoint.location = point.scaled(scale)
    val mouseListener = object: MouseAdapter() {

      override fun mouseMoved(event: MouseEvent) {
        virtualSceneCameraOrientation.move(
            event.x.scaled(scale) - virtualSceneCameraReferencePoint.x, event.y.scaled(scale) - virtualSceneCameraReferencePoint.y)
        val mouseEvent = MouseEventMessage.newBuilder()
          .setX(virtualSceneCameraOrientation.x)
          .setY(virtualSceneCameraOrientation.y)
          .build()
        emulator.sendMouse(mouseEvent)
        event.consume()
      }

      override fun mouseDragged(e: MouseEvent) {
        mouseMoved(e)
      }

      override fun mouseEntered(event: MouseEvent) {
        glass.setCursor(cursor, this)
      }
    }

    glass.addMousePreprocessor(mouseListener, disposable)
    glass.addMouseMotionPreprocessor(mouseListener, disposable)
  }

  private fun stopOperatingVirtualSceneCamera() {
    findNotificationHolderPanel()?.showNotification("Hold Shift to control camera")
    virtualSceneCameraOperatingDisposable?.let { Disposer.dispose(it) }
    val glass = IdeGlassPaneUtil.find(this)
    glass.setCursor(null, this)
    UIUtil.setCursor(glass.rootPane, null)
  }

  private val IdeGlassPane.rootPane
    get() = (this as IdeGlassPaneImpl).rootPane

  private fun findLoadingPanel(): EmulatorLoadingPanel? = findContainingComponent()

  private fun findNotificationHolderPanel(): NotificationHolderPanel? = findContainingComponent()

  private inline fun <reified T : JComponent> findContainingComponent(): T? {
    var component = parent
    while (component != null) {
      if (component is T) {
        return component
      }
      component = component.parent
    }
    return null
  }

  private inner class ClipboardReceiver : EmptyStreamObserver<ClipData>() {
    var responseCount = 0

    override fun onNext(response: ClipData) {
      if (clipboardReceiver != this) {
        return // This clipboard feed has already been cancelled.
      }

      // Skip the first response that reflects the current clipboard state.
      if (responseCount != 0 && response.text.isNotEmpty()) {
        invokeLaterInAnyModalityState {
          val content = StringSelection(response.text)
          ClipboardSynchronizer.getInstance().setContent(content, content)
        }
      }
      responseCount++
    }
  }

  private inner class NotificationReceiver : EmptyStreamObserver<EmulatorNotification>() {

    override fun onNext(response: EmulatorNotification) {
      if (EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS.get()) {
        LOG.info("Notification ${response.event}")
      }

      if (notificationReceiver != this) {
        return // This notification feed has already been cancelled.
      }

      invokeLaterInAnyModalityState {
        when (response.event) {
          VIRTUAL_SCENE_CAMERA_ACTIVE -> virtualSceneCameraActive = true
          VIRTUAL_SCENE_CAMERA_INACTIVE -> virtualSceneCameraActive = false
          else -> {}
        }
      }
    }
  }

  private inner class ScreenshotReceiver(
    val rotation: SkinRotation,
    val currentScreenshotShape: DisplayShape
  ) : EmptyStreamObserver<ImageMessage>() {
    private var cachedImageSource: MemoryImageSource? = null
    private val screenshotForSkinUpdate = AtomicReference<Screenshot>()
    private val screenshotForDisplay = AtomicReference<Screenshot>()

    override fun onNext(response: ImageMessage) {
      if (EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.get()) {
        val latency = System.currentTimeMillis() - response.timestampUs / 1000
        LOG.info("Screenshot ${response.seq} ${response.format.width}x${response.format.height} ${response.format.rotation.rotation} " +
                 "$latency ms latency")
      }
      if (screenshotReceiver != this) {
        return // This screenshot feed has already been cancelled.
      }

      if (response.format.width == 0 || response.format.height == 0) {
        return // Ignore empty screenshot.
      }

      val screenshot = Screenshot(response)

      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (screenshot.rotation != rotation) {
        invokeLaterInAnyModalityState {
          requestScreenshotFeed(screenshot.rotation)
        }
        return
      }

      if (screenshot.shape == currentScreenshotShape) {
        updateDisplayImageAsync(screenshot)
      }
      else {
        updateSkinAndDisplayImageAsync(screenshot)
      }
    }

    private fun updateSkinAndDisplayImageAsync(screenshot: Screenshot) {
      screenshotForSkinUpdate.set(screenshot)

      executeOnPooledThread {
        // If the screenshot feed has not been cancelled, update the skin and the display image.
        if (screenshotReceiver == this) {
          updateSkinAndDisplayImage()
        }
      }
    }

    @Slow
    private fun updateSkinAndDisplayImage() {
      val screenshot = screenshotForSkinUpdate.getAndSet(null) ?: return
      screenshot.skinLayout = emulator.skinDefinition?.createScaledLayout(screenshot.width, screenshot.height, screenshot.rotation) ?:
                              SkinLayout(Dimension(screenshot.width, screenshot.height))
      updateDisplayImageAsync(screenshot)
    }

    private fun updateDisplayImageAsync(screenshot: Screenshot) {
      screenshotForDisplay.set(screenshot)

      invokeLaterInAnyModalityState {
        // If the screenshot feed has not been cancelled, update the display image.
        if (screenshotReceiver == this) {
          updateDisplayImage()
        }
      }
    }

    @UiThread
    private fun updateDisplayImage() {
      hideLongRunningOperationIndicatorInstantly()

      val screenshot = screenshotForDisplay.getAndSet(null) ?: return
      val w = screenshot.width
      val h = screenshot.height

      val layout = screenshot.skinLayout
      if (layout != null) {
        skinLayout = layout
      }
      if (skinLayout == null) {
        // Create a skin layout without a device frame.
        skinLayout = SkinLayout(Dimension(w, h))
      }

      var imageSource = cachedImageSource
      if (imageSource == null || screenshotShape.width != w || screenshotShape.height != h) {
        imageSource = MemoryImageSource(w, h, screenshot.pixels, 0, w)
        imageSource.setAnimated(true)
        screenshotImage = createImage(imageSource)
        screenshotShape = screenshot.shape
        cachedImageSource = imageSource
      }
      else {
        imageSource.newPixels(screenshot.pixels, ColorModel.getRGBdefault(), 0, w)
      }

      frameNumber++
      frameTimestampMillis = System.currentTimeMillis()
      repaint()
    }
  }

  private class Screenshot(emulatorImage: ImageMessage) {
    val shape: DisplayShape
    val pixels: IntArray
    var skinLayout: SkinLayout? = null
    val width: Int
      get() = shape.width
    val height: Int
      get() = shape.height
    val rotation: SkinRotation
      get() = shape.rotation

    init {
      val format = emulatorImage.format
      shape = DisplayShape(format.width, format.height, format.rotation.rotation)
      pixels = getPixels(emulatorImage.image, width, height)
    }

    private fun getPixels(imageBytes: ByteString, width: Int, height: Int): IntArray {
      val pixels = IntArray(width * height)
      val byteIterator = imageBytes.iterator()
      val alpha = 0xFF shl 24
      for (i in pixels.indices) {
        val red = byteIterator.nextByte().toInt() and 0xFF
        val green = byteIterator.nextByte().toInt() and 0xFF
        val blue = byteIterator.nextByte().toInt() and 0xFF
        pixels[i] = alpha or (red shl 16) or (green shl 8) or blue
      }
      return pixels
    }
  }

  private data class DisplayShape(val width: Int, val height: Int, val rotation: SkinRotation)
}

private var emulatorOutOfDateNotificationShown = false

private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES = 5
private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_PIXELS = VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES * 5

private const val MAX_SCALE = 2.0 // Zoom above 200% is not allowed.

private val ZOOM_LEVELS = intArrayOf(5, 10, 25, 50, 100, 200) // In percent.

private val LOG = Logger.getInstance(EmulatorView::class.java)
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

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.emulator.ImageConverter
import com.android.emulator.control.DisplayModeValue
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.KeyboardEvent.KeyEventType
import com.android.emulator.control.Notification.EventType.DISPLAY_CONFIGURATIONS_CHANGED_UI
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_ACTIVE
import com.android.emulator.control.Notification.EventType.VIRTUAL_SCENE_CAMERA_INACTIVE
import com.android.emulator.control.Rotation.SkinRotation
import com.android.emulator.control.RotationRadian
import com.android.emulator.control.Touch
import com.android.emulator.control.Touch.EventExpiration.NEVER_EXPIRE
import com.android.emulator.control.TouchEvent
import com.android.emulator.control.WheelEvent
import com.android.ide.common.util.Cancelable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.analytics.toProto
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS
import com.android.tools.idea.io.grpc.stub.StreamObserver
import com.android.tools.idea.protobuf.TextFormat.shortDebugString
import com.android.tools.idea.streaming.AbstractDisplayView
import com.android.tools.idea.streaming.EmulatorSettings
import com.android.tools.idea.streaming.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.RUNNING_DEVICES_NOTIFICATION_GROUP
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration.DisplayMode
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.streaming.isSameAspectRatio
import com.android.tools.idea.streaming.rotatedByQuadrants
import com.android.tools.idea.streaming.scaled
import com.android.tools.idea.streaming.scaledDown
import com.android.tools.idea.streaming.scaledUnbiased
import com.google.protobuf.TextFormat.shortDebugString
import com.intellij.ide.DataManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.util.Alarm
import com.intellij.util.SofterReference
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import org.HdrHistogram.Histogram
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.color.ColorSpace
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.KEY_PRESSED
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
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_TAB
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.event.MouseEvent.BUTTON2
import java.awt.event.MouseEvent.BUTTON3
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.util.concurrent.atomic.AtomicReference
import javax.swing.KeyStroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import com.android.emulator.control.Image as ImageMessage
import com.android.emulator.control.MouseEvent as MouseEventMessage
import com.android.emulator.control.Notification as EmulatorNotification

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param disposableParent the disposable parent controlling the lifetime of this view
 * @param emulator the handle of the Emulator
 * @param displayId the ID of the device display
 * @param displaySize the size of the device display; a null value defaults to `emulator.emulatorConfig.displaySize`
 * @param deviceFrameVisible controls visibility of the device frame
 */
class EmulatorView(
  disposableParent: Disposable,
  val emulator: EmulatorController,
  displayId: Int,
  private val displaySize: Dimension?,
  deviceFrameVisible: Boolean
) : AbstractDisplayView(displayId), ConnectionStateListener, Disposable {

  override var displayOrientationQuadrants: Int
    get() = screenshotShape.orientation
    internal set(value) {
      if (value != screenshotShape.orientation && deviceFrameVisible) {
        requestScreenshotFeed(currentDisplaySize, value)
      }
    }

  private var lastScreenshot: Screenshot? = null
  private val displayTransform = AffineTransform()
  private val screenshotShape: DisplayShape
    get() = lastScreenshot?.displayShape ?: DisplayShape(0, 0, initialOrientation)
  private val initialOrientation: Int
    get() = if (displayId == PRIMARY_DISPLAY_ID) emulatorConfig.initialOrientation.number else SkinRotation.PORTRAIT.number
  private val currentDisplaySize: Dimension
    get() = screenshotShape.activeDisplayRegion?.size ?: deviceDisplaySize
  private val deviceDisplayRegion: Rectangle
    get() = screenshotShape.activeDisplayRegion ?: Rectangle(deviceDisplaySize)
  internal val displayMode: DisplayMode?
    get() = screenshotShape.displayMode ?: emulatorConfig.displayModes.firstOrNull()
  override val deviceDisplaySize: Dimension
    get() = displaySize ?: emulatorConfig.displaySize
  override val deviceSerialNumber: String
    get() = emulator.emulatorId.serialNumber

  @get:VisibleForTesting
  var frameTimestampMillis = 0L
    private set
  private var receivedFrameCount: Int = 0
  /** Time of the last frame update in milliseconds since epoch. */

  private var screenshotFeed: Cancelable? = null
  @Volatile
  private var screenshotReceiver: ScreenshotReceiver? = null

  private var notificationFeed: Cancelable? = null
  @Volatile
  private var notificationReceiver: NotificationReceiver? = null

  private var mouseWheelSender: StreamObserver<WheelEvent>? = null

  private val displayConfigurationListeners: MutableList<DisplayConfigurationListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  var deviceFrameVisible: Boolean = deviceFrameVisible
    set(value) {
      if (field != value) {
        field = value
        requestScreenshotFeed()
      }
    }

  private val isConnected
    get() = emulator.connectionState == ConnectionState.CONNECTED
  private val emulatorConfig
    get() = emulator.emulatorConfig

  /**
   * The size of the device including frame in device pixels.
   */
  val displaySizeWithFrame: Dimension
    get() = computeActualSize(screenshotShape.orientation)

  private var multiTouchMode = false
    set(value) {
      if (value != field) {
        if (!value) {
          mouseListener.terminateDragging()
        }
        field = value
        repaint()
      }
    }

  private val mouseListener = MyMouseListener()

  /**
   * Last coordinates of the mouse pointer while the first button was pressed.
   * Set to null when the first mouse button is released.
   */
  private var lastTouchCoordinates: Point? = null

  private var virtualSceneCameraActive = false
    set(value) {
      if (value != field) {
        field = value
        if (value) {
          multiTouchMode = false
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

  private var virtualSceneCameraVelocityController: VirtualSceneCameraVelocityController? = null
  private val stats = if (StudioFlags.EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS.get()) Stats() else null

  init {
    Disposer.register(disposableParent, this)

    emulator.addConnectionStateListener(this)
    addComponentListener(object : ComponentAdapter() {
      override fun componentShown(event: ComponentEvent) {
        requestScreenshotFeed()
        if (displayId == PRIMARY_DISPLAY_ID) {
          requestNotificationFeed()
        }
      }
    })

    // Forward mouse & keyboard events.
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)
    addMouseWheelListener(mouseListener)

    addKeyListener(MyKeyListener())

    if (displayId == PRIMARY_DISPLAY_ID) {
      showLongRunningOperationIndicator("Connecting to the Emulator")

      addFocusListener(object : FocusListener {
        override fun focusGained(event: FocusEvent) {
          if (virtualSceneCameraActive) {
            showVirtualSceneCameraPrompt()
          }
        }

        override fun focusLost(event: FocusEvent) {
          hideVirtualSceneCameraPrompt()
        }
      })
    }

    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { lafManager ->
      if (isConnected) {
        emulator.setUiTheme(getEmulatorUiTheme(lafManager))
      }
    })

    updateConnectionState(emulator.connectionState)
  }

  override fun dispose() {
    mouseWheelSender?.onCompleted()
    mouseWheelSender = null
    cancelNotificationFeed()
    cancelScreenshotFeed()
    emulator.removeConnectionStateListener(this)
    stats?.let { Disposer.dispose(it) } // The stats object has to be disposed last.
  }

  fun addDisplayConfigurationListener(listener: DisplayConfigurationListener) {
    displayConfigurationListeners.add(listener)
  }

  fun removeDisplayConfigurationListener(listener: DisplayConfigurationListener) {
    displayConfigurationListeners.remove(listener)
  }

  override fun canZoom(): Boolean = isConnected

  override fun computeActualSize(): Dimension =
    computeActualSize(screenshotShape.orientation)

  private fun computeActualSize(orientationQuadrants: Int): Dimension {
    val skin = emulator.skinDefinition
    return if (skin != null && deviceFrameVisible) {
      skin.getRotatedFrameSize(orientationQuadrants, currentDisplaySize)
    }
    else {
      currentDisplaySize.rotatedByQuadrants(orientationQuadrants)
    }
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val resized = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (resized) {
      requestScreenshotFeed()
    }
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
      updateConnectionState(connectionState)
    }
  }

  private fun updateConnectionState(connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      hideDisconnectedStateMessage()
      if (isVisible) {
        if (screenshotFeed == null) {
          requestScreenshotFeed()
        }
        if (displayId == PRIMARY_DISPLAY_ID) {
          if (notificationFeed == null) {
            requestNotificationFeed()
          }
        }
      }
    }
    else if (connectionState == ConnectionState.DISCONNECTED) {
      lastScreenshot = null
      showDisconnectedStateMessage("Disconnected from the Emulator")
    }

    repaint()
  }

  private fun notifyEmulatorIsOutOfDate() {
    if (emulatorOutOfDateNotificationShown) {
      return
    }
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this)) ?: return
    val title = "Emulator is out of date"
    val message = XmlStringUtil.wrapInHtml("Please update the Android Emulator")
    val notification = RUNNING_DEVICES_NOTIFICATION_GROUP.createNotification(title, message, NotificationType.WARNING)
    notification.collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
    notification.addAction(object : NotificationAction("Check for updates") {
      override fun actionPerformed(event: AnActionEvent, notification: Notification) {
        notification.expire()
        val action = ActionManager.getInstance().getAction("CheckForUpdate")
        action.actionPerformed(event)
      }
    })
    notification.notify(project)
    emulatorOutOfDateNotificationShown = true
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val screenshot = lastScreenshot ?: return
    val skin = screenshot.skinLayout
    assert(screenshotShape.width != 0)
    assert(screenshotShape.height != 0)
    val displayRect = computeDisplayRectangle(skin)
    displayRectangle = displayRect

    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw display.
    if (displayRect.width == screenshotShape.width && displayRect.height == screenshotShape.height) {
      g.drawImage(screenshot.image, null, displayRect.x, displayRect.y)
    }
    else {
      displayTransform.setToTranslation(displayRect.x.toDouble(), displayRect.y.toDouble())
      displayTransform.scale(displayRect.width.toDouble() / screenshotShape.width, displayRect.height.toDouble() / screenshotShape.height)
      g.drawImage(screenshot.image, displayTransform, null)
    }

    frameNumber = screenshotShape.frameNumber
    notifyFrameListeners(displayRect, screenshot.image)

    if (multiTouchMode) {
      drawMultiTouchFeedback(g, displayRect, lastTouchCoordinates != null)
    }

    if (deviceFrameVisible) {
      // Draw device frame and mask.
      skin.drawFrameAndMask(g, displayRect)
    }
    if (!screenshot.painted) {
      screenshot.painted = true
      val paintTime = System.currentTimeMillis()
      stats?.recordLatencyEndToEnd(paintTime - screenshot.frameOriginationTime)
    }
  }

  private fun computeDisplayRectangle(skin: SkinLayout): Rectangle {
    // The roundScale call below is used to avoid scaling by a fractional factor larger than 1 or
    // by a factor that is only slightly below 1.
    val maxSize = computeMaxImageSize()
    val maxWidth = maxSize.width
    val maxHeight = maxSize.height
    return if (deviceFrameVisible) {
      val frameRectangle = skin.frameRectangle
      val scale = roundScale(min(maxWidth.toDouble() / frameRectangle.width, maxHeight.toDouble() / frameRectangle.height))
      val fw = frameRectangle.width.scaled(scale)
      val fh = frameRectangle.height.scaled(scale)
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((physicalWidth - fw) / 2 - frameRectangle.x.scaled(scale), (physicalHeight - fh) / 2 - frameRectangle.y.scaled(scale),
                w, h)
    }
    else {
      val scale = roundScale(min(maxWidth.toDouble() / screenshotShape.width, maxHeight.toDouble() / screenshotShape.height))
      val w = screenshotShape.width.scaled(scale)
      val h = screenshotShape.height.scaled(scale)
      Rectangle((physicalWidth - w) / 2, (physicalHeight - h) / 2, w, h)
    }
  }

  private fun requestScreenshotFeed() {
    if (isConnected) {
      requestScreenshotFeed(currentDisplaySize, displayOrientationQuadrants)
    }
  }

  private fun requestScreenshotFeed(displaySize: Dimension, orientationQuadrants: Int) {
    if (width != 0 && height != 0 && isConnected) {
      val maxSize = physicalSize.rotatedByQuadrants(-orientationQuadrants)
      val skin = emulator.skinDefinition
      if (skin != null && deviceFrameVisible) {
        // Scale down to leave space for the device frame.
        val layout = skin.layout
        maxSize.width = maxSize.width.scaledDown(layout.displaySize.width, layout.frameRectangle.width)
        maxSize.height = maxSize.height.scaledDown(layout.displaySize.height, layout.frameRectangle.height)
      }

      // TODO: Remove the following three lines when b/238205075 is fixed.
      // Limit by the display resolution.
      maxSize.width = maxSize.width.coerceAtMost(displaySize.width)
      maxSize.height = maxSize.height.coerceAtMost(displaySize.height)

      val maxImageSize = maxSize.rotatedByQuadrants(orientationQuadrants)

      val currentReceiver = screenshotReceiver
      if (currentReceiver != null &&
          currentReceiver.maxImageSize == maxImageSize && currentReceiver.orientationQuadrants == orientationQuadrants) {
        return // Keep the current screenshot feed because it is identical.
      }

      cancelScreenshotFeed()
      val imageFormat = ImageFormat.newBuilder()
        .setDisplay(displayId)
        .setFormat(ImageFormat.ImgFormat.RGB888)
        .setWidth(maxImageSize.width)
        .setHeight(maxImageSize.height)
        .build()
      val receiver = ScreenshotReceiver(maxImageSize, orientationQuadrants)
      screenshotReceiver = receiver
      screenshotFeed = emulator.streamScreenshot(imageFormat, receiver)
    }
  }

  private fun cancelScreenshotFeed() {
    screenshotReceiver?.let { Disposer.dispose(it) }
    screenshotReceiver = null
    screenshotFeed?.cancel()
    screenshotFeed = null
  }

  private fun requestNotificationFeed() {
    cancelNotificationFeed()
    if (isConnected) {
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

  private fun showVirtualSceneCameraPrompt(prompt: String = "Hold Shift to control camera") {
    if (EmulatorSettings.getInstance().showCameraControlPrompts) {
      findNotificationHolderPanel()?.showNotification(prompt)
    }
  }

  private fun hideVirtualSceneCameraPrompt() {
    findNotificationHolderPanel()?.hideNotification()
  }

  private fun startOperatingVirtualSceneCamera() {
    val keys = EmulatorSettings.getInstance().cameraVelocityControls.keys
    showVirtualSceneCameraPrompt("Move camera with $keys keys, rotate with mouse or arrow keys")
    val glass = IdeGlassPaneUtil.find(this)
    val cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.MOVE)
    val rootPane = glass.rootPane
    val scale = PI / min(rootPane.width, rootPane.height)
    UIUtil.setCursor(rootPane, cursor)
    glass.setCursor(cursor, this)
    val referencePoint = MouseInfo.getPointerInfo().location
    val mouseListener = object: MouseAdapter() {

      override fun mouseMoved(event: MouseEvent) {
        rotateVirtualSceneCamera(-(event.yOnScreen - referencePoint.y) * scale, (referencePoint.x - event.xOnScreen) * scale)
        referencePoint.setLocation(event.xOnScreen, event.yOnScreen)
        event.consume()
      }

      override fun mouseDragged(e: MouseEvent) {
        mouseMoved(e)
      }

      override fun mouseEntered(event: MouseEvent) {
        glass.setCursor(cursor, this)
      }
    }

    val velocityController = VirtualSceneCameraVelocityController(emulator, EmulatorSettings.getInstance().cameraVelocityControls.keys)
    virtualSceneCameraVelocityController = velocityController
    glass.addMousePreprocessor(mouseListener, velocityController)
    glass.addMouseMotionPreprocessor(mouseListener, velocityController)
  }

  private fun stopOperatingVirtualSceneCamera() {
    virtualSceneCameraVelocityController?.let(Disposer::dispose)
    virtualSceneCameraVelocityController = null
    showVirtualSceneCameraPrompt()
    val glass = IdeGlassPaneUtil.find(this)
    glass.setCursor(null, this)
    UIUtil.setCursor(glass.rootPane, null)
  }

  private fun rotateVirtualSceneCamera(rotationX: Double, rotationY: Double) {
    val cameraRotation = RotationRadian.newBuilder()
      .setX(rotationX.toFloat())
      .setY(rotationY.toFloat())
      .build()
    emulator.rotateVirtualSceneCamera(cameraRotation)
  }

  private fun getButtonBit(button: Int): Int {
    return when(button) {
      BUTTON1 -> ANDROID_BUTTON1_BIT
      BUTTON2 -> ANDROID_BUTTON2_BIT
      BUTTON3 -> ANDROID_BUTTON3_BIT
      else -> 0
    }
  }

  internal fun displayModeChanged(displayModeId: DisplayModeValue) {
    val displayMode = emulatorConfig.displayModes.firstOrNull { it.displayModeId == displayModeId } ?: return
    requestScreenshotFeed(displayMode.displaySize, displayOrientationQuadrants)
  }

  private val IdeGlassPane.rootPane
    get() = (this as IdeGlassPaneImpl).rootPane

  private inner class NotificationReceiver : EmptyStreamObserver<EmulatorNotification>() {

    override fun onNext(response: EmulatorNotification) {
      if (EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS.get()) {
        LOG.info("Notification ${response.event}")
      }

      if (notificationReceiver != this) {
        return // This notification feed has already been cancelled.
      }

      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        when (response.event) {
          VIRTUAL_SCENE_CAMERA_ACTIVE -> virtualSceneCameraActive = true
          VIRTUAL_SCENE_CAMERA_INACTIVE -> virtualSceneCameraActive = false
          DISPLAY_CONFIGURATIONS_CHANGED_UI -> notifyDisplayConfigurationListeners()
          else -> {}
        }
      }
    }

    private fun notifyDisplayConfigurationListeners() {
      for (listener in displayConfigurationListeners) {
        listener.displayConfigurationChanged()
      }
    }
  }

  private inner class MyKeyListener  : KeyAdapter() {

    private var cachedKeyStrokeMap: Map<KeyStroke, EmulatorKeyStroke>? = null
    private val keyStrokeMap: Map<KeyStroke, EmulatorKeyStroke>
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
        when (event.keyCode) {
          VK_LEFT, VK_KP_LEFT -> rotateVirtualSceneCamera(0.0, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_RIGHT, VK_KP_RIGHT -> rotateVirtualSceneCamera(0.0, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_UP, VK_KP_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
          VK_DOWN, VK_KP_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
          VK_HOME -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_END -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_PAGE_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          VK_PAGE_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
          else -> virtualSceneCameraVelocityController?.keyPressed(event.keyCode)
        }
        return
      }

      keyPressedOrReleased(event)
    }

    override fun keyReleased(event: KeyEvent) {
      if (event.keyCode == VK_SHIFT) {
        virtualSceneCameraOperating = false
      }

      virtualSceneCameraVelocityController?.keyReleased(event.keyCode)

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
      val emulatorKeyStroke = hostKeyStrokeToEmulatorKeyStroke(keyCode, modifiers)
      if (emulatorKeyStroke == null) {
        val keyName = hostKeyCodeToEmulatorKeyName(keyCode)
        if (keyName != null) {
          emulator.pressModifierKeys(modifiers)
          val eventType = if (event.id == KEY_PRESSED) KeyEventType.keydown else KeyEventType.keyup
          emulator.sendKey(createKeyboardEvent(keyName, eventType))
          emulator.releaseModifierKeys(modifiers)
        }
      }
      else if (event.id == KEY_PRESSED) {
        emulator.pressModifierKeys(emulatorKeyStroke.modifiers)
        emulator.sendKey(KeyboardEvent.newBuilder().setKey(emulatorKeyStroke.keyName).setEventType(KeyEventType.keypress).build())
        emulator.releaseModifierKeys(emulatorKeyStroke.modifiers)
      }
      event.consume()
    }

    private fun hostKeyStrokeToEmulatorKeyStroke(hostKeyCode: Int, modifiers: Int): EmulatorKeyStroke? {
      val canonicalKeyCode = when (hostKeyCode) {
        VK_KP_LEFT -> VK_LEFT
        VK_KP_RIGHT -> VK_RIGHT
        VK_KP_UP -> VK_UP
        VK_KP_DOWN -> VK_DOWN
        else -> hostKeyCode
      }

      return keyStrokeMap[KeyStroke.getKeyStroke(canonicalKeyCode, modifiers)]
    }

    private fun hostKeyCodeToEmulatorKeyName(hostKeyCode: Int): String? {
      return when (hostKeyCode) {
        VK_BACK_SPACE -> "Backspace"
        VK_DELETE -> if (SystemInfo.isMac) "Backspace" else "Delete"
        VK_ENTER -> "Enter"
        VK_ESCAPE -> "Escape"
        VK_TAB -> "Tab"
        else -> return null
      }
    }

    private fun buildKeyStrokeMap(): Map<KeyStroke, EmulatorKeyStroke> {
      return mutableMapOf<KeyStroke, EmulatorKeyStroke>().apply {
        addKeystrokesForAction(IdeActions.ACTION_CUT, EmulatorKeyStroke("Cut"))
        addKeystrokesForAction(IdeActions.ACTION_COPY, EmulatorKeyStroke("Copy"))
        addKeystrokesForAction(IdeActions.ACTION_PASTE, EmulatorKeyStroke("Paste"))
        addKeystrokesForAction(IdeActions.ACTION_SELECT_ALL, EmulatorKeyStroke("KeyA", CTRL_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, EmulatorKeyStroke("ArrowLeft"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, EmulatorKeyStroke("ArrowRight"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, EmulatorKeyStroke("ArrowLeft", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, EmulatorKeyStroke("ArrowRight", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, EmulatorKeyStroke("ArrowUp"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, EmulatorKeyStroke("ArrowDown"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION, EmulatorKeyStroke("ArrowUp", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION, EmulatorKeyStroke("ArrowDown", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD, EmulatorKeyStroke("ArrowLeft", CTRL_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_NEXT_WORD, EmulatorKeyStroke("ArrowRight", CTRL_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_PREVIOUS_WORD_WITH_SELECTION, EmulatorKeyStroke("ArrowLeft", CTRL_SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_NEXT_WORD_WITH_SELECTION, EmulatorKeyStroke("ArrowRight", CTRL_SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START, EmulatorKeyStroke("Home"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END, EmulatorKeyStroke("End"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, EmulatorKeyStroke("Home", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, EmulatorKeyStroke("End", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP, EmulatorKeyStroke("PageUp"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN, EmulatorKeyStroke("PageDown"))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION, EmulatorKeyStroke("PageUp", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION, EmulatorKeyStroke("PageDown", SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_TEXT_START, EmulatorKeyStroke("Home", CTRL_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_TEXT_END, EmulatorKeyStroke("End", CTRL_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION, EmulatorKeyStroke("Home", CTRL_SHIFT_DOWN_MASK))
        addKeystrokesForAction(IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION, EmulatorKeyStroke("End", CTRL_SHIFT_DOWN_MASK))
      }
    }

    private fun MutableMap<KeyStroke, EmulatorKeyStroke>.addKeystrokesForAction(actionId: String, androidKeystroke: EmulatorKeyStroke) {
      for (keyStroke in KeymapUtil.getKeyStrokes(KeymapUtil.getActiveKeymapShortcuts(actionId))) {
        put(keyStroke, androidKeystroke)
      }
    }
  }

  private inner class MyMouseListener : MouseAdapter() {

    /** A bit set indicating the current pressed buttons. */
    private var buttons = 0

    override fun mousePressed(event: MouseEvent) {
      requestFocusInWindow()
      buttons = buttons or getButtonBit(event.button)
      if (isInsideDisplay(event)) {
        lastTouchCoordinates = Point(event.x, event.y)
        updateMultiTouchMode(event)
        sendMouseEvent(event.x, event.y, buttons)
      }
    }

    override fun mouseReleased(event: MouseEvent) {
      buttons = buttons and getButtonBit(event.button).inv()
      if (event.button == BUTTON1) {
        lastTouchCoordinates = null
        updateMultiTouchMode(event)
      }
      sendMouseEvent(event.x, event.y, buttons)
    }

    override fun mouseEntered(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    override fun mouseExited(event: MouseEvent) {
      if (lastTouchCoordinates != null) {
        // Moving over the edge of the display view will terminate the ongoing dragging.
        sendMouseEvent(event.x, event.y, 0)
      }
      lastTouchCoordinates = null
      multiTouchMode = false
    }

    override fun mouseDragged(event: MouseEvent) {
      updateMultiTouchMode(event)
      if (!virtualSceneCameraOperating && lastTouchCoordinates != null) {
        sendMouseEvent(event.x, event.y, buttons, drag = true)
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      updateMultiTouchMode(event)
      if (!virtualSceneCameraOperating && !multiTouchMode) {
        sendMouseEvent(event.x, event.y, 0)
      }
    }

    override fun mouseWheelMoved(event: MouseWheelEvent) {
      if (event.wheelRotation == 0 || event.scrollType != WHEEL_UNIT_SCROLL) {
        return
      }
      // Multiplying wheelRotation by -1 because AWT assigns the opposite sign to Qt/Android.
      getOrCreateMouseWheelSender().onNext(WheelEvent.newBuilder().setDy(-event.wheelRotation * 120).build())
    }

    private fun getOrCreateMouseWheelSender(): StreamObserver<WheelEvent> {
      return mouseWheelSender ?: emulator.injectWheel().also { mouseWheelSender = it }
    }

    private fun updateMultiTouchMode(event: MouseEvent) {
      val oldMultiTouchMode = multiTouchMode
      multiTouchMode = (event.modifiersEx and CTRL_DOWN_MASK) != 0 && !virtualSceneCameraOperating
      if (multiTouchMode && oldMultiTouchMode) {
        repaint() // If multitouch mode changed above, the repaint method was already called.
      }
    }

    // Terminates ongoing dragging if any.
    fun terminateDragging() {
      lastTouchCoordinates?.let { sendMouseEvent(it.x, it.y, 0) }
    }

    private fun sendMouseEvent(x: Int, y: Int, buttons: Int, drag: Boolean = false) {
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

      val deviceDisplayRegion = deviceDisplayRegion

      // Device display coordinates.
      val displayX = normalizedX.scaledUnbiased(imageWidth, deviceDisplayRegion.width)
      val displayY = normalizedY.scaledUnbiased(imageHeight, deviceDisplayRegion.height)

      if (deviceDisplayRegion.contains(displayX, displayY)) {
        // Within the bounds of the device display.
        sendMouseOrTouchEvent(displayX, displayY, buttons, deviceDisplayRegion)
      }
      else if (drag) {
        // Crossed the device display boundary while dragging.
        lastTouchCoordinates = null
        val adjustedX = displayX.coerceIn(deviceDisplayRegion.x, deviceDisplayRegion.width - 1)
        val adjustedY = displayY.coerceIn(deviceDisplayRegion.y, deviceDisplayRegion.height - 1)
        sendMouseOrTouchEvent(adjustedX, adjustedY, 0, deviceDisplayRegion)
      }
    }

    private fun sendMouseOrTouchEvent(displayX: Int, displayY: Int, buttons: Int, deviceDisplayRegion: Rectangle) {
      if (multiTouchMode) {
        val pressure = if (buttons == 0) 0 else PRESSURE_RANGE_MAX
        val touchEvent = TouchEvent.newBuilder()
          .setDisplay(displayId)
          .addTouches(createTouch(displayX, displayY, 0, pressure))
          .addTouches(
              createTouch(deviceDisplayRegion.width - 1 - displayX, deviceDisplayRegion.height - 1 - displayY, 1, pressure))
          .build()
        emulator.sendTouch(touchEvent)
      }
      else {
        val mouseEvent = MouseEventMessage.newBuilder()
          .setDisplay(displayId)
          .setX(displayX)
          .setY(displayY)
          .setButtons(buttons)
          .build()
        emulator.sendMouse(mouseEvent)
      }
    }

    private fun createTouch(x: Int, y: Int, identifier: Int, pressure: Int): Touch.Builder {
      return Touch.newBuilder()
        .setX(x)
        .setY(y)
        .setIdentifier(identifier)
        .setPressure(pressure)
        .setExpiration(NEVER_EXPIRE)
    }

    private fun isInsideDisplay(event: MouseEvent) =
      displayRectangle?.contains(event.x * screenScale, event.y * screenScale) ?: false
  }

  private inner class ScreenshotReceiver(
    val maxImageSize: Dimension,
    val orientationQuadrants: Int
  ) : EmptyStreamObserver<ImageMessage>(), Disposable {
    private val screenshotForProcessing = AtomicReference<Screenshot?>()
    private val screenshotForDisplay = AtomicReference<Screenshot?>()
    private val skinLayoutCache = SkinLayoutCache(emulator)
    private val recycledImage = AtomicReference<SofterReference<BufferedImage>?>()
    private val alarm = Alarm(this)
    private var expectedFrameNumber = -1

    override fun onNext(response: ImageMessage) {
      val arrivalTime = System.currentTimeMillis()
      val imageFormat = response.format
      val imageRotation = imageFormat.rotation.rotation.number
      val frameOriginationTime: Long = response.timestampUs / 1000
      val displayMode: DisplayMode? = emulatorConfig.displayModes.firstOrNull { it.displayModeId == imageFormat.displayMode }

      if (EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.get()) {
        val latency = arrivalTime - frameOriginationTime
        val foldedState = if (imageFormat.hasFoldedDisplay()) " foldedDisplay={${shortDebugString(imageFormat.foldedDisplay)}}" else ""
        val mode = if (emulatorConfig.displayModes.size > 1) " ${imageFormat.displayMode}" else ""
        LOG.info("Screenshot ${response.seq} ${imageFormat.width}x${imageFormat.height}$mode$foldedState ${imageRotation * 90}°" +
                 " $latency ms latency")
      }
      if (screenshotReceiver != this) {
        expectedFrameNumber++
        return // This screenshot feed has already been cancelled.
      }

      if (imageFormat.width == 0 || imageFormat.height == 0) {
        expectedFrameNumber++
        return // Ignore empty screenshot.
      }

      if (response.image.size() != imageFormat.width * imageFormat.height * 3) {
        LOG.error("Inconsistent ImageMessage: ${imageFormat.width}x${imageFormat.width} image contains ${response.image.size()} bytes" +
                  " instead of ${imageFormat.width * imageFormat.height * 3}")
        return
      }

      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (imageRotation != orientationQuadrants) {
        EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
          requestScreenshotFeed(currentDisplaySize, imageRotation)
        }
        expectedFrameNumber++
        return
      }
      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (imageFormat.displayMode != displayMode?.displayModeId) {
        if (displayMode != null) {
          EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
            requestScreenshotFeed(displayMode.displaySize, imageRotation)
          }
          expectedFrameNumber++
          return
        }
      }

      alarm.cancelAllRequests()
      val recycledImage = recycledImage.getAndSet(null)?.get()
      val image = if (recycledImage?.width == imageFormat.width && recycledImage.height == imageFormat.height) {
        val pixels = (recycledImage.raster.dataBuffer as DataBufferInt).data
        ImageConverter.unpackRgb888(response.image, pixels)
        recycledImage
      }
      else {
        val pixels = IntArray(imageFormat.width * imageFormat.height)
        ImageConverter.unpackRgb888(response.image, pixels)
        val buffer = DataBufferInt(pixels, pixels.size)
        val sampleModel = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, imageFormat.width, imageFormat.height, SAMPLE_MODEL_BIT_MASKS)
        val raster = Raster.createWritableRaster(sampleModel, buffer, ZERO_POINT)
        @Suppress("UndesirableClassUsage")
        BufferedImage(COLOR_MODEL, raster, false, null)
      }

      val lostFrames = if (expectedFrameNumber > 0) response.seq - expectedFrameNumber else 0
      stats?.recordFrameArrival(arrivalTime - frameOriginationTime, lostFrames, imageFormat.width * imageFormat.height)
      expectedFrameNumber = response.seq + 1

      if (displayMode != null && !checkAspectRatioConsistency(imageFormat, displayMode)) {
        return
      }
      val foldedDisplay = imageFormat.foldedDisplay
      val activeDisplayRegion = when {
        foldedDisplay.width != 0 && foldedDisplay.height != 0 ->
            Rectangle(foldedDisplay.xOffset, foldedDisplay.yOffset, foldedDisplay.width, foldedDisplay.height)
        displayMode != null -> Rectangle(displayMode.displaySize)
        else -> null
      }
      val displayShape = DisplayShape(imageFormat.width, imageFormat.height, imageRotation, activeDisplayRegion, displayMode, response.seq)
      val screenshot = Screenshot(displayShape, image, frameOriginationTime)
      val skinLayout = skinLayoutCache.getCached(displayShape)
      if (skinLayout == null) {
        computeSkinLayoutOnPooledThread(screenshot)
      }
      else {
        screenshot.skinLayout = skinLayout
        updateDisplayImageOnUiThread(screenshot)
      }
    }

    private fun checkAspectRatioConsistency(imageFormat: ImageFormat, displayMode: DisplayMode): Boolean {
      val imageAspectRatio = if (imageFormat.rotation.rotationValue % 2 == 0) imageFormat.width.toDouble() / imageFormat.height
                             else imageFormat.height.toDouble() / imageFormat.width
      val displayAspectRatio = displayMode.width.toDouble() / displayMode.height
      val tolerance = 1.0 / imageFormat.width + 1.0 / imageFormat.height
      if (abs(imageAspectRatio / displayAspectRatio - 1) > tolerance) {
        val imageDimensions = if (imageFormat.rotation.rotationValue % 2 == 0) "${imageFormat.width}x${imageFormat.height}"
                              else "${imageFormat.height}x${imageFormat.width}"
        LOG.error("Inconsistent ImageMessage: the $imageDimensions display image has different aspect ratio than" +
                  " the ${displayMode.width}x${displayMode.height} display")
        return false
      }
      return true
    }

    private fun computeSkinLayoutOnPooledThread(screenshotWithoutSkin: Screenshot) {
      screenshotForProcessing.set(screenshotWithoutSkin)

      executeOnPooledThread {
        // If the screenshot feed has not been cancelled, update the skin and the display image.
        if (screenshotReceiver == this) {
          val screenshot = screenshotForProcessing.getAndSet(null)
          if (screenshot == null) {
            stats?.recordDroppedFrame()
          }
          else {
            screenshot.skinLayout = skinLayoutCache.get(screenshot.displayShape)
            updateDisplayImageOnUiThread(screenshot)
          }
        }
      }
    }

    private fun updateDisplayImageOnUiThread(screenshot: Screenshot) {
      screenshotForDisplay.set(screenshot)

      EventQueue.invokeLater { // This is safe because this code doesn't touch PSI or VFS.
        // If the screenshot feed has not been cancelled, update the display image.
        if (screenshotReceiver == this) {
          updateDisplayImage()
        }
      }
    }

    @UiThread
    private fun updateDisplayImage() {
      hideLongRunningOperationIndicatorInstantly()

      val screenshot = screenshotForDisplay.getAndSet(null)
      if (screenshot == null) {
        stats?.recordDroppedFrame()
        return
      }

      // Creation of a large BufferedImage is expensive. Recycle the old image if it has the proper size.
      lastScreenshot?.image?.let {
        if (it.width == screenshot.displayShape.width && it.height == screenshot.displayShape.height) {
          recycledImage.set(SofterReference(it))
          alarm.cancelAllRequests()
          alarm.addRequest({ recycledImage.set(null) }, CACHED_IMAGE_LIVE_TIME_MILLIS, ModalityState.any())
        }
        else if (!isSameAspectRatio(it.width, it.height, screenshot.displayShape.width, screenshot.displayShape.height, 0.01)) {
          zoom(ZoomType.FIT) // Display dimensions changed - reset zoom level.
        }
      }

      val lastDisplayMode = lastScreenshot?.displayShape?.displayMode
      lastScreenshot = screenshot

      receivedFrameCount++
      frameTimestampMillis = System.currentTimeMillis()
      repaint()

      if (screenshot.displayShape.displayMode != lastDisplayMode) {
        firePropertyChange(DISPLAY_MODE_PROPERTY, lastDisplayMode, screenshot.displayShape.displayMode)
      }
    }

    override fun dispose() {
    }
  }

  private class Screenshot(val displayShape: DisplayShape, val image: BufferedImage, val frameOriginationTime: Long) {
    lateinit var skinLayout: SkinLayout
    var painted = false
  }

  /**
   * Stores the last computed scaled [SkinLayout] together with the corresponding display
   * dimensions and orientation.
   */
  private class SkinLayoutCache(val emulator: EmulatorController) {
    var displayShape: DisplayShape? = null
    var skinLayout: SkinLayout? = null

    fun getCached(displayShape: DisplayShape): SkinLayout? {
      synchronized(this) {
        return if (displayShape == this.displayShape) skinLayout else null
      }
    }

    @Slow
    fun get(displayShape: DisplayShape): SkinLayout {
      synchronized(this) {
        var layout = this.skinLayout
        if (displayShape != this.displayShape || layout == null) {
          layout = emulator.skinDefinition?.createScaledLayout(displayShape.width, displayShape.height, displayShape.orientation) ?:
                   SkinLayout(displayShape.width, displayShape.height)
          this.displayShape = displayShape
          this.skinLayout = layout
        }
        return layout
      }
    }
  }

  private data class DisplayShape(val width: Int,
                                  val height: Int,
                                  val orientation: Int,
                                  val activeDisplayRegion: Rectangle? = null,
                                  val displayMode: DisplayMode? = null,
                                  val frameNumber: Int = 0)

  private class Stats: Disposable {
    @GuardedBy("this")
    private var data = Data()
    private val alarm = Alarm(this)

    init {
      scheduleNextLogging()
    }

    @Synchronized
    fun recordFrameArrival(latencyOfArrival: Long, numberOfLostFrames: Int, numberOfPixels: Int) {
      data.frameCount += 1 + numberOfLostFrames
      data.pixelCount += (1 + numberOfLostFrames) * numberOfPixels
      data.latencyOfArrival.recordValue(latencyOfArrival)
      if (numberOfLostFrames != 0) {
        data.droppedFrameCount += numberOfLostFrames
        data.droppedFrameCountBeforeArrival += numberOfLostFrames
      }
    }

    @Synchronized
    fun recordDroppedFrame() {
      data.droppedFrameCount++
    }

    @Synchronized
    fun recordLatencyEndToEnd(latency: Long) {
      data.latencyEndToEnd.recordValue(latency)
    }

    @Synchronized
    override fun dispose() {
      data.log()
    }

    @Synchronized
    private fun getAndSetData(newData: Data): Data {
      val oldData = data
      data = newData
      return oldData
    }

    private fun scheduleNextLogging() {
      alarm.addRequest(::logAndReset, STATS_LOG_INTERVAL_MILLIS, ModalityState.any())
    }

    private fun logAndReset() {
      getAndSetData(Data()).log()
      scheduleNextLogging()
    }

    private class Data {
      var frameCount = 0
      var droppedFrameCount = 0
      var droppedFrameCountBeforeArrival = 0
      var pixelCount = 0L
      val latencyEndToEnd = Histogram(1)
      val latencyOfArrival = Histogram(1)
      val collectionStart = System.currentTimeMillis()

      fun log() {
        if (frameCount != 0) {
          val frameRate = String.format("%.2g", frameCount * 1000.0 / (System.currentTimeMillis() - collectionStart))
          val frameSize = (pixelCount.toDouble() / frameCount).roundToInt()
          val neverArrived = if (droppedFrameCountBeforeArrival != 0) " (${droppedFrameCountBeforeArrival} never arrived)" else ""
          val dropped = if (droppedFrameCount != 0) " dropped frames: $droppedFrameCount$neverArrived" else ""
          LOG.info("Frames: $frameCount $dropped average frame rate: $frameRate average frame size: $frameSize pixels\n" +
                   "latency: ${shortDebugString(latencyEndToEnd.toProto())}\n" +
                   "latency of arrival: ${shortDebugString(latencyOfArrival.toProto())}")
        }
      }
    }
  }
}

internal const val DISPLAY_MODE_PROPERTY = "displayMode"

private var emulatorOutOfDateNotificationShown = false

private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES = 5
private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN = VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES * PI / 180

private val ZERO_POINT = Point()
private const val ALPHA_MASK = 0xFF shl 24
private val SAMPLE_MODEL_BIT_MASKS = intArrayOf(0xFF0000, 0xFF00, 0xFF, ALPHA_MASK)
private val COLOR_MODEL = DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                           32, 0xFF0000, 0xFF00, 0xFF, ALPHA_MASK, false, DataBuffer.TYPE_INT)
private const val CACHED_IMAGE_LIVE_TIME_MILLIS = 2000

// Android (and the emulator gRPC) button bits corresponding to the AWT button definitions.
// The middle and the right buttons are ordered differently in Android compared to AWT.
private const val ANDROID_BUTTON1_BIT = 1 shl 0 // Left
private const val ANDROID_BUTTON2_BIT = 1 shl 2 // Middle
private const val ANDROID_BUTTON3_BIT = 1 shl 1 // Right

private const val CTRL_SHIFT_DOWN_MASK = CTRL_DOWN_MASK or SHIFT_DOWN_MASK

private val STATS_LOG_INTERVAL_MILLIS = StudioFlags.EMBEDDED_EMULATOR_STATISTICS_INTERVAL_SECONDS.get().toLong() * 1000

// The same as MTS_PRESSURE_RANGE_MAX defined in
// https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/android-emu/android/multitouch-screen.h
private const val PRESSURE_RANGE_MAX = 0x400

private val LOG = Logger.getInstance(EmulatorView::class.java)
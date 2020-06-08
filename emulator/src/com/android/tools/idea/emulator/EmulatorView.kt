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
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.Rotation.SkinRotation
import com.android.ide.common.util.Cancelable
import com.android.tools.adtui.Zoomable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS
import com.android.tools.idea.protobuf.ByteString
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager
import java.awt.Point
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_DELETE
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
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
import java.awt.event.MouseMotionAdapter
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.awt.image.MemoryImageSource
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.android.emulator.control.Image as ImageMessage
import com.android.emulator.control.MouseEvent as MouseEventMessage

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param emulator the handle of the Emulator
 * @param cropFrame if true, the device frame is cropped to maximize the size of the display image
 */
class EmulatorView(
  val emulator: EmulatorController,
  parentDisposable: Disposable,
  cropFrame: Boolean
) : JPanel(BorderLayout()), ComponentListener, ConnectionStateListener, Zoomable, Disposable {

  private var disconnectedStateLabel: JLabel
  private var screenshotFeed: Cancelable? = null
  private var displayImage: Image? = null
  private var displayWidth = 0
  private var displayHeight = 0
  private var skinLayout: SkinLayout? = null
  private var displayRotationInternal = SkinRotation.PORTRAIT
  private val displayTransform = AffineTransform()
  @Volatile
  private var screenshotReceiver: ScreenshotReceiver? = null
  /** Count of received display frames. */
  @VisibleForTesting
  var frameNumber = 0
    private set

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
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseDragged(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 1)
      }
    })

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 1)
      }

      override fun mouseReleased(event: MouseEvent) {
        sendMouseEvent(event.x, event.y, 0)
      }

      override fun mouseClicked(event: MouseEvent) {
        requestFocusInWindow()
      }
    })

    addKeyListener(object : KeyAdapter() {
      override fun keyTyped(event: KeyEvent) {
        val c = event.keyChar
        if (c == CHAR_UNDEFINED || Character.isISOControl(c)) {
          return
        }

        val keyboardEvent = KeyboardEvent.newBuilder().setText(c.toString()).build()
        emulator.sendKey(keyboardEvent)
      }

      override fun keyPressed(event: KeyEvent) {
        // The Tab character is passed to the emulator, but Shift+Tab is converted to Tab and processed locally.
        if (event.keyCode == VK_TAB && event.modifiersEx == SHIFT_DOWN_MASK) {
          val tabEvent = KeyEvent(event.source as Component, event.id, event.getWhen(), 0, event.keyCode, event.keyChar, event.keyLocation)
          traverseFocusLocally(tabEvent)
          return
        }

        if (event.modifiers != 0) {
          return
        }
        val keyName =
          when (event.keyCode) {
            VK_BACK_SPACE -> "Backspace"
            VK_DELETE -> if (SystemInfo.isMac) "Backspace" else "Delete"
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
    })

    updateConnectionState(emulator.connectionState)
  }

  var displayRotation: SkinRotation
    get() = displayRotationInternal
    set(value) {
      if (value != displayRotationInternal && !cropFrame) {
        requestScreenshotFeed(value)
      }
    }

  var cropFrame: Boolean = cropFrame
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
    get() = screenScale.toFloat()

  override val scale: Double
    get() = computeScaleToFit(realSize, displayRotationInternal)

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

  private val explicitlySetPreferredSize: Dimension?
    get() = if (isPreferredSizeSet) preferredSize else null

  /**
   * Computes the preferred size in virtual pixels after the given zoom operation.
   * The preferred size is null for zoom to fit.
   */
  private fun computeZoomedSize(zoomType: ZoomType): Dimension? {
    val newScale: Double
    when (zoomType) {
      ZoomType.IN -> {
        newScale = min(ZoomType.zoomIn((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0, MAX_SCALE)
      }
      ZoomType.OUT -> {
        newScale = max(ZoomType.zoomOut((scale * 100).roundToInt(), ZOOM_LEVELS) / 100.0, computeScaleToFitInParent())
      }
      ZoomType.ACTUAL -> {
        newScale = 1.0
      }
      ZoomType.FIT -> {
        return null
      }
      else -> throw IllegalArgumentException("Unsupported zoom type $zoomType")
    }
    val scaledSize = computeScaledSize(newScale, displayRotationInternal)
    val availableSize = computeAvailableSize()
    if (scaledSize.width <= availableSize.width && scaledSize.height <= availableSize.height) {
      return null
    }
    return scaledSize.scaled(1 / screenScale)
  }

  private fun computeScaleToFitInParent() = computeScaleToFit(computeAvailableSize(), displayRotationInternal)

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
    return if (cropFrame || skin == null) {
      computeRotatedDisplaySize(emulatorConfig, rotation)
    }
    else {
      skin.getRotatedFrameSize(rotation, emulator.emulatorConfig.displaySize)
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
    val skin = skinLayout ?: return // Null skinLayout means that Emulator screen is not displayed.
    val displayPosition = computeDisplayPosition(skin)
    val normalizedX = (x * screenScale - displayPosition.x) / displayWidth - 0.5  // X relative to display center in [-0.5, 0.5) range.
    val normalizedY = (y * screenScale - displayPosition.y) / displayHeight - 0.5 // Y relative to display center in [-0.5, 0.5) range.
    val deviceDisplayWidth = emulatorConfig.displayWidth
    val deviceDisplayHeight = emulatorConfig.displayHeight
    val displayX: Int
    val displayY: Int
    when (displayRotationInternal) {
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
      if (isVisible && screenshotFeed == null) {
        requestScreenshotFeed()
      }
    }
    else if (connectionState == ConnectionState.DISCONNECTED) {
      displayImage = null
      hideLongRunningOperationIndicator()
      disconnectedStateLabel.text = "Disconnected from the Emulator"
      add(disconnectedStateLabel)
    }
    revalidate()
    repaint()
  }

  private fun findLoadingPanel(): EmulatorLoadingPanel? {
    var component = parent
    while (component != null) {
      if (component is EmulatorLoadingPanel) {
        return component
      }
      component = component.parent
    }
    return null
  }

  override fun dispose() {
    screenshotFeed?.cancel()
    removeComponentListener(this)
    emulator.removeConnectionStateListener(this)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val displayImage = displayImage ?: return
    val skin = skinLayout ?: return
    val displayPosition = computeDisplayPosition(skin)

    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw display.
    displayTransform.setToTranslation(displayPosition.x.toDouble(), displayPosition.y.toDouble())
    g.drawImage(displayImage, displayTransform, null)

    skin.drawFrameAndMask(displayPosition.x, displayPosition.y, g)
  }

  private fun computeDisplayPosition(skin: SkinLayout): Point {
    return if (cropFrame) {
      Point((realWidth - displayWidth) / 2, (realHeight - displayHeight) / 2)
    }
    else {
      val frameRect = skin.frameRectangle
      Point((realWidth - frameRect.width) / 2 - frameRect.x, (realHeight - frameRect.height) / 2 - frameRect.y)
    }
  }

  private fun requestScreenshotFeed() {
    requestScreenshotFeed(displayRotationInternal)
  }

  private fun requestScreenshotFeed(rotation: SkinRotation) {
    screenshotFeed?.cancel()
    screenshotReceiver = null
    if (width != 0 && height != 0 && connected) {
      if (screenshotReceiver == null) {
        displayRotationInternal = emulator.emulatorConfig.initialOrientation
      }
      val rotatedDisplaySize = computeRotatedDisplaySize(emulatorConfig, rotation)
      val scale = computeScaleToFit(realSize, rotation)
      val scaledDisplaySize = rotatedDisplaySize.scaled(scale)

      // Limit the size of the received screenshots to avoid wasting gRPC resources.
      val screenshotSize = rotatedDisplaySize.scaled(scale.coerceAtMost(1.0))

      val imageFormat = ImageFormat.newBuilder()
        .setFormat(ImageFormat.ImgFormat.RGBA8888) // TODO: Change to RGB888 after b/150494232 is fixed.
        .setWidth(screenshotSize.width)
        .setHeight(screenshotSize.height)
        .build()
      val receiver = ScreenshotReceiver(DisplayShape(scaledDisplaySize.width, scaledDisplaySize.height, rotation))
      screenshotReceiver = receiver
      screenshotFeed = emulator.streamScreenshot(imageFormat, receiver)
    }
  }

  override fun componentResized(event: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentShown(event: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentHidden(event: ComponentEvent) {
    screenshotFeed?.cancel()
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

  private inner class ScreenshotReceiver(val displayShape: DisplayShape) : DummyStreamObserver<ImageMessage>() {
    private var cachedImageSource: MemoryImageSource? = null
    private var screenshotShape: DisplayShape? = null
    private val screenshotForSkinUpdate = AtomicReference<Screenshot>()
    private val screenshotForDisplay = AtomicReference<Screenshot>()

    override fun onNext(response: ImageMessage) {
      if (EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.get()) {
        LOG.info("Screenshot ${response.seq} ${response.format.width}x${response.format.height} ${response.format.rotation.rotation}")
      }
      if (screenshotReceiver != this) {
        return // This screenshot feed has already been cancelled.
      }

      if (response.format.width == 0 || response.format.height == 0) {
        return // Ignore empty screenshot
      }

      val screenshot = Screenshot(response)

      // It is possible that the snapshot feed was requested assuming an out of date device rotation.
      // If the received rotation is different from the assumed one, ignore this screenshot and request
      // a fresh feed for the accurate rotation.
      if (screenshot.rotation != displayShape.rotation) {
        invokeLaterInAnyModalityState {
          requestScreenshotFeed(screenshot.rotation)
        }
        return
      }

      if (screenshot.shape == screenshotShape) {
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
      screenshot.skinLayout = emulator.skinDefinition?.createScaledLayout(displayShape.width, displayShape.height, displayShape.rotation) ?:
                              SkinLayout(Dimension(displayShape.width, displayShape.height))
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

      displayRotationInternal = screenshot.rotation
      displayWidth = displayShape.width
      displayHeight = displayShape.height
      var imageSource = cachedImageSource
      if (imageSource == null || screenshotShape?.width != w || screenshotShape?.height != h) {
        imageSource = MemoryImageSource(w, h, screenshot.pixels, 0, w)
        imageSource.setAnimated(true)
        val image = createImage(imageSource)
        displayImage = if (w == displayWidth && h == displayHeight)
            image else image.getScaledInstance(displayWidth, displayHeight, Image.SCALE_SMOOTH)
        screenshotShape = screenshot.shape
        cachedImageSource = imageSource
      }
      else {
        imageSource.newPixels(screenshot.pixels, ColorModel.getRGBdefault(), 0, w)
      }

      frameNumber++
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
      for (i in pixels.indices) {
        val red = byteIterator.nextByte().toInt() and 0xFF
        val green = byteIterator.nextByte().toInt() and 0xFF
        val blue = byteIterator.nextByte().toInt() and 0xFF
        byteIterator.nextByte() // Alpha is ignored since the screenshots are always opaque.
        pixels[i] = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
      }
      return pixels
    }
  }

  private data class DisplayShape(val width: Int, val height: Int, val rotation: SkinRotation)
}

private const val MAX_SCALE = 2.0 // Zoom above 200% is not allowed.

private val ZOOM_LEVELS = intArrayOf(5, 10, 25, 50, 100, 200) // In percent.

private val LOG = Logger.getInstance(EmulatorView::class.java)
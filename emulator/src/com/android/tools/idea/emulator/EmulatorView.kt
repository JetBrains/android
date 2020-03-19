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
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.flags.StudioFlags.EMBEDDED_EMULATOR_TRACE_SCREENSHOTS
import com.android.tools.idea.protobuf.ByteString
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.awt.image.MemoryImageSource
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt
import com.android.emulator.control.Image as EmulatorImage

/**
 * A view of the Emulator display optionally encased in the device frame.
 *
 * @param emulator the handle of the Emulator
 * @param cropFrame if true, the device frame is cropped to maximize the size of the display image
 */
class EmulatorView(
  private val emulator: EmulatorController,
  cropFrame: Boolean
) : JPanel(BorderLayout()), ComponentListener, ConnectionStateListener, Disposable {

  private var connectionStateLabel = JLabel(getConnectionStateText(ConnectionState.NOT_INITIALIZED))
  private var screenshotFeed: Cancelable? = null
  private var displayImage: Image? = null
  private var displayWidth = 0
  private var displayHeight = 0
  private var skinLayout: ScaledSkinLayout? = null
  private var cropFrameInternal: Boolean = cropFrame
  private var displayRotationInternal = SkinRotation.PORTRAIT
  private val displayTransform = AffineTransform()
  @Volatile
  private var screenshotReceiver: ScreenshotReceiver? = null

  init {
    connectionStateLabel.border = JBUI.Borders.emptyLeft(20)
    connectionStateLabel.font = connectionStateLabel.font.deriveFont(connectionStateLabel.font.size * 1.2F)
    isFocusable = true // Must be focusable to receive keyboard events.

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
        val keyboardEvent =
          when (val c = event.keyChar) {
            '\b' -> createHardwareKeyEvent("Backspace")
            else -> KeyboardEvent.newBuilder().setText(c.toString()).build()
          }
        emulator.sendKey(keyboardEvent)
        println("sendKey: $keyboardEvent")
      }
    })

    updateConnectionState(emulator.connectionState)
  }

  var displayRotation: SkinRotation
    get() = displayRotationInternal
    set(value) {
      if (value != displayRotationInternal && !cropFrameInternal) {
        requestScreenshotFeed(value)
      }
    }

  var cropFrame: Boolean
    get() = cropFrameInternal
    set(value) {
      if (value != cropFrameInternal) {
        cropFrameInternal = value
        requestScreenshotFeed()
      }
    }

  private inline val skinDefinition
    get() = emulator.skinDefinition

  private fun sendMouseEvent(x: Int, y: Int, button: Int) {
    val config = emulator.emulatorConfig
    val displayWidth = config.displayWidth
    val displayHeight = config.displayHeight
    val relativeX = (x - width * 0.5) / this.displayWidth
    val relativeY = (y - height * 0.5) / this.displayHeight
    val displayX: Int
    val displayY: Int
    when (displayRotationInternal) {
      SkinRotation.PORTRAIT -> {
        displayX = ((0.5 + relativeX) * displayWidth).roundToInt()
        displayY = ((0.5 + relativeY) * displayHeight).roundToInt()
      }
      SkinRotation.LANDSCAPE -> {
        displayX = ((0.5 - relativeY) * displayWidth).roundToInt()
        displayY = ((0.5 + relativeX) * displayHeight).roundToInt()
      }
      SkinRotation.REVERSE_PORTRAIT -> {
        displayX = ((0.5 - relativeX) * displayWidth).roundToInt()
        displayY = ((0.5 - relativeY) * displayHeight).roundToInt()
      }
      SkinRotation.REVERSE_LANDSCAPE -> {
        displayX = ((0.5 + relativeY) * displayWidth).roundToInt()
        displayY = ((0.5 - relativeX) * displayHeight).roundToInt()
      }
      else -> {
        return
      }
    }
    val mouseEvent = com.android.emulator.control.MouseEvent.newBuilder()
      .setX(displayX.coerceIn(0, displayWidth))
      .setY(displayY.coerceIn(0, displayHeight))
      .setButtons(button)
      .build()
    emulator.sendMouse(mouseEvent)
  }

  private fun updateConnectionState(connectionState: ConnectionState) {
    if (connectionState == ConnectionState.CONNECTED) {
      remove(connectionStateLabel)
      if (isVisible) {
        requestScreenshotFeed()
      }
    }
    else {
      displayImage = null
      connectionStateLabel.text = getConnectionStateText(connectionState)
      add(connectionStateLabel)
    }
    revalidate()
    repaint()
  }

  private fun getConnectionStateText(connectionState: ConnectionState): String {
    return when (connectionState) {
      ConnectionState.CONNECTED -> "Connected"
      ConnectionState.DISCONNECTED -> "Disconnected from the Emulator"
      else -> "Connecting to the Emulator"
    }
  }

  override fun dispose() {
    removeComponentListener(this)
    emulator.removeConnectionStateListener(this)
  }

  /**
   * Returns the preferred size that depends on the rotation of the Emulator's display.
   */
  override fun getPreferredSize(): Dimension {
    try {
      val config = emulator.emulatorConfig
      return if (displayRotationInternal.is90Degrees) {
        Dimension(config.displayHeight, config.displayWidth)
      }
      else {
        Dimension(config.displayWidth, config.displayHeight)
      }
    }
    catch (e: IllegalStateException) {
      // Don't have Emulator configuration yet. Return the size of the parent.
      return parent.size
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    val displayImage = displayImage ?: return
    val skin = skinLayout ?: return
    val skinSize = skin.skinSize
    val baseX: Int
    val baseY: Int
    if (cropFrameInternal) {
      baseX = (width - displayWidth) / 2 - skin.displayRect.x
      baseY = (height - displayHeight) / 2 - skin.displayRect.y
    }
    else {
      baseX = (width - skinSize.width) / 2
      baseY = (height - skinSize.height) / 2
    }

    g as Graphics2D
    // Draw background.
    val background = skin.background
    if (background != null) {
      displayTransform.setToTranslation((baseX + background.x).toDouble(), (baseY + background.y).toDouble())
      g.drawImage(background.image, displayTransform, null)
    }
    // Draw display.
    displayTransform.setToTranslation((baseX + skin.displayRect.x).toDouble(), (baseY + skin.displayRect.y).toDouble())
    g.drawImage(displayImage, displayTransform, null)
    // Draw mask.
    val mask = skin.mask
    if (mask != null) {
      displayTransform.setToTranslation((baseX + mask.x).toDouble(), (baseY + mask.y).toDouble())
      g.drawImage(mask.image, displayTransform, null)
    }
  }

  private fun requestScreenshotFeed() {
    requestScreenshotFeed(displayRotationInternal)
  }

  private fun requestScreenshotFeed(rotation: SkinRotation) {
    screenshotFeed?.cancel()
    screenshotReceiver = null
    if (width != 0 && height != 0 && emulator.connectionState == ConnectionState.CONNECTED) {
      val w: Int
      val h: Int
      val skin = skinDefinition
      if (cropFrameInternal || skin == null) {
        w = width
        h = height
      }
      else {
        val size = skin.getScaledDisplaySize(width, height, rotation)
        w = size.width
        h = size.height
      }
      val imageFormat = ImageFormat.newBuilder()
        .setFormat(ImageFormat.ImgFormat.RGBA8888) // TODO: Change to RGB888 after b/150494232 is fixed.
        .setWidth(w)
        .setHeight(h)
        .build()
      val screenshotReceiver = ScreenshotReceiver()
      this.screenshotReceiver = screenshotReceiver
      screenshotFeed = emulator.streamScreenshot(imageFormat, screenshotReceiver)
    }
  }

  override fun componentResized(e: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentShown(e: ComponentEvent) {
    requestScreenshotFeed()
  }

  override fun componentHidden(e: ComponentEvent) {
    screenshotFeed?.cancel()
  }

  override fun componentMoved(e: ComponentEvent) {
  }

  override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
    invokeLater {
      updateConnectionState(connectionState)
    }
  }

  private inner class ScreenshotReceiver : DummyStreamObserver<EmulatorImage>() {
    private var cachedImageSource: MemoryImageSource? = null
    private var screenshotShape: ScreenshotShape? = null
    private val screenshotForSkinUpdate = AtomicReference<Screenshot>()
    private val screenshotForDisplay = AtomicReference<Screenshot>()

    override fun onNext(response: EmulatorImage) {
      if (EMBEDDED_EMULATOR_TRACE_SCREENSHOTS.get()) {
        // TODO: Remove isBlack check when Emulator stabilizes.
        val note = if (isBlack(response.image)) " completely black" else ""
        LOG.info("Screenshot ${response.seq} ${response.format.width}x${response.format.height} $note")
      }
      if (screenshotReceiver != this) {
        return // This screenshot feed has already been cancelled.
      }

      if (response.format.width == 0 || response.format.height == 0) {
        return // Ignore empty screenshot
      }

      val screenshot = Screenshot(response)
      if (screenshot.shape == screenshotShape) {
        updateDisplayImageAsync(screenshot)
      }
      else {
        updateSkinAndDisplayImageAsync(screenshot)
      }
    }

    private fun updateSkinAndDisplayImageAsync(screenshot: Screenshot) {
      screenshotForSkinUpdate.set(screenshot)

      ApplicationManager.getApplication().executeOnPooledThread {
        // If the screenshot feed has not been cancelled, update the skin and the display image.
        if (screenshotReceiver == this) {
          updateSkinAndDisplayImage()
        }
      }
    }

    @Slow
    private fun updateSkinAndDisplayImage() {
      val screenshot = screenshotForSkinUpdate.getAndSet(null) ?: return
      screenshot.skinLayout = emulator.skinDefinition?.createScaledLayout(screenshot.width, screenshot.height, screenshot.rotation)
      updateDisplayImageAsync(screenshot)
    }

    private fun updateDisplayImageAsync(screenshot: Screenshot) {
      screenshotForDisplay.set(screenshot)

      invokeLater {
        // If the screenshot feed has not been cancelled, update the display image.
        if (screenshotReceiver == this) {
          updateDisplayImage()
        }
      }
    }

    @UiThread
    private fun updateDisplayImage() {
      val screenshot = screenshotForDisplay.getAndSet(null) ?: return
      val w = screenshot.width
      val h = screenshot.height

      if (!cropFrameInternal) {
        // If the frame is not cropped, it is possible that the snapshot feed was requested assuming
        // a different device rotation. Check that the dimensions of the received screenshot match
        // our expectations. If they don't, ignore this screenshot request a fresh feed.
        val skin = skinDefinition
        if (skin != null) {
          val size = skin.getScaledDisplaySize(width, height, screenshot.rotation)
          if (w != size.width && h != size.height) {
            requestScreenshotFeed(screenshot.rotation)
            return
          }
        }
      }

      val layout = screenshot.skinLayout
      if (layout != null) {
        skinLayout = layout
      }
      if (skinLayout == null) {
        // Create a skin layout without a device frame.
        skinLayout = ScaledSkinLayout(Dimension(w, h))
      }

      displayRotationInternal = screenshot.rotation
      displayWidth = w
      displayHeight = h
      var imageSource = cachedImageSource
      if (imageSource == null || screenshotShape?.width != w || screenshotShape?.height != h) {
        imageSource = MemoryImageSource(w, h, screenshot.pixels, 0, w)
        imageSource.setAnimated(true)
        displayImage = createImage(imageSource)
        screenshotShape = screenshot.shape
        cachedImageSource = imageSource
      }
      else {
        imageSource.newPixels(screenshot.pixels, ColorModel.getRGBdefault(), 0, displayWidth)
      }
      repaint()
    }

    private fun isBlack(image: ByteString): Boolean {
      val bytes = image.toByteArray()
      for (i in bytes.indices) {
        if (i.rem(4) != 3 && bytes[i] != 0.toByte()) {
          return false
        }
      }
      return true
    }
  }

  private class Screenshot(emulatorImage: EmulatorImage) {
    val shape: ScreenshotShape
    val pixels: IntArray
    var skinLayout: ScaledSkinLayout? = null
    val width: Int
      get() = shape.width
    val height: Int
      get() = shape.height
    val rotation: SkinRotation
      get() = shape.rotation

    init {
      val format = emulatorImage.format
      shape = ScreenshotShape(format.width, format.height, format.rotation.rotation)
      pixels = getPixels(emulatorImage.image, width, height)
    }
  }

  private data class ScreenshotShape(val width: Int, val height: Int, val rotation: SkinRotation)

  companion object {
    @JvmStatic
    private val LOG = Logger.getInstance(EmulatorView::class.java)

    @JvmStatic
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
}
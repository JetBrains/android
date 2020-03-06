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

import com.android.emulator.control.ImageFormat
import com.android.emulator.control.KeyboardEvent
import com.android.emulator.control.Rotation.SkinRotation
import com.android.ide.common.util.Cancelable
import com.android.tools.idea.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.protobuf.ByteString
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt
import com.android.emulator.control.Image as EmulatorImage

/**
 * A view of the Emulator display.
 */
class EmulatorView(
  private val emulator: EmulatorController
) : JPanel(BorderLayout()), ComponentListener, ConnectionStateListener, Disposable {

  var screenRotation = SkinRotation.PORTRAIT

  // TODO: Make label text larger.
  private var connectionStateLabel = JLabel(getConnectionStateText(ConnectionState.NOT_INITIALIZED))
  private var screenshotFeed: Cancelable? = null
  private var screenshotReceiver: ScreenshotReceiver? = null
  private var screenImage: Image? = null
  private var screenWidth = 0
  private var screenHeight = 0
  private val screenImageTransform = AffineTransform()

  private val rotatedBy90Degrees
    get() = screenRotation.ordinal % 2 != 0

  init {
    emulator.addConnectionStateListener(this)
    addComponentListener(this)
    isFocusable = true // Must be focusable to receive keyboard events.

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
        val keyboardEvent =
          when (c) {
            '\b' -> createHardwareKeyEvent("Backspace")
            else -> KeyboardEvent.newBuilder().setText(c.toString()).build()
          }
        emulator.sendKey(keyboardEvent)
        println("sendKey: $keyboardEvent")
      }
    })

    updateConnectionState(emulator.connectionState)
  }

  private fun sendMouseEvent(x: Int, y: Int, button: Int) {
    val config = emulator.emulatorConfig
    val displayWidth = config.displayWidth
    val displayHeight = config.displayHeight
    val relativeX = (x - width * 0.5) / screenWidth
    val relativeY = (y - height * 0.5) / screenHeight
    val displayX: Int
    val displayY: Int
    when (screenRotation) {
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
   * Returns the preferred size that depends on the rotation of the Emulator.
   */
  override fun getPreferredSize(): Dimension {
    try {
      val config = emulator.emulatorConfig
      return if (rotatedBy90Degrees) {
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
    g as Graphics2D
    screenImageTransform.setToTranslation((width - screenWidth) * 0.5, (height - screenHeight) * 0.5)
    g.drawImage(screenImage, screenImageTransform, null)
  }

  private fun requestScreenshotFeed() {
    screenshotFeed?.cancel()
    screenshotReceiver = null
    if (width != 0 && height != 0) {
      val imageFormat = ImageFormat.newBuilder()
        .setFormat(ImageFormat.ImgFormat.RGBA8888) // TODO: Change to RGB888 after b/150494232 is fixed.
        .setWidth(width)
        .setHeight(height)
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
    private var imageWidth = 0
    private var imageHeight = 0
    private val screenshotReference = AtomicReference<Screenshot>()

    override fun onNext(response: EmulatorImage) {
      val note = if (isBlack(response.image)) " completely black" else ""
      println(LOG_TIME_FORMAT.format(System.currentTimeMillis()) + " screenshot " + response.seq + note)
      screenshotReference.set(Screenshot(response))

      invokeLater {
        if (screenshotReceiver != this) {
          return@invokeLater // This screenshot feed has already been cancelled.
        }
        val screenshot = screenshotReference.getAndSet(null) ?: return@invokeLater
        screenRotation = screenshot.rotation
        screenWidth = screenshot.width
        screenHeight = screenshot.height
        var imageSource = cachedImageSource
        if (imageSource == null || screenWidth != imageWidth || screenHeight != imageHeight) {
          imageSource = MemoryImageSource(screenWidth, screenHeight, screenshot.pixels, 0, screenWidth)
          imageSource.setAnimated(true)
          screenImage = createImage(imageSource)
          imageWidth = screenWidth
          imageHeight = screenHeight
          cachedImageSource = imageSource
        }
        else {
          imageSource.newPixels(screenshot.pixels, ColorModel.getRGBdefault(), 0, screenWidth)
        }
        repaint()
      }
    }

    private fun isBlack(image: ByteString): Boolean {
      val bytes = image.toByteArray()
      for (i in bytes.indices) {
        if (i.rem(4) != 3 && bytes[i] != 0.toByte()) {
          return false;
        }
      }
      return true
    }
  }

  private class Screenshot(emulatorImage: EmulatorImage) {
    val rotation: SkinRotation
    val width: Int
    val height: Int
    val pixels: IntArray

    init {
      val format = emulatorImage.format
      rotation = format.rotation.rotation
      width = format.width
      height = format.height
      pixels = getPixels(emulatorImage.image, width, height)
    }
  }

  companion object {
    @JvmStatic
    private val LOG = Logger.getInstance(EmulatorView::class.java)
    @JvmStatic
    private val LOG_TIME_FORMAT= SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

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
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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.PRIMARY_DISPLAY_ID
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.android.tools.idea.emulator.scaled
import com.android.tools.idea.emulator.scaledUnbiased
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1
import java.awt.geom.AffineTransform
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
  private val project: Project,
) : AbstractDisplayView(PRIMARY_DISPLAY_ID), Disposable {

  /** Area of the window occupied by the device display image in physical pixels. */
  private var displayRectangle: Rectangle? = null
  private val displayTransform = AffineTransform()
  private var deviceClient: DeviceClient? = null
  val deviceController: DeviceController?
    get() = deviceClient?.deviceController
  private var decoder: VideoDecoder? = null

  /** Size of the device display in device pixels. */
  private val deviceDisplaySize = Dimension()

  var displayRotationQuadrants: Int = 0
    private set

  /** Count of received display frames. */
  @get:VisibleForTesting
  var frameNumber = 0
    private set

  /**
   * The size of the device including frame in device pixels.
   */
  val displaySizeWithFrame: Dimension
    get() = computeActualSize()

  private val connected = true

  /** Last received state of the first mouse button. */
  private var mouseButton1Pressed = false

  init {
    Disposer.register(disposableParent, this)

    // Forward mouse & keyboard events.
    val mouseListener = MyMouseListener()
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)

    AndroidCoroutineScope(this).launch { initializeAgent() }
  }

  private suspend fun initializeAgent() {
    val deviceClient = DeviceClient(this, deviceSerialNumber, deviceAbi, project)
    deviceClient.startAgentAndConnect()
    val decoder = deviceClient.createVideoDecoder(realSize.rotatedByQuadrants(-displayRotationQuadrants))
    EventQueue.invokeLater {
      this.deviceClient = deviceClient
      this.decoder = decoder
    }
    decoder.addFrameListener(object : VideoDecoder.FrameListener {
      override fun onNewFrameAvailable() {
        EventQueue.invokeLater {
          if (frameNumber == 0) {
            hideLongRunningOperationIndicatorInstantly()
          }
          frameNumber++
          if (width != 0 && height != 0) {
            repaint()
          }
        }
      }
    })
    deviceClient.startVideoDecoding(decoder)
  }

  override fun dispose() {
  }

  override fun canZoom(): Boolean = connected

  override fun computeActualSize(): Dimension =
    computeActualSize(displayRotationQuadrants)

  private fun computeActualSize(rotationQuadrants: Int): Dimension =
    deviceDisplaySize.rotatedByQuadrants(rotationQuadrants)

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val resized = width != this.width || height != this.height
    super.setBounds(x, y, width, height)
    if (resized) {
      decoder?.maxOutputSize = realSize.rotatedByQuadrants(-displayRotationQuadrants)
    }
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    if (width == 0 || height == 0) {
      return
    }

    val decoder = decoder ?: return
    g as Graphics2D
    val physicalToVirtualScale = 1.0 / screenScale
    g.scale(physicalToVirtualScale, physicalToVirtualScale) // Set the scale to draw in physical pixels.

    // Draw device display.
    decoder.consumeDisplayFrame { displayFrame ->
      val image = displayFrame.image
      val scale = roundScale(min(realWidth.toDouble() / image.width, realHeight.toDouble() / image.height))
      val w = image.width.scaled(scale).coerceAtMost(realWidth)
      val h = image.height.scaled(scale).coerceAtMost(realHeight)
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
      displayRotationQuadrants = displayFrame.orientation

      deviceClient?.apply {
        if (startTime != 0L) {
          val delay = System.currentTimeMillis() - startTime
          val pushDelay = pushTime - startTime
          val agentStartDelay = startAgentTime - startTime
          val connectionDelay = connectionTime - startTime
          val firstPacketDelay = firstPacketArrival - startTime
          println("Initialization took $delay ms, push took $pushDelay ms, agent was started after $agentStartDelay ms," +
                  " connected after $connectionDelay ms, first video packet arrived after $firstPacketDelay ms")
          startTime = 0L
        }
      }
    }
  }

  private inner class MyMouseListener : MouseAdapter() {

    private var dragging = false

    override fun mousePressed(event: MouseEvent) {
      requestFocusInWindow()
      if (event.button == BUTTON1) {
        mouseButton1Pressed = true
        sendMouseEvent(event.x, event.y, 1)
      }
    }

    override fun mouseReleased(event: MouseEvent) {
      if (event.button == BUTTON1) {
        mouseButton1Pressed = false
        sendMouseEvent(event.x, event.y, 0)
      }
    }

    override fun mouseEntered(event: MouseEvent) {
    }

    override fun mouseExited(event: MouseEvent) {
      if (dragging) {
        sendMouseEvent(event.x, event.y, 0) // Terminate the ongoing dragging.
      }
    }

    override fun mouseDragged(event: MouseEvent) {
      sendMouseEvent(event.x, event.y, 1, drag = true)
    }

    private fun sendMouseEvent(x: Int, y: Int, buttons: Int, drag: Boolean = false) {
      val displayRectangle = displayRectangle ?: return
      // Mouse pointer coordinates compensated for the device display rotation.
      val normalizedX: Int
      val normalizedY: Int
      val imageWidth: Int
      val imageHeight: Int
      when (displayRotationQuadrants) {
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
        else -> return
      }
      // Device display coordinates.
      val displayX = normalizedX.scaledUnbiased(imageWidth, deviceDisplaySize.width)
      val displayY = normalizedY.scaledUnbiased(imageHeight, deviceDisplaySize.height)

      if (displayX in 0 until deviceDisplaySize.width && displayY in 0 until deviceDisplaySize.height) {
        // Within the bounds of the device display.
        sendMouseOrTouchEvent(displayX, displayY, buttons)
      }
      else if (drag) {
        // Crossed the device display boundary while dragging.
        sendMouseOrTouchEvent(displayX.coerceIn(0, deviceDisplaySize.width - 1), displayY.coerceIn(0, deviceDisplaySize.height - 1), 0)
      }
    }

    private fun sendMouseOrTouchEvent(displayX: Int, displayY: Int, buttons: Int) {
      val deviceController = deviceController ?: return
      val message = MouseEventMessage(displayX, displayY, buttons, displayId)
      deviceController.sendControlMessage(message)
    }
  }
}

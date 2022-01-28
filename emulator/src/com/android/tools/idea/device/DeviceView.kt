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
import com.android.tools.idea.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.emulator.AbstractDisplayView
import com.android.tools.idea.emulator.PRIMARY_DISPLAY_ID
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.android.tools.idea.emulator.scaled
import com.android.tools.idea.emulator.scaledUnbiased
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
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

  private var connected = false
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

  /** Last coordinates of the mouse pointer while the first button was pressed.
   *  Set to null when the first mouse button is released. */
  private var lastTouchCoordinates: Point? = null

  init {
    Disposer.register(disposableParent, this)

    addComponentListener(object : ComponentAdapter() {
      override fun componentShown(event: ComponentEvent) {
        if (width > 0 && height > 0) {
          deviceClient?.deviceController?.sendControlMessage(SetMaxVideoResolutionMessage(realWidth, realHeight))
        }
      }
    })

    // Forward mouse & keyboard events.
    val mouseListener = MyMouseListener()
    addMouseListener(mouseListener)
    addMouseMotionListener(mouseListener)

    addKeyListener(MyKeyListener())

    AndroidCoroutineScope(this).launch { initializeAgent() }
  }

  private suspend fun initializeAgent() {
    try {
      val deviceClient = DeviceClient(this, deviceSerialNumber, deviceAbi, project)
      deviceClient.startAgentAndConnect()
      val decoder = deviceClient.createVideoDecoder(realSize.rotatedByQuadrants(-displayRotationQuadrants))
      EventQueue.invokeLater {
        if (!disposed) {
          this.deviceClient = deviceClient
          this.decoder = decoder
          if (width > 0 && height > 0) {
            deviceClient.deviceController.sendControlMessage(SetMaxVideoResolutionMessage(realWidth, realHeight))
          }
        }
      }
      decoder.addFrameListener(object : VideoDecoder.FrameListener {

        override fun onNewFrameAvailable() {
          EventQueue.invokeLater {
            connected = true
            if (frameNumber == 0) {
              hideLongRunningOperationIndicatorInstantly()
            }
            frameNumber++
            if (width != 0 && height != 0) {
              repaint()
            }
          }
        }

        override fun onEndOfVideoStream() {
          showDisconnectedMessage("Lost connection to the device. See the error log.")
        }
      })
      deviceClient.startVideoDecoding(decoder)
    }
    catch (_: CancellationException) {
      // The view has been closed.
    }
    catch (e: Throwable) {
      thisLogger().error("Failed to initialize the screen sharing agent", e)
      showDisconnectedMessage("Failed to initialize the device agent. See the error log.")
    }
  }

  private fun showDisconnectedMessage(message: String) {
    EventQueue.invokeLater {
      if (!disposed) {
        connected = false
        decoder = null
        hideLongRunningOperationIndicatorInstantly()
        disconnectedStateLabel.text = message
        add(disconnectedStateLabel)
        revalidate()
      }
    }
  }

  override fun dispose() {
    disposed = true
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
      if (width > 0 && height > 0) {
        deviceClient?.deviceController?.sendControlMessage(SetMaxVideoResolutionMessage(realWidth, realHeight))
      }
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

    if (multiTouchMode) {
      val displayRect = displayRectangle
      if (displayRect != null) {
        drawMultiTouchFeedback(g, displayRect, lastTouchCoordinates != null)
      }
    }
  }


  private fun sendMotionEvent(x: Int, y: Int, action: Int) {
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
      else -> {
        assert(false) { "Invalid display orientation: $displayRotationQuadrants" }
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
      val adjustedX = displayX.coerceIn(0, deviceDisplaySize.width - 1)
      val adjustedY = displayY.coerceIn(0, deviceDisplaySize.height - 1)
      sendMotionEventDisplayCoordinates(adjustedX, adjustedY, action)
      sendMotionEventDisplayCoordinates(adjustedX, adjustedY, MotionEventMessage.ACTION_OUTSIDE)
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
        VK_PAGE_DOWN -> AKEYCODE_PAGE_UP
        else -> AKEYCODE_UNKNOWN
      }
    }
  }

  private inner class MyMouseListener : MouseAdapter() {

    override fun mousePressed(event: MouseEvent) {
      requestFocusInWindow()
      if (event.button == BUTTON1) {
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
      if ((event.modifiersEx and BUTTON1_DOWN_MASK) != 0) {
        sendMotionEvent(event.x, event.y, MotionEventMessage.ACTION_UP) // Terminate the ongoing dragging.
      }
      multiTouchMode = false
    }

    override fun mouseDragged(event: MouseEvent) {
      updateMultiTouchMode(event)
      if ((event.modifiersEx and BUTTON1_DOWN_MASK) != 0) {
        sendMotionEvent(event.x, event.y, MotionEventMessage.ACTION_MOVE)
      }
    }

    override fun mouseMoved(event: MouseEvent) {
      updateMultiTouchMode(event)
    }

    private fun updateMultiTouchMode(event: MouseEvent) {
      val oldMultiTouchMode = multiTouchMode
      multiTouchMode = (event.modifiersEx and CTRL_DOWN_MASK) != 0
      if (multiTouchMode && oldMultiTouchMode) {
        repaint() // If multitouch mode changed above, the repaint method was already called.
      }
    }
  }
}

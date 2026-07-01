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
package com.android.tools.idea.streaming.emulator

import com.android.emulator.control.RotationRadian
import com.android.emulator.control.Velocity
import com.android.tools.adtui.common.AdtUiCursorType
import com.android.tools.adtui.common.AdtUiCursorsProvider
import com.android.tools.idea.streaming.EmulatorSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.IdeGlassPaneEx
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_DOWN
import java.awt.event.KeyEvent.VK_END
import java.awt.event.KeyEvent.VK_HOME
import java.awt.event.KeyEvent.VK_KP_DOWN
import java.awt.event.KeyEvent.VK_KP_LEFT
import java.awt.event.KeyEvent.VK_KP_RIGHT
import java.awt.event.KeyEvent.VK_KP_UP
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_PAGE_DOWN
import java.awt.event.KeyEvent.VK_PAGE_UP
import java.awt.event.KeyEvent.VK_RIGHT
import java.awt.event.KeyEvent.VK_UP
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.math.PI
import kotlin.math.min

/**
 * Controller of virtual scene camera. Processes keyboard and mouse events and converts them to camera movements and rotations in virtual
 * space.
 *
 * Camera velocity is changed in response to pressing and releasing WASDQE keys:
 * - D - forward along X axis
 * - A - backward along X axis
 * - E - forward along Y axis
 * - Q - backward along Y axis
 * - S - forward along Z axis
 * - W - backward along Z axis
 *
 * For AZERTY keyboard the keys are ZQSDAE.
 *
 * The coordinate system is right-handed and is defined as follows:
 * - X axis is pointing right
 * - Y axis is pointing up
 * - Z axis is pointing towards the viewer
 */
internal class VirtualSceneCameraController(
  disposableParent: Disposable,
  private val hostComponent: JComponent,
  private val emulator: EmulatorController,
  private val allowTranslation: Boolean = true,
) : Disposable {

  private val controlKeys = EmulatorSettings.getInstance().cameraVelocityControls.keys
  private var pressedKeysMask = 0
  private val virtualSceneCameraVelocity = Velocity.newBuilder()

  init {
    Disposer.register(disposableParent, this)
    val glass = IdeGlassPaneUtil.find(hostComponent) as IdeGlassPaneEx
    val cursor = AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.MOVE)
    val rootPane = glass.rootPane
    val scale = PI / min(rootPane.width, rootPane.height)
    UIUtil.setCursor(rootPane, cursor)
    glass.setCursor(cursor, hostComponent)
    val mouseListener = MyMouseListener(glass, cursor, scale)
    glass.addMousePreprocessor(mouseListener, this)
    glass.addMouseMotionPreprocessor(mouseListener, this)
  }

  /** Stops the camera movement and releases all keys. */
  override fun dispose() {
    // Stop the camera movement and release all keys.
    pressedKeysMask = 0
    if (virtualSceneCameraVelocity.x != 0F || virtualSceneCameraVelocity.y != 0F || virtualSceneCameraVelocity.z != 0F) {
      virtualSceneCameraVelocity.clear()
      emulator.setVirtualSceneCameraVelocity(Velocity.getDefaultInstance(), getEmptyObserver())
    }
    val glass = IdeGlassPaneUtil.find(hostComponent) as IdeGlassPaneEx
    glass.setCursor(null, hostComponent)
    UIUtil.setCursor(glass.rootPane, null)
  }

  /** Notifies the controller that a key was pressed. Returns true if the key was handled. */
  fun keyPressed(event: KeyEvent): Boolean {
    when (val keyCode = event.keyCode) {
      VK_LEFT,
      VK_KP_LEFT -> rotateVirtualSceneCamera(0.0, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
      VK_RIGHT,
      VK_KP_RIGHT -> rotateVirtualSceneCamera(0.0, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
      VK_UP,
      VK_KP_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
      VK_DOWN,
      VK_KP_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, 0.0)
      VK_HOME -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
      VK_END -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
      VK_PAGE_UP -> rotateVirtualSceneCamera(VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
      VK_PAGE_DOWN -> rotateVirtualSceneCamera(-VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN, -VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN)
      else -> {
        val mask = keyToMask(keyCode)
        if (mask == 0 || !allowTranslation) {
          return false
        }
        val newPressed = pressedKeysMask or mask
        if (pressedKeysMask != newPressed) {
          pressedKeysMask = newPressed
          updateCameraVelocity(mask, CAMERA_VELOCITY_UNIT)
        }
      }
    }
    event.consume()
    return true
  }

  /** Notifies the controller that a key was released. Returns true if the key was handled. */
  fun keyReleased(event: KeyEvent): Boolean {
    val mask = keyToMask(event.keyCode)
    if (mask == 0 || !allowTranslation) {
      return false
    }
    val newPressed = pressedKeysMask and mask.inv()
    if (pressedKeysMask != newPressed) {
      pressedKeysMask = newPressed
      updateCameraVelocity(mask, -CAMERA_VELOCITY_UNIT)
    }
    event.consume()
    return true
  }

  private fun rotateVirtualSceneCamera(rotationX: Double, rotationY: Double) {
    val cameraRotation = RotationRadian.newBuilder().setX(rotationX.toFloat()).setY(rotationY.toFloat()).build()
    emulator.rotateVirtualSceneCamera(cameraRotation, getEmptyObserver())
  }

  private fun keyToMask(keyCode: Int): Int {
    val index = controlKeys.indexOf(keyCode.toChar())
    return if (index >= 0) 1 shl index else 0
  }

  private fun updateCameraVelocity(mask: Int, deltaVelocity: Float) {
    when (mask) {
      0x08 -> virtualSceneCameraVelocity.x += deltaVelocity // D
      0x02 -> virtualSceneCameraVelocity.x -= deltaVelocity // A
      0x20 -> virtualSceneCameraVelocity.y += deltaVelocity // E
      0x10 -> virtualSceneCameraVelocity.y -= deltaVelocity // Q
      0x04 -> virtualSceneCameraVelocity.z += deltaVelocity // S
      0x01 -> virtualSceneCameraVelocity.z -= deltaVelocity // W
      else -> throw IllegalArgumentException()
    }
    emulator.setVirtualSceneCameraVelocity(virtualSceneCameraVelocity.build(), getEmptyObserver())
  }

  private inner class MyMouseListener(private val glass: IdeGlassPaneEx, private val cursor: Cursor, private val scale: Double) :
    MouseAdapter() {

    private val referencePoint = MouseInfo.getPointerInfo().location

    override fun mouseMoved(event: MouseEvent) {
      if (referencePoint != null) {
        rotateVirtualSceneCamera(-(event.yOnScreen - referencePoint.y) * scale, (referencePoint.x - event.xOnScreen) * scale)
        referencePoint.setLocation(event.xOnScreen, event.yOnScreen)
        event.consume()
      }
    }

    override fun mouseDragged(event: MouseEvent) {
      mouseMoved(event)
    }

    override fun mouseEntered(event: MouseEvent) {
      glass.setCursor(cursor, hostComponent)
    }
  }
}

private const val CAMERA_VELOCITY_UNIT = 1F
private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES = 5
private const val VIRTUAL_SCENE_CAMERA_ROTATION_STEP_RADIAN = VIRTUAL_SCENE_CAMERA_ROTATION_STEP_DEGREES * PI / 180

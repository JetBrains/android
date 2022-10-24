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
package com.android.tools.idea.common.surface

import com.android.ide.common.rendering.api.RenderSession
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import java.awt.Cursor

/**
 * An implementation of [Interaction] that passes interaction events to layoutlib via [LayoutlibSceneManager]
 */
class LayoutlibInteraction(private val sceneView: SceneView) : Interaction() {
  override fun commit(event: InteractionEvent) {
    val mouseEvent = event as MouseReleasedEvent
    end(mouseEvent.eventObject.x, mouseEvent.eventObject.y, mouseEvent.eventObject.modifiersEx)
  }

  override fun end(x: Int, y: Int, modifiersEx: Int) {
    val androidX = Coordinates.getAndroidX(sceneView, x)
    val androidY = Coordinates.getAndroidY(sceneView, y)
    when(val sceneManager = sceneView.sceneManager) {
      is LayoutlibSceneManager -> sceneManager.triggerTouchEventAsync(RenderSession.TouchEventType.RELEASE, androidX, androidY)
    }
    sceneView.surface.repaint()
  }

  override fun begin(event: InteractionEvent) {
    when (event) {
      is MousePressedEvent -> begin(event.eventObject.x, event.eventObject.y, event.eventObject.modifiersEx)
      is KeyPressedEvent -> (sceneView.sceneManager as? LayoutlibSceneManager)?.triggerKeyEventAsync(event.eventObject)
      else -> {}
    }
  }

  override fun begin(x: Int, y: Int, modifiersEx: Int) {
    super.begin(x, y, modifiersEx)
    val androidX = Coordinates.getAndroidX(sceneView, myStartX)
    val androidY = Coordinates.getAndroidY(sceneView, myStartY)
    when(val sceneManager = sceneView.sceneManager) {
      is LayoutlibSceneManager -> sceneManager.triggerTouchEventAsync(RenderSession.TouchEventType.PRESS, androidX, androidY)
    }
  }

  override fun cancel(event: InteractionEvent) {
    cancel(event.info.x, event.info.y, event.info.modifiersEx)
  }

  override fun cancel(x: Int, y: Int, modifiersEx: Int) {
    sceneView.scene.mouseCancel()
    sceneView.surface.repaint()
  }

  override fun getCursor(): Cursor? = sceneView.scene.mouseCursor

  override fun update(event: InteractionEvent) {
    when (event) {
      is MouseDraggedEvent -> {
        val mouseX = event.eventObject.x
        val mouseY = event.eventObject.y
        sceneView.context.setMouseLocation(mouseX, mouseY)
        update(mouseX, mouseY, event.eventObject.modifiersEx)
      }
      is KeyPressedEvent -> (sceneView.sceneManager as? LayoutlibSceneManager)?.triggerKeyEventAsync(event.eventObject)
      is KeyReleasedEvent -> (sceneView.sceneManager as? LayoutlibSceneManager)?.triggerKeyEventAsync(event.eventObject)
      else -> {}
    }
  }

  override fun update(x: Int, y: Int, modifiersEx: Int) {
    super.update(x, y, modifiersEx)
    val androidX = Coordinates.getAndroidX(sceneView, x)
    val androidY = Coordinates.getAndroidY(sceneView, y)
    when(val sceneManager = sceneView.sceneManager) {
      is LayoutlibSceneManager -> sceneManager.triggerTouchEventAsync(RenderSession.TouchEventType.DRAG, androidX, androidY)
    }
    sceneView.surface.repaint()
  }
}
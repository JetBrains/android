/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.relative

import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.uibuilder.api.DragType
import com.android.tools.idea.uibuilder.api.InsertType
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeDragTarget
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import com.intellij.openapi.diagnostic.Logger

/**
 * TODO: don't allow dragging multiple component from Component Tree
 */
internal class RelativeDragHandlerKt(editor: ViewEditor,
                                     handler: RelativeLayoutHandlerKt,
                                     layout: SceneComponent,
                                     components: List<NlComponent>,
                                     type: DragType) : DragHandler(editor, handler, layout, components, type) {

  private val component: SceneComponent
  private val dragTarget = RelativeDragTarget()
  /**
   * The selected components when this handler created.
   * The selection will be changed when dragging from Palette since the [components] is a mutable List.
   * We need to use the original components when dragging from Palette, and using the muted components when dragging from Component Tree.
   */
  private val initialComponents = components.toList()

  init {
    assert(!components.isEmpty())
    val dragged = components[0]
    component = layout.scene.getSceneComponent(dragged) ?:
        TemporarySceneComponent(layout.scene, dragged).apply { setSize(editor.pxToDp(dragged.w), editor.pxToDp(dragged.h), false) }

    component.setTargetProvider({ _, _ -> mutableListOf<Target>(dragTarget) }, false)
    layout.addChild(component)
    component.drawState = SceneComponent.DrawState.DRAG
  }

  override fun start(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int) {
    super.start(x, y, modifiers)
    dragTarget.mouseDown(x, y)
  }

  override fun update(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int): String? {
    val result = super.update(x, y, modifiers)
    @AndroidDpCoordinate val dx = x + startX - component.drawWidth / 2
    @AndroidDpCoordinate val dy = y + startY - component.drawHeight / 2
    dragTarget.mouseDrag(dx, dy, null)
    return result
  }

  override fun commit(@AndroidCoordinate x: Int, @AndroidCoordinate y: Int, modifiers: Int, insertType: InsertType) {
    layout.scene.removeComponent(component)

    when (insertType) {
      InsertType.CREATE -> {
        // Dragging from Palette, use initial components.
        editor.insertChildren(layout.nlComponent, initialComponents, -1, insertType)
        @AndroidDpCoordinate val dx = x + startX - lastX - component.drawWidth / 2
        @AndroidDpCoordinate val dy = y + startY - lastY - component.drawHeight / 2
        dragTarget.mouseRelease(dx, dy, null)
      }
      InsertType.MOVE_INTO -> {
        // Dragging from Component Tree, use initial components.
        editor.insertChildren(layout.nlComponent, components, -1, insertType)
        for (c in components) {
          val sceneComponent = layout.getSceneComponent(c) ?: continue
          dragTarget.component = sceneComponent

          @AndroidDpCoordinate val dx = x + startX - lastX - sceneComponent.drawWidth / 2
          @AndroidDpCoordinate val dy = y + startY - lastY - sceneComponent.drawHeight / 2

          dragTarget.mouseDrag(dx, dy, null)
          dragTarget.mouseRelease(dx, dy, null)
        }
      }
      else -> Logger.getInstance(javaClass.name).error("Unexpected InsertType in ${javaClass.name}#commit}")
    }

    layout.scene.checkRequestLayoutStatus()
  }

  override fun cancel() {
    layout.scene.removeComponent(component)
    dragTarget.cancel()
  }
}

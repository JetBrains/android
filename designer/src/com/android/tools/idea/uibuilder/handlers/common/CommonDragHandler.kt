/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.common

import com.android.sdklib.AndroidCoordinate
import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.common.scene.target.CommonDragTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w

/** [DragHandler] handles the dragging from Palette and ComponentTree for all Layouts. */
internal class CommonDragHandler(
  editor: ViewEditor,
  handler: ViewGroupHandler,
  layout: SceneComponent,
  components: List<NlComponent>,
  type: DragType,
) : DragHandler(editor, handler, layout, components, type) {

  private val dragTarget: CommonDragTarget

  init {
    val dragged = components[0]
    val component =
      layout.scene.getSceneComponent(dragged)
        ?: TemporarySceneComponent(layout.scene, dragged).apply {
          setSize(editor.pxToDp(dragged.w), editor.pxToDp(dragged.h))
        }

    dragTarget = CommonDragTarget(component, fromToolWindow = true)

    component.setTargetProvider { _ -> mutableListOf<Target>(dragTarget) }
    component.updateTargets()
    // Note: Don't use [dragged] in this lambda function since the content of components may be
    // replaced within interaction.
    // This weird implementation may be fixed in the future, but we just work around here.
    component.setComponentProvider { _ -> components[0] }
    layout.addChild(component)
    component.drawState = SceneComponent.DrawState.DRAG
  }

  override fun start(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int) {
    super.start(x, y, modifiers)
    dragTarget.mouseDown(x, y)
  }

  override fun update(
    @AndroidDpCoordinate x: Int,
    @AndroidDpCoordinate y: Int,
    modifiers: Int,
    sceneContext: SceneContext,
  ): String? {
    val result = super.update(x, y, modifiers, sceneContext)
    dragTarget.mouseDrag(x, y, emptyList(), sceneContext)
    dragTarget.component.scene.requestLayoutIfNeeded()
    return result
  }

  // Note that coordinate is AndroidCoordinate, not AndroidDpCoordinate.
  override fun commit(
    @AndroidCoordinate x: Int,
    @AndroidCoordinate y: Int,
    modifiers: Int,
    insertType: InsertType,
  ) {
    dragTarget.insertType = insertType
    @AndroidDpCoordinate val dx = editor.pxToDp(x)
    @AndroidDpCoordinate val dy = editor.pxToDp(y)
    dragTarget.mouseRelease(dx, dy, emptyList())

    // Remove Temporary SceneComponent
    val component = dragTarget.component
    if (component is TemporarySceneComponent) {
      layout.scene.removeComponent(component)
    }
    component.drawState = SceneComponent.DrawState.NORMAL
    layout.scene.requestLayoutIfNeeded()
  }

  override fun cancel() {
    if (dragTarget.component is TemporarySceneComponent) {
      layout.scene.removeComponent(dragTarget.component)
    }
    dragTarget.component.drawState = SceneComponent.DrawState.NORMAL
    dragTarget.mouseCancel()
  }
}

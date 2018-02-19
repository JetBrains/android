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
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeDragTarget
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w

/**
 * TODO: don't allow dragging multiple component from Component Tree
 */
internal class RelativeDragHandlerKt(editor: ViewEditor,
                                     handler: RelativeLayoutHandlerKt,
                                     layout: SceneComponent,
                                     components: List<NlComponent>,
                                     type: DragType
) : DragHandler(editor, handler, layout, components, type) {

  private val component: SceneComponent
  private val dragTarget = RelativeDragTarget()

  init {
    assert(!components.isEmpty())
    val dragged = components[0]
    component = layout.scene.getSceneComponent(dragged) ?:
        TemporarySceneComponent(layout.scene, dragged).apply { setSize(editor.pxToDp(dragged.w), editor.pxToDp(dragged.h), false) }

    component.setTargetProvider({ _ -> mutableListOf<Target>(dragTarget) })
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
    dragTarget.mouseDrag(dx, dy, emptyList())
    return result
  }

  override fun commit(@AndroidCoordinate x: Int, @AndroidCoordinate y: Int, modifiers: Int, insertType: InsertType) {
    editor.insertChildren(layout.nlComponent, components, -1, insertType)
    for (child in components) {
      @AndroidDpCoordinate val dx = editor.pxToDp(x) + startX - component.drawWidth / 2
      @AndroidDpCoordinate val dy = editor.pxToDp(y) + startY - component.drawHeight / 2
      dragTarget.mouseRelease(dx, dy, child)
    }
    // Remove Temporary SceneComponent
    layout.scene.removeComponent(component)
    layout.scene.checkRequestLayoutStatus()
  }

  override fun cancel() {
    layout.scene.removeComponent(component)
    dragTarget.cancel()
  }
}

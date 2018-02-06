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
package com.android.tools.idea.uibuilder.handlers.frame

import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.uibuilder.api.*
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w

class FrameDragHandler(editor: ViewEditor,
                       handler: ViewGroupHandler,
                       layout: SceneComponent,
                       components: List<NlComponent>,
                       type: DragType
) : DragHandler(editor, handler, layout, components, type) {

  private val component: SceneComponent?
  private val dragTarget = FrameDragTarget()

  init {
    if (components.size == 1) {
      val selectedNlCmponent = components[0]
      component = TemporarySceneComponent(layout.scene, selectedNlCmponent)
      component.setSize(editor.pxToDp(selectedNlCmponent.w), editor.pxToDp(selectedNlCmponent.h), false)
      component.setTargetProvider({ listOf(dragTarget) })
      component.setDrawState(SceneComponent.DrawState.DRAG)
      layout.addChild(component)
    }
    else {
      component = null
    }
  }

  override fun start(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int) {
    super.start(x, y, modifiers)
    if (component == null) {
      return
    }
    dragTarget.mouseDown(x, y)
  }

  override fun update(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int): String? {
    val result = super.update(x, y, modifiers)
    if (component == null) {
      return "undefined"
    }
    @AndroidDpCoordinate val dx = x + startX - component.drawWidth / 2
    @AndroidDpCoordinate val dy = y + startY - component.drawHeight / 2
    dragTarget.mouseDrag(dx, dy, null)
    return result
  }

  override fun commit(@AndroidCoordinate x: Int, @AndroidCoordinate y: Int, modifiers: Int, insertType: InsertType) {
    if (component != null) {
      dragTarget.cancel()
      layout.scene.removeComponent(component)
      editor.insertChildren(layout.nlComponent, components, -1, insertType)
      layout.scene.checkRequestLayoutStatus()
    }
  }

  override fun cancel() {
    if (component != null) {
      layout.scene.removeComponent(component)
    }
    dragTarget.cancel()
  }
}

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

import com.android.tools.idea.common.model.AndroidCoordinate
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.scene.target.CommonDragTarget
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.AdapterViewHandler
import com.android.tools.idea.uibuilder.handlers.DelegatingViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.TabLayoutHandler
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceCategoryHandler
import com.android.tools.idea.uibuilder.handlers.preference.PreferenceScreenHandler
import com.android.tools.idea.uibuilder.menu.ItemHandler
import com.android.tools.idea.uibuilder.menu.MenuHandler
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import kotlin.reflect.KClass

private const val ERROR_UNDEFINED = "undefined"

/**
 * [DragHandler] handles the dragging from Palette and ComponentTree for all Layouts.
 */
internal class CommonDragHandler(editor: ViewEditor,
                                 handler: ViewGroupHandler,
                                 layout: SceneComponent,
                                 components: List<NlComponent>,
                                 type: DragType
) : DragHandler(editor, handler, layout, components, type) {

  private val component: SceneComponent?
  private val dragTarget = CommonDragTarget()

  init {
    if (components.size == 1) {
      val dragged = components[0]
      component = layout.scene.getSceneComponent(dragged) ?: TemporarySceneComponent(layout.scene, dragged).apply {
        setSize(editor.pxToDp(dragged.w), editor.pxToDp(dragged.h), false)
      }

      component.setTargetProvider { _ -> mutableListOf<Target>(dragTarget) }
      // Note: Don't use [dragged] in this lambda function since the content of components may be replaced within interaction.
      // This weird implementation may be fixed in the future, but we just work around here.
      component.setComponentProvider { _ -> components[0] }
      layout.addChild(component)
      component.drawState = SceneComponent.DrawState.DRAG
    }
    else {
      component = null
    }
  }

  override fun start(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int) {
    if (component == null) {
      return
    }
    super.start(x, y, modifiers)
    dragTarget.mouseDown(x, y)
  }

  override fun update(@AndroidDpCoordinate x: Int, @AndroidDpCoordinate y: Int, modifiers: Int): String? {
    if (component == null) {
      return ERROR_UNDEFINED
    }
    val result = super.update(x, y, modifiers)
    @AndroidDpCoordinate val dx = x + startX - component.drawWidth / 2
    @AndroidDpCoordinate val dy = y + startY - component.drawHeight / 2
    dragTarget.mouseDrag(dx, dy, emptyList())
    return result
  }

  override fun commit(@AndroidCoordinate x: Int, @AndroidCoordinate y: Int, modifiers: Int, insertType: InsertType) {
    if (component == null) {
      return
    }
    editor.insertChildren(layout.nlComponent, components, -1, insertType)
    assert(components.size == 1)
    @AndroidDpCoordinate val dx = editor.pxToDp(x) + startX - component.drawWidth / 2
    @AndroidDpCoordinate val dy = editor.pxToDp(y) + startY - component.drawHeight / 2
    dragTarget.mouseRelease(dx, dy, emptyList())

    // Remove Temporary SceneComponent
    layout.scene.removeComponent(component)
    layout.scene.checkRequestLayoutStatus()
  }

  override fun cancel() {
    if (component != null) {
      layout.scene.removeComponent(component)
    }
    dragTarget.cancel()
  }

  companion object {
    /**
     * The classes of [ViewGroupHandler] which don't support [CommonDragHandler] yet.
     * TODO: makes [CommonDragHandler] can be used in all [ViewGroupHandler].
     */
    private val HANDLER_CLASSES_NOT_SUPPORT= listOf(
      AdapterViewHandler::class,
      DelegatingViewGroupHandler::class,
      ItemHandler::class,
      MenuHandler::class,
      PreferenceCategoryHandler::class,
      PreferenceScreenHandler::class,
      TabLayoutHandler::class
    )

    @JvmStatic
    fun isSupportCommonDragHandler(handler: ViewGroupHandler) = handler::class !in HANDLER_CLASSES_NOT_SUPPORT
  }
}

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
package com.android.tools.idea.uibuilder.handlers.grid

import com.android.SdkConstants
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.Region
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.common.CommonDragHandler
import com.android.tools.idea.uibuilder.handlers.grid.targets.GridResizeTarget
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList

open class GridLayoutHandler : ViewGroupHandler() {

  protected open val namespace = SdkConstants.ANDROID_URI

  override fun createDragHandler(
    editor: ViewEditor,
    layout: SceneComponent,
    components: List<NlComponent>,
    type: DragType
  ): DragHandler = CommonDragHandler(editor, this, layout, components, type)

  override fun onChildRemoved(layout: NlComponent, newChild: NlComponent, insertType: InsertType) {
    newChild.removeAttribute(namespace, SdkConstants.ATTR_LAYOUT_ROW)
    newChild.removeAttribute(namespace, SdkConstants.ATTR_LAYOUT_COLUMN)
    newChild.removeAttribute(namespace, SdkConstants.ATTR_LAYOUT_ROW_SPAN)
    newChild.removeAttribute(namespace, SdkConstants.ATTR_LAYOUT_COLUMN_SPAN)
  }

  override fun createInteraction(screenView: ScreenView, x: Int, y: Int, component: NlComponent) =
    SceneInteraction(screenView)

  override fun handlesPainting() = true

  override fun createChildTargets(
    parentComponent: SceneComponent,
    childComponent: SceneComponent
  ): List<Target> {
    val listBuilder = ImmutableList.builder<Target>()
    createResizeTarget(listBuilder)
    return listBuilder.build()
  }

  override fun shouldAddCommonDragTarget(component: SceneComponent) = true

  private fun createResizeTarget(listBuilder: ImmutableList.Builder<Target>) {
    ResizeBaseTarget.Type.values().map { listBuilder.add(GridResizeTarget(it)) }
  }

  override fun getPlaceholders(
    component: SceneComponent,
    draggedComponents: List<SceneComponent>
  ): List<Placeholder> {
    val listBuilder = ImmutableList.builder<Placeholder>()
    val barrier = getGridBarriers(component)
    for (row in barrier.rowIndices) {
      for (column in barrier.columnIndices) {
        val bounds = barrier.getBounds(row, column) ?: continue
        if (bounds.width <= 0 || bounds.height <= 0) {
          continue
        }
        val r =
          Region(
            bounds.x,
            bounds.y,
            bounds.x + bounds.width,
            bounds.y + bounds.height,
            component.depth
          )
        listBuilder.add(GridPlaceholder(r, row, column, namespace, component))
      }
    }
    return listBuilder.build()
  }
}

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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.grid.targets.GridDragTarget
import com.android.tools.idea.uibuilder.handlers.grid.targets.GridResizeTarget
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList

open class GridLayoutHandler : ViewGroupHandler() {

  override fun createDragHandler(editor: ViewEditor, layout: SceneComponent, components: List<NlComponent>, type: DragType): DragHandler =
      GridDragHandler(editor, this, layout, components, type)

  override fun createInteraction(screenView: ScreenView, layout: NlComponent) = SceneInteraction(screenView)

  override fun handlesPainting() = true

  override fun createChildTargets(parentComponent: SceneComponent, childComponent: SceneComponent): List<Target> {
    val listBuilder = ImmutableList.builder<Target>()
    createDragTarget(listBuilder)
    createResizeTarget(listBuilder)
    return listBuilder.build()
  }

  open internal fun createDragTarget(listBuilder: ImmutableList.Builder<Target>) = listBuilder.add(GridDragTarget(isSupportLibrary = false))

  private fun createResizeTarget(listBuilder: ImmutableList.Builder<Target>) =
      ResizeBaseTarget.Type.values().map { listBuilder.add(GridResizeTarget(it)) }
}

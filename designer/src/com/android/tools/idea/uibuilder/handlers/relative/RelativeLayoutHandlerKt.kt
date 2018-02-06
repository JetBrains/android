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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeDragTarget
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeParentTarget
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeResizeTarget
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeWidgetTarget
import com.android.tools.idea.uibuilder.model.getBaseline
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList

/**
 * Handler of New Target Architecture for the `<RelativeLayout>` layout
 *
 * TODO:
 * - Don't allow selecting multiple widgets.
 */
class RelativeLayoutHandlerKt : ViewGroupHandler() {

  override fun createDragHandler(editor: ViewEditor, layout: SceneComponent, components: List<NlComponent>, type: DragType): DragHandler? {
    if (layout.drawWidth == 0 || layout.drawHeight == 0) {
      return null
    }
    return RelativeDragHandlerKt(editor, this, layout, components, type)
  }

  override fun handlesPainting(): Boolean = true

  override fun createInteraction(screenView: ScreenView, layout: NlComponent) = SceneInteraction(screenView)

  override fun createTargets(sceneComponent: SceneComponent): List<Target> {
    val listBuilder = ImmutableList.Builder<Target>()
    RelativeParentTarget.Type.values().forEach { listBuilder.add(RelativeParentTarget(it)) }
    return listBuilder.build()
  }

  override fun createChildTargets(parentComponent: SceneComponent, childComponent: SceneComponent): List<Target> {
    val listBuilder = ImmutableList.builder<Target>()
    listBuilder.add(RelativeDragTarget())
    createResizeTarget(listBuilder)
    createWidgetTargets(listBuilder, childComponent)
    return listBuilder.build()
  }

  private fun createResizeTarget(listBuilder: ImmutableList.Builder<Target>) =
      ResizeBaseTarget.Type.values().forEach { listBuilder.add(RelativeResizeTarget(it)) }

  private fun createWidgetTargets(listBuilder: ImmutableList.Builder<Target>, sceneComponent: SceneComponent) =
      RelativeWidgetTarget.Type.values()
          .filter { it !== RelativeWidgetTarget.Type.BASELINE || sceneComponent.nlComponent.getBaseline() != -1 }
          .forEach { listBuilder.add(RelativeWidgetTarget(it)) }
}

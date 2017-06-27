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

import com.android.tools.idea.uibuilder.api.DragType
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeResizeTarget
import com.android.tools.idea.uibuilder.model.NlComponent
import com.android.tools.idea.uibuilder.scene.SceneComponent
import com.android.tools.idea.uibuilder.scene.SceneInteraction
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.scene.target.Target
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList
import java.awt.Graphics2D


/**
 * Handler of New Target Architecture for the `<RelativeLayout>` layout
 * Current progress:
 * 1. Delegate all non target related functions to RelativeLayoutHandler.java
 * 2. Resizing target for widgets
 *
 * TBD
 * - Limit the resizing options
 * - Create Draggable Target (positions related to parent and other components)
 *   - Snap and tips when Dragging
 * - Decoration of relationships between widgets.
 * - Handle the case of dragging from palette?
 */
class RelativeLayoutHandlerKt : ViewGroupHandler() {

  // TODO: Remove this and migrate all delegated functions when this class is ready.
  private val myLegacyHandler = RelativeLayoutHandler()

  override fun paintConstraints(screenView: ScreenView, graphics: Graphics2D, component: NlComponent) =
      myLegacyHandler.paintConstraints(screenView, graphics, component)

  override fun createDragHandler(editor: ViewEditor, layout: SceneComponent, components: List<NlComponent>, type: DragType) =
      myLegacyHandler.createDragHandler(editor, layout, components, type)

  override fun handlesPainting(): Boolean = true

  override fun createInteraction(screenView: ScreenView, layout: NlComponent) = SceneInteraction(screenView)

  override fun createTargets(sceneComponent: SceneComponent, isParent: Boolean): List<Target> {
    if (isParent) {
      // RelativeLayout case
      return super.createTargets(sceneComponent, isParent)
    }
    else {
      // children components cases
      val listBuilder = ImmutableList.builder<Target>()
      createDragTargets(listBuilder)
      createResizeTarget(listBuilder)
      return listBuilder.build()
    }
  }

  private fun createDragTargets(listBuilder: ImmutableList.Builder<Target>) {
    // TODO create the drag target for component
  }

  private fun createResizeTarget(listBuilder: ImmutableList.Builder<Target>) {
    // TODO: limit resizing options. (e.g. alignParentLeft -> don't allow resizing from left sides)
    listBuilder
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.LEFT_TOP))
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.LEFT))
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM))
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.TOP))
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.BOTTOM))
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP))
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.RIGHT))
        .add(RelativeResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM))
  }
}

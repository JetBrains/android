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
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeDragTarget
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeParentTarget
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
 * 3. Dragging a widget inside RelativeLayout
 *
 * TBD
 * - Drag to other widgets
 * - Decoration of relationships between widgets.
 * - Handle the case of multiple dragging component.
 * - Handle the case of dragging from palette?
 */
class RelativeLayoutHandlerKt : ViewGroupHandler() {

  companion object {
    /**
     * The width of every edges which provide by RelativeLayout
     */
    const private val EDGE_WIDTH = 3
  }

  // TODO: Remove this and migrate all delegated functions when this class is ready.
  private val myLegacyHandler = RelativeLayoutHandler()

  override fun paintConstraints(screenView: ScreenView, graphics: Graphics2D, component: NlComponent) =
      myLegacyHandler.paintConstraints(screenView, graphics, component)

  override fun createDragHandler(editor: ViewEditor, layout: SceneComponent, components: List<NlComponent>, type: DragType) =
      myLegacyHandler.createDragHandler(editor, layout, components, type)

  override fun handlesPainting(): Boolean = true

  override fun createInteraction(screenView: ScreenView, layout: NlComponent) = SceneInteraction(screenView)

  override fun createTargets(sceneComponent: SceneComponent, isParent: Boolean): List<Target> {
    val listBuilder = ImmutableList.Builder<Target>()
    if (isParent) {
      // RelativeLayout cases, create the target related to attributes of parent
      createParentTargets(listBuilder, sceneComponent)
    }
    else {
      // children components cases
      listBuilder.add(RelativeDragTarget())
      createResizeTarget(listBuilder)

      // create related target of this component.
      createRelativeTargets(listBuilder, sceneComponent)
    }
    return listBuilder.build()
  }

  private fun createParentTargets(listBuilder: ImmutableList.Builder<Target>, relativeSceneComponent: SceneComponent) {
    val x1 = relativeSceneComponent.drawX
    val y1 = relativeSceneComponent.drawY
    val x2 = x1 + relativeSceneComponent.drawWidth
    val y2 = y1 + relativeSceneComponent.drawHeight

    listBuilder.add(RelativeParentTarget(RelativeParentTarget.Type.LEFT, x1, y1, x1 + EDGE_WIDTH, y2))
    listBuilder.add(RelativeParentTarget(RelativeParentTarget.Type.TOP, x1, y1, x2, y1 + EDGE_WIDTH))
    listBuilder.add(RelativeParentTarget(RelativeParentTarget.Type.RIGHT, x2 - EDGE_WIDTH, y1, x2, y2))
    listBuilder.add(RelativeParentTarget(RelativeParentTarget.Type.BOTTOM, x1, y2 - EDGE_WIDTH, x2, y2))

    // center horizontal create vertical line
    listBuilder.add(
        RelativeParentTarget(RelativeParentTarget.Type.CENTER_HORIZONTAL,(x1 + x2 - EDGE_WIDTH) / 2, y1, (x1 + x2 + EDGE_WIDTH) / 2, y2))
    // center vertical create horizontal line
    listBuilder.add(
        RelativeParentTarget(RelativeParentTarget.Type.CENTER_VERTICAL, x1, (y1 + y2 - EDGE_WIDTH) / 2, x2, (y1 + y2 + EDGE_WIDTH) / 2))
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

  private fun createRelativeTargets(listBuilder: ImmutableList.Builder<Target>, sceneComponent: SceneComponent) {
    // TODO: create target related to the component. (aboveTo, belowTo, ... etc)
  }
}

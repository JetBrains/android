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
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeWidgetTarget
import com.android.tools.idea.uibuilder.model.NlComponent
import com.android.tools.idea.uibuilder.model.getBaseline
import com.android.tools.idea.uibuilder.scene.SceneComponent
import com.android.tools.idea.uibuilder.scene.SceneInteraction
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.scene.target.Target
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList
import java.awt.Graphics2D

/**
 * The width of every edges which provide by RelativeLayout
 */
const private val PARENT_EDGE_WIDTH = 3

/**
 * The width of every edges which provide by Widgets in RelativeLayout
 */
const private val WIDGET_EDGE_WIDTH = 2

/**
 * Handler of New Target Architecture for the `<RelativeLayout>` layout
 * Current progress:
 * 1. Delegate all non target related functions to RelativeLayoutHandler.java
 * 2. Resizing target for widgets
 * 3. Dragging a widget inside RelativeLayout
 * 4. Drag to other widgets
 *
 * TODO:
 * - Improve the decoration of relationships between widgets.
 * - Handle the case of multiple dragging component.
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
      createWidgetTargets(listBuilder, sceneComponent)
    }
    return listBuilder.build()
  }

  private fun createParentTargets(listBuilder: ImmutableList.Builder<Target>, relativeSceneComponent: SceneComponent) {
    val x1 = relativeSceneComponent.drawX
    val y1 = relativeSceneComponent.drawY
    val x2 = x1 + relativeSceneComponent.drawWidth
    val y2 = y1 + relativeSceneComponent.drawHeight

    listBuilder
        .add(RelativeParentTarget(RelativeParentTarget.Type.LEFT, x1, y1, x1 + PARENT_EDGE_WIDTH, y2))
        .add(RelativeParentTarget(RelativeParentTarget.Type.TOP, x1, y1, x2, y1 + PARENT_EDGE_WIDTH))
        .add(RelativeParentTarget(RelativeParentTarget.Type.RIGHT, x2 - PARENT_EDGE_WIDTH, y1, x2, y2))
        .add(RelativeParentTarget(RelativeParentTarget.Type.BOTTOM, x1, y2 - PARENT_EDGE_WIDTH, x2, y2))
        .add(RelativeParentTarget(RelativeParentTarget.Type.CENTER_HORIZONTAL,
            (x1 + x2 - PARENT_EDGE_WIDTH) / 2, y1, (x1 + x2 + PARENT_EDGE_WIDTH) / 2, y2))
        .add(RelativeParentTarget(RelativeParentTarget.Type.CENTER_VERTICAL, x1,
            (y1 + y2 - PARENT_EDGE_WIDTH) / 2, x2, (y1 + y2 + PARENT_EDGE_WIDTH) / 2))
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

  private fun createWidgetTargets(listBuilder: ImmutableList.Builder<Target>, sceneComponent: SceneComponent) {
    val parent = sceneComponent.parent ?: return

    val x1 = sceneComponent.drawX
    val y1 = sceneComponent.drawY
    val x2 = x1 + sceneComponent.drawWidth
    val y2 = y1 + sceneComponent.drawHeight

    val parentX1 = parent.drawX
    val parentY1 = parent.drawY
    val parentX2 = parentX1 + parent.drawWidth
    val parentY2 = parentY1 + parent.drawHeight

    val halfWidth = WIDGET_EDGE_WIDTH / 2

    listBuilder
        .add(RelativeWidgetTarget(RelativeWidgetTarget.Type.LEFT, x1 - halfWidth, parentY1, x1 + halfWidth, parentY2))
        .add(RelativeWidgetTarget(RelativeWidgetTarget.Type.TOP, parentX1, y1 - halfWidth, parentX2, y1 + halfWidth))
        .add(RelativeWidgetTarget(RelativeWidgetTarget.Type.RIGHT, x2 - halfWidth, parentY1, x2 + halfWidth, parentY2))
        .add(RelativeWidgetTarget(RelativeWidgetTarget.Type.BOTTOM, parentX1, y2 - halfWidth, parentX2, y2 + halfWidth))

    if (sceneComponent.nlComponent.getBaseline() != -1) {
      val bl = y1 + sceneComponent.baseline
      listBuilder.add(RelativeWidgetTarget(RelativeWidgetTarget.Type.BASELINE, parentX1, bl - halfWidth, parentX2, bl + halfWidth))
    }
  }
}

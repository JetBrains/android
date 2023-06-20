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

import com.android.SdkConstants
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.scene.target.AnchorTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.api.DragHandler
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewGroupHandler
import com.android.tools.idea.uibuilder.api.actions.ToggleAutoConnectAction
import com.android.tools.idea.uibuilder.api.actions.ViewAction
import com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getToggleSizeActions
import com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getViewOptionsAction
import com.android.tools.idea.uibuilder.handlers.relative.targets.RELATIVE_LAYOUT_ATTRIBUTES
import com.android.tools.idea.uibuilder.handlers.relative.targets.RelativeAnchorTarget
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
class RelativeLayoutHandler : ViewGroupHandler() {

  override fun createDragHandler(editor: ViewEditor, layout: SceneComponent, components: List<NlComponent>, type: DragType): DragHandler? {
    if (layout.drawWidth == 0 || layout.drawHeight == 0) {
      return null
    }
    return RelativeDragHandler(editor, this, layout, components, type)
  }

  override fun onChildRemoved(
    layout: NlComponent,
    newChild: NlComponent,
    insertType: InsertType
  ) {
    RELATIVE_LAYOUT_ATTRIBUTES.forEach { newChild.removeAndroidAttribute(it) }
  }

  override fun cleanUpAttributes(component: NlComponent, attributes: NlAttributesHolder) {
    with (attributes) {
      if (getAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL) == SdkConstants.VALUE_TRUE &&
          getAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL) == SdkConstants.VALUE_TRUE) {
        removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL)
        removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL)
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT, SdkConstants.VALUE_TRUE)
      }

      val margin = getAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN)
      if (margin != null) {
        MARGINS_ATTRS.forEach {
          if (margin != getAndroidAttribute(it)) {
            return
          }
        }
        removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_MARGIN)
      }
    }
  }

  override fun handlesPainting(): Boolean = true

  override fun createInteraction(screenView: ScreenView,
                                 x: Int,
                                 y: Int,
                                 component: NlComponent) = SceneInteraction(screenView)

  override fun createTargets(sceneComponent: SceneComponent): List<Target> {
    val listBuilder = ImmutableList.Builder<Target>()
    RelativeParentTarget.Type.values().forEach { listBuilder.add(RelativeParentTarget(it)) }
    AnchorTarget.Type.values()
      .filterNot { it == AnchorTarget.Type.BASELINE }
      .forEach { listBuilder.add(RelativeAnchorTarget(it, true)) }
    return listBuilder.build()
  }

  override fun createChildTargets(parentComponent: SceneComponent, childComponent: SceneComponent): List<Target> {
    val listBuilder = ImmutableList.builder<Target>()
    listBuilder.add(RelativeDragTarget())

    RESIZE_TARGETS.forEach { listBuilder.add(RelativeResizeTarget(it)) }
    AnchorTarget.Type.values()
      .filterNot { it == AnchorTarget.Type.BASELINE }
      .forEach { listBuilder.add(RelativeAnchorTarget(it, false)) }
    RelativeWidgetTarget.Type.values()
      .filter { it !== RelativeWidgetTarget.Type.BASELINE || childComponent.nlComponent.getBaseline() != -1 }
      .forEach { listBuilder.add(RelativeWidgetTarget(it)) }
    return listBuilder.build()
  }

  override fun addToolbarActions(actions: MutableList<ViewAction>) {
    actions.add(getViewOptionsAction())
    actions.addAll(getToggleSizeActions())
    actions.add(ToggleAutoConnectAction())
  }

  override fun getPlaceholders(sceneComponent: SceneComponent, draggedComponents: List<SceneComponent>) =
    listOf(RelativePlaceholder(sceneComponent))
}

private val RESIZE_TARGETS = listOf(
  ResizeBaseTarget.Type.LEFT_TOP,
  ResizeBaseTarget.Type.LEFT_BOTTOM,
  ResizeBaseTarget.Type.RIGHT_TOP,
  ResizeBaseTarget.Type.RIGHT_BOTTOM
)

val MARGINS_ATTRS = arrayOf(
  SdkConstants.ATTR_LAYOUT_MARGIN_START,
  SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
  SdkConstants.ATTR_LAYOUT_MARGIN_END,
  SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
)

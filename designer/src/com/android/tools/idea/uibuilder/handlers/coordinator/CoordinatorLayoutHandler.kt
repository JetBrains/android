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
package com.android.tools.idea.uibuilder.handlers.coordinator

import com.android.AndroidXConstants.COORDINATOR_LAYOUT
import com.android.AndroidXConstants.FLOATING_ACTION_BUTTON
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.ATTR_FITS_SYSTEM_WINDOWS
import com.android.SdkConstants.ATTR_LAYOUT_ANCHOR
import com.android.SdkConstants.ATTR_LAYOUT_ANCHOR_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_BEHAVIOR
import com.android.SdkConstants.ATTR_LAYOUT_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.BOTTOM_APP_BAR
import com.android.SdkConstants.ID_PREFIX
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneInteraction
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.uibuilder.handlers.ScrollViewHandler
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder
import com.android.tools.idea.uibuilder.handlers.frame.FrameResizeTarget
import com.android.tools.idea.uibuilder.scene.target.ResizeBaseTarget
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.collect.ImmutableList

/** Handler for android.support.design.widget.CoordinatorLayout */
class CoordinatorLayoutHandler : ScrollViewHandler() {

  override fun handlesPainting() = true

  override fun getInspectorProperties(): List<String> =
    listOf(ATTR_CONTEXT, ATTR_FITS_SYSTEM_WINDOWS)

  override fun getLayoutInspectorProperties(): List<String> {
    return listOf(ATTR_LAYOUT_BEHAVIOR, ATTR_LAYOUT_ANCHOR, ATTR_LAYOUT_ANCHOR_GRAVITY)
  }

  override fun onChildInserted(parent: NlComponent, child: NlComponent, insertType: InsertType) {
    // b/67452405 Do not call super()
    if (COORDINATOR_LAYOUT.newName() == parent.tagName && BOTTOM_APP_BAR == child.tagName) {
      configureNewBottomAppBar(parent, child)
    }
  }

  override fun onChildRemoved(layout: NlComponent, newChild: NlComponent, insertType: InsertType) {
    newChild.removeAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR_GRAVITY)
    newChild.removeAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR)

    val removedId = newChild.id ?: return

    layout.children
      .filterNot { it == newChild }
      .forEach {
        val anchor = it.getAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR) ?: return@forEach
        if (NlComponent.extractId(anchor) == removedId) {
          it.removeAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR)
          it.removeAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR_GRAVITY)
        }
      }
  }

  /**
   * Return a new ConstraintInteraction instance to handle a mouse interaction
   *
   * @param screenView the associated screen view
   * @param x mouse down (x)
   * @param y mouse down (y)
   * @param component the component target of the interaction
   * @return a new instance of ConstraintInteraction
   */
  override fun createInteraction(screenView: ScreenView, x: Int, y: Int, component: NlComponent) =
    SceneInteraction(screenView)

  override fun createChildTargets(
    parentComponent: SceneComponent,
    childComponent: SceneComponent,
  ): MutableList<Target> {
    val listBuilder = ImmutableList.builder<Target>()

    if (childComponent !is TemporarySceneComponent) {
      listBuilder.add(
        CoordinatorResizeTarget(ResizeBaseTarget.Type.LEFT_TOP),
        CoordinatorResizeTarget(ResizeBaseTarget.Type.LEFT),
        CoordinatorResizeTarget(ResizeBaseTarget.Type.LEFT_BOTTOM),
        CoordinatorResizeTarget(ResizeBaseTarget.Type.TOP),
        CoordinatorResizeTarget(ResizeBaseTarget.Type.BOTTOM),
        CoordinatorResizeTarget(ResizeBaseTarget.Type.RIGHT_TOP),
        CoordinatorResizeTarget(ResizeBaseTarget.Type.RIGHT),
        CoordinatorResizeTarget(ResizeBaseTarget.Type.RIGHT_BOTTOM),
      )
    }

    return listBuilder.build()
  }

  override fun shouldAddCommonDragTarget(component: SceneComponent): Boolean {
    return component !is TemporarySceneComponent
  }

  override fun acceptsChild(layout: NlComponent, newChild: NlComponent) = true

  override fun getPlaceholders(component: SceneComponent, draggedComponents: List<SceneComponent>) =
    component.children
      .filterNot { component.scene.selection.contains(it.nlComponent) }
      .flatMap { child ->
        CoordinatorPlaceholder.Type.values().map { type ->
          CoordinatorPlaceholder(component, child, type)
        }
      }
      .toList()
      .plus(ViewGroupPlaceholder(component))

  private fun configureNewBottomAppBar(parent: NlComponent, bottomAppBar: NlComponent) {
    val fab = parent.children.firstOrNull { FLOATING_ACTION_BUTTON.newName() == it.tagName }
    if (fab != null && !bottomAppBar.id.isNullOrEmpty()) {
      NlWriteCommandActionUtil.run(listOf(fab), "Move fab to BottomAppBar") {
        fab.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR, ID_PREFIX + bottomAppBar.id!!)
        fab.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, null)
        fab.setAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN, null)
      }
    }
  }
}

// The resize behaviour is similar to the FrameResizeTarget so far.
private class CoordinatorResizeTarget(type: ResizeBaseTarget.Type) : FrameResizeTarget(type)

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
package com.android.tools.idea.common.scene

import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlAttributesHolder
import java.awt.Point

/**
 * Describe an area for receiving the drag event from
 * [com.android.tools.idea.common.scene.target.CommonDragTarget]. [host] is the parent
 * SceneComponent when the dragged [SceneComponent] inserted.
 */
abstract class Placeholder(val host: SceneComponent) {

  /**
   * The dominating [Placeholder] is rendered when the mouse hovers over the [associatedComponent].
   *
   * @see dominate
   */
  open val associatedComponent: SceneComponent = host

  /**
   * Return true if this Placeholder updates the attribute when dragging component in same parent.
   * The updated attributes are pending in
   * [com.android.tools.idea.common.model.AttributesTransaction] which is committed after mouse
   * released.
   *
   * @see updateLiveAttribute
   */
  open val isLiveUpdatable = false

  /**
   * Only dominate [Placeholder] would be rendered. Also, dominate [Placeholder]s has higher
   * priority than recessive ones. Which means all dominated [Placeholder]s will be snapped first.
   * If it is impossible to snap to a dominating [Placeholder], then recessive [Placeholder]s would
   * be tried.<br>
   *
   * When dragging multiple components, the dominate Placeholders change the position of all dragged
   * components. The recessive Placeholder doesn't change the positions of them so they will keep
   * the same related positions.
   */
  open val dominate = true

  /** Provide the interactive [Region] of this Placeholder. */
  abstract val region: Region

  /**
   * Used to find the anchor component that will become the next sibling of the inserted component.
   * If this is null, the inserted component is appended as last component.
   */
  open fun findNextSibling(
    appliedComponent: SceneComponent,
    newParent: SceneComponent
  ): SceneComponent? {
    if (appliedComponent.parent != newParent) {
      return null
    }
    val siblings = newParent.children
    val index = siblings.indexOf(appliedComponent)
    return if (index == -1) null else siblings.getOrNull(index + 1)
  }

  /**
   * Called for snapping to Placeholder. [info] provides the bounds of the interacting
   * [SceneComponent].<br> [retPoint] is used to store the value after snapping.<br> The return
   * value is the distance of original point to snapped point. It may not exist if it the given
   * point couldn't snap to this Placeholder.
   */
  open fun snap(info: SnappingInfo, retPoint: Point): Boolean = false

  /** Function to update attribute when mouse is dragging. */
  open fun updateLiveAttribute(
    sceneComponent: SceneComponent,
    attributes: NlAttributesHolder,
    x: Int,
    y: Int
  ) = Unit

  /** Function to update attribute when mouse is released. */
  abstract fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder)
}

data class Region(
  @AndroidDpCoordinate val left: Int,
  @AndroidDpCoordinate val top: Int,
  @AndroidDpCoordinate val right: Int,
  @AndroidDpCoordinate val bottom: Int,
  val level: Int = 0
) {
  /** Helper function to determine if [Region] contains the specified point. */
  fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom
}

/** Class for providing information from snapping [SceneComponent]. */
class SnappingInfo(
  @AndroidDpCoordinate val left: Int,
  @AndroidDpCoordinate val top: Int,
  @AndroidDpCoordinate val right: Int,
  @AndroidDpCoordinate val bottom: Int
) {
  /** Helper function for getting centerX */
  @AndroidDpCoordinate val centerX: Int = (left + right) / 2

  /** Helper function for getting centerY */
  @AndroidDpCoordinate val centerY: Int = (top + bottom) / 2
}

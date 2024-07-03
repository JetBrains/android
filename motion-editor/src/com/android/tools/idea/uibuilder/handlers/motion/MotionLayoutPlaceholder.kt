/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion

import com.android.SdkConstants
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.scene.Placeholder
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SnappingInfo
import com.android.tools.idea.common.scene.TemporarySceneComponent
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities
import com.android.tools.idea.uibuilder.handlers.constraint.targets.GuidelineTarget
import com.android.tools.idea.uibuilder.handlers.motion.editor.targets.MotionLayoutDropHandler
import com.android.tools.idea.uibuilder.model.x
import com.android.tools.idea.uibuilder.model.y
import com.android.tools.idea.uibuilder.scout.Scout
import com.android.tools.idea.uibuilder.scout.ScoutArrange
import com.android.tools.idea.uibuilder.scout.ScoutWidget
import java.awt.Point

class MotionLayoutPlaceholder(host: SceneComponent) : Placeholder(host) {

  private val delegator = ViewGroupPlaceholder(host)

  override val dominate = false

  override val region = delegator.region

  override fun snap(info: SnappingInfo, retPoint: Point) = delegator.snap(info, retPoint)

  override fun updateAttribute(sceneComponent: SceneComponent, attributes: NlAttributesHolder) =
    updateLiveAttribute(sceneComponent, attributes, sceneComponent.drawX, sceneComponent.drawY)

  /**
   * Position of [SceneComponent] is not set yet when live rendering is enabled, the [x] and [y]
   * argument should be passed in.
   */
  override fun updateLiveAttribute(
    sceneComponent: SceneComponent,
    attributes: NlAttributesHolder,
    x: Int,
    y: Int,
  ) {
    if (ConstraintComponentUtilities.isGuideLine(sceneComponent.authoritativeNlComponent)) {
      val horizontal =
        attributes.getAndroidAttribute(SdkConstants.ATTR_ORIENTATION) != SdkConstants.VALUE_VERTICAL
      GuidelineTarget.GuidelineDropHandler(sceneComponent, horizontal)
        .updateAttributes(attributes, x, y)
    } else if (sceneComponent !is TemporarySceneComponent) {
      MotionLayoutDropHandler(sceneComponent).updateAttributes(attributes, host, x, y)
    } else {
      val nlComponent = sceneComponent.authoritativeNlComponent
      var horizontalMatchParent = false
      var verticalMatchParent = false
      if (
        SdkConstants.VALUE_MATCH_PARENT ==
          attributes.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH)
      ) {
        horizontalMatchParent = true
      }
      if (
        SdkConstants.VALUE_MATCH_PARENT ==
          attributes.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT)
      ) {
        verticalMatchParent = true
      }
      if (horizontalMatchParent || verticalMatchParent) {
        val transaction = nlComponent.startAttributeTransaction()
        nlComponent.x = Coordinates.dpToPx(host.scene.sceneManager, x.toFloat())
        nlComponent.y = Coordinates.dpToPx(host.scene.sceneManager, y.toFloat())
        val parentScoutWidget = ScoutWidget(host.nlComponent, null)
        val scoutWidgets = ScoutWidget.create(listOf(nlComponent), parentScoutWidget)
        val margin = Scout.getMargin()
        if (horizontalMatchParent) {
          ScoutArrange.expandHorizontally(scoutWidgets, parentScoutWidget, margin, false)
        }
        if (verticalMatchParent) {
          ScoutArrange.expandVertically(scoutWidgets, parentScoutWidget, margin, false)
        }
        transaction.apply()
      }
    }
  }

  override fun isLiveUpdatableForComponent(draggedComponent: SceneComponent): Boolean = true
}

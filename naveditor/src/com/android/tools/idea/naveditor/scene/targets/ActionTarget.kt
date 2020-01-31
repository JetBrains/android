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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.BaseTarget
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.ActionType
import com.android.tools.idea.naveditor.model.getActionType
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.scene.getCurvePoints
import com.android.tools.idea.naveditor.scene.getRegularActionIconRect
import com.android.tools.idea.naveditor.scene.getSelfActionIconRect
import com.android.tools.idea.naveditor.scene.selfActionPoints
import java.awt.geom.Rectangle2D

private val SOURCE_RECT = Rectangle2D.Float()
private val DEST_RECT = Rectangle2D.Float()

/**
 * An Action in the navigation editor
 */
class ActionTarget(component: SceneComponent,
                   private val sourceComponent: SceneComponent,
                   private val destComponent: SceneComponent) : BaseTarget() {

  private val actionType: ActionType

  init {
    setComponent(component)
    actionType = component.nlComponent.getActionType(component.scene.root!!.nlComponent)
  }

  override fun getPreferenceLevel() = Target.DRAG_LEVEL

  override fun newSelection() = listOf(myComponent)

  override fun layout(context: SceneContext, l: Int, t: Int, r: Int, b: Int) = false

  override fun render(list: DisplayList, sceneContext: SceneContext) {}

  override fun addHit(transform: SceneContext, picker: ScenePicker) {
    val source = Coordinates.getSwingRectDip(transform, sourceComponent.fillDrawRect2D(0, SOURCE_RECT))
    val isPopAction = myComponent.nlComponent.popUpTo != null
    var iconRect: Rectangle2D.Float? = null

    if (actionType === ActionType.SELF) {
      @SwingCoordinate val points = selfActionPoints(source, transform.scale.toFloat())
      for (i in 1 until points.size) {
        picker.addLine(this, 0, points[i - 1].x.toInt(), points[i - 1].y.toInt(), points[i].x.toInt(), points[i].y.toInt(), 5)
      }

      if (isPopAction) {
        iconRect = getSelfActionIconRect(points[0], transform.scale.toFloat())
      }
    }
    else {
      val scale = transform.scale.toFloat()
      val dest = Coordinates.getSwingRectDip(transform, destComponent.fillDrawRect2D(0, DEST_RECT))
      val (p1, p2, p3, p4) = getCurvePoints(source, dest, scale)
      picker.addCurveTo(this, 0, p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt(), p3.x.toInt(), p3.y.toInt(),
                        p4.x.toInt(), p4.y.toInt(), 10)

      if (isPopAction) {
        iconRect = getRegularActionIconRect(source, dest, scale)
      }
    }

    if (iconRect != null) {
      picker.addRect(this, 0, iconRect.x.toInt(), iconRect.y.toInt(), (iconRect.x + iconRect.width).toInt(),
                     (iconRect.y + iconRect.height).toInt())
    }
  }

  override fun getToolTipText() = component.id
}


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

import com.android.SdkConstants
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.model.uiName
import com.android.tools.idea.naveditor.scene.DRAW_SCREEN_LABEL_LEVEL
import com.android.tools.idea.naveditor.scene.HEADER_HEIGHT
import com.android.tools.idea.naveditor.scene.HEADER_ICON_SIZE
import com.android.tools.idea.naveditor.scene.HEADER_TEXT_HEIGHT
import com.android.tools.idea.naveditor.scene.HEADER_TEXT_PADDING
import com.android.tools.idea.naveditor.scene.NavColors.SUBDUED_TEXT
import com.android.tools.idea.naveditor.scene.draw.DrawIcon
import com.android.tools.idea.naveditor.scene.scaledFont
import java.awt.Font
import java.awt.geom.Rectangle2D

/**
 * [ScreenHeaderTarget] draws the header above the frame.
 * It consists of an optional start destination icon, followed by
 * the label, followed by an optional deep link icon.
 */
class ScreenHeaderTarget(component: SceneComponent) : NavBaseTarget(component) {

  private val hasDeepLink: Boolean
    get() {
      return component.nlComponent.children.any { it.tagName == SdkConstants.TAG_DEEP_LINK }
    }

  override fun getPreferenceLevel(): Int {
    return Target.ANCHOR_LEVEL
  }

  override fun layout(sceneTransform: SceneContext,
                      @NavCoordinate l: Int,
                      @NavCoordinate t: Int,
                      @NavCoordinate r: Int,
                      @NavCoordinate b: Int): Boolean {
    layoutRectangle(l, t - HEADER_HEIGHT.toInt(), r, t)
    return false
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    val view = component.scene.designSurface.currentSceneView ?: return
    @SwingCoordinate var l = Coordinates.getSwingX(view, myLeft)
    @SwingCoordinate val t = Coordinates.getSwingY(view, myTop)
    @SwingCoordinate var r = Coordinates.getSwingX(view, myRight)
    @SwingCoordinate val iconSize = Coordinates.getSwingDimension(view, HEADER_ICON_SIZE)
    @SwingCoordinate val textPadding = Coordinates.getSwingDimension(view, HEADER_TEXT_PADDING)
    @SwingCoordinate val textHeight = Coordinates.getSwingDimension(view, HEADER_TEXT_HEIGHT)

    if (component.nlComponent.isStartDestination) {
      list.add(DrawIcon(Rectangle2D.Float(l, t, iconSize, iconSize), DrawIcon.IconType.START_DESTINATION))
      l += iconSize + textPadding
    }

    if (hasDeepLink) {
      list.add(DrawIcon(Rectangle2D.Float(r - iconSize, t, iconSize, iconSize), DrawIcon.IconType.DEEPLINK))
      r -= iconSize + textPadding
    }

    val text = component.nlComponent.uiName
    @SwingCoordinate val textRectangle = Rectangle2D.Float(l, t + textPadding, r - l, textHeight)
    list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, text, textRectangle,
        SUBDUED_TEXT, scaledFont(sceneContext, Font.PLAIN), false))
  }

  override fun addHit(transform: SceneContext, picker: ScenePicker) {
    picker.addRect(this, 0, getSwingLeft(transform), getSwingTop(transform), getSwingRight(transform), getSwingBottom(transform))
  }

  override fun newSelection() = listOf(component)
}

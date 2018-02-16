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
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.DRAW_SCREEN_LABEL_LEVEL
import com.android.tools.idea.naveditor.scene.draw.DrawIcon
import com.android.tools.idea.naveditor.scene.scaledFont
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_START_DESTINATION
import java.awt.Font
import java.awt.Rectangle

/**
 * [ScreenHeaderTarget] draws the header above the frame.
 * It consists of an optional start destination icon, followed by
 * the label, followed by an optional deep link icon.
 */
@NavCoordinate private const val ICON_SIZE = 14
@NavCoordinate private const val TEXT_PADDING = 2
@NavCoordinate private const val HEADER_PADDING = 8

@NavCoordinate private const val HEADER_HEIGHT = ICON_SIZE + HEADER_PADDING
@NavCoordinate private const val TEXT_HEIGHT = ICON_SIZE - 2 * TEXT_PADDING

class ScreenHeaderTarget(component: SceneComponent) : NavBaseTarget(component) {

  private val hasDeepLink: Boolean
    get() {
      return component.nlComponent.children.any { it.tagName == SdkConstants.TAG_DEEP_LINK }
    }

  private val isStartDestination: Boolean
    get() {
      val parent = component.nlComponent.parent ?: return false

      val startDestination = NlComponent.stripId(parent.getAttribute(SdkConstants.AUTO_URI, ATTR_START_DESTINATION))
      return startDestination.equals(component.id)
    }

  override fun getPreferenceLevel(): Int {
    return Target.ANCHOR_LEVEL
  }

  override fun layout(sceneTransform: SceneContext,
                      @NavCoordinate l: Int,
                      @NavCoordinate t: Int,
                      @NavCoordinate r: Int,
                      @NavCoordinate b: Int): Boolean {
    layoutRectangle(l, t - HEADER_HEIGHT, r, t)
    return false
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    @SwingCoordinate var l = getSwingLeft(sceneContext)
    @SwingCoordinate val t = getSwingTop(sceneContext)
    @SwingCoordinate var r = getSwingRight(sceneContext)
    @SwingCoordinate val iconSize = Coordinates.getSwingDimension(sceneContext, ICON_SIZE)
    @SwingCoordinate val textPadding = Coordinates.getSwingDimension(sceneContext, TEXT_PADDING)
    @SwingCoordinate val textHeight = Coordinates.getSwingDimension(sceneContext, TEXT_HEIGHT)

    if (isStartDestination) {
      list.add(DrawIcon(Rectangle(l, t, iconSize, iconSize), DrawIcon.IconType.START_DESTINATION))
      l += iconSize + textPadding
    }

    if (hasDeepLink) {
      list.add(DrawIcon(Rectangle(r - iconSize, t, iconSize, iconSize), DrawIcon.IconType.DEEPLINK))
      r -= iconSize + textPadding
    }

    val text = component.nlComponent.id ?: ""
    @SwingCoordinate val textRectangle = Rectangle(l, t + textPadding, r - l, textHeight)
    list.add(DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, text, textRectangle,
        sceneContext.colorSet.subduedText, scaledFont(sceneContext, Font.PLAIN), false))
  }

  override fun addHit(transform: SceneContext, picker: ScenePicker) {
    picker.addRect(this, 0, getSwingLeft(transform), getSwingTop(transform), getSwingRight(transform), getSwingBottom(transform))
  }
}

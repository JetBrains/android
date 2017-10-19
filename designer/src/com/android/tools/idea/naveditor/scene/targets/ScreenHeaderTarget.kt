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
import com.android.tools.idea.common.model.AndroidDpCoordinate
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.ScenePicker
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.target.Target
import com.android.tools.idea.naveditor.scene.draw.DrawIcon
import com.android.tools.idea.naveditor.scene.draw.DrawScreenLabel
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_START_DESTINATION
import java.awt.Font
import java.awt.Rectangle

/**
 * [ScreenHeaderTarget] draws the header above the frame.
 * It consists of an optional start destination icon, followed by
 * the label, followed by an optional deep link icon.
 */
class ScreenHeaderTarget(component: SceneComponent) : NavBaseTarget(component) {

  private val hasDeepLink: Boolean
    get() {
      return component.nlComponent.children?.any { it.tagName == NavigationSchema.TAG_DEEPLINK } ?: false
    }

  private val isStartDestination: Boolean
    get() {
      val parent = component.nlComponent.parent ?: return false

      val startDestination = NlComponent.stripId(parent.getAttribute(SdkConstants.AUTO_URI, ATTR_START_DESTINATION))
      return startDestination?.equals(component.id) ?: false
    }

  override fun getPreferenceLevel(): Int {
    return Target.ANCHOR_LEVEL
  }

  override fun layout(sceneTransform: SceneContext,
                      @AndroidDpCoordinate l: Int,
                      @AndroidDpCoordinate t: Int,
                      @AndroidDpCoordinate r: Int,
                      @AndroidDpCoordinate b: Int): Boolean {
    layoutRectangle(l, t - HEIGHT, r, t)
    return false
  }

  override fun render(list: DisplayList, sceneContext: SceneContext) {
    var l = getSwingLeft(sceneContext)
    val t = getSwingTop(sceneContext)
    val b = getSwingBottom(sceneContext)
    val r = getSwingRight(sceneContext)
    val iconSize = sceneContext.getSwingDimension(HEIGHT.toFloat())

    if (isStartDestination) {
      list.add(DrawIcon(Rectangle(l, t, iconSize, iconSize), DrawIcon.IconType.START_DESTINATION))
      l += iconSize + sceneContext.getSwingDimension(PADDING.toFloat())
    }

    val font = Font(FONT_NAME, FONT_STYLE, sceneContext.getSwingDimension(FONT_SIZE.toFloat()))
    val text = component.nlComponent.id ?: ""
    list.add(DrawScreenLabel(l, b - sceneContext.getSwingDimension(PADDING.toFloat()), font, text))

    if (hasDeepLink) {
      list.add(DrawIcon(Rectangle(r - iconSize, t, iconSize, iconSize), DrawIcon.IconType.DEEPLINK))
    }
  }

  override fun addHit(transform: SceneContext, picker: ScenePicker) {
    picker.addRect(this, 0, getSwingLeft(transform), getSwingTop(transform), getSwingRight(transform), getSwingBottom(transform))
  }

  companion object {
    private const val HEIGHT = 12
    private const val PADDING = 4

    // TODO: finalize values for the following constants
    private const val FONT_NAME: String = "Default"
    private const val FONT_STYLE = Font.PLAIN
    private const val FONT_SIZE = 11
  }
}

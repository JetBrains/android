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
package com.android.tools.idea.common.scene.draw

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.common.SwingFont
import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.scene.SceneContext
import java.awt.Color
import java.awt.Graphics2D

/** [DrawTruncatedText] draws a string in the specified rectangle and truncates if necessary */
data class DrawTruncatedText(
  private val myText: String,
  private val myRectangle: SwingRectangle,
  private val myColor: Color,
  private val myFont: SwingFont,
  private val myIsCentered: Boolean,
) : DrawCommandBase() {

  private var myTruncatedText = ""
  @SwingCoordinate private var myX = 0f
  @SwingCoordinate private var myY = 0f

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    val fontValue = myFont.value
    val fontMetrics = g.getFontMetrics(fontValue)
    val textRectangle = myRectangle.value

    if (myTruncatedText.isEmpty()) {
      myTruncatedText = AdtUiUtils.shrinkToFit(myText, fontMetrics, textRectangle.width)

      myX = textRectangle.x
      myY = textRectangle.y + textRectangle.height

      if (myIsCentered) {
        myX += (textRectangle.width - fontMetrics.stringWidth(myTruncatedText)) / 2
        myY -= (textRectangle.height - fontMetrics.ascent + fontMetrics.descent) / 2
      }
    }

    g.color = myColor
    g.font = fontValue
    g.drawString(myTruncatedText, myX, myY)
  }
}

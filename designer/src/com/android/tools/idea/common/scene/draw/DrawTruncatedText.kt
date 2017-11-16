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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import com.google.common.base.Joiner
import java.awt.*

/**
 * [DrawTruncatedText] draws a string in the specified rectangle and truncates if necessary
 */
private const val ELLIPSIS = "..."

class DrawTruncatedText(private var myLevel: Int,
                        private var myText: String,
                        @SwingCoordinate private val myRectangle: Rectangle,
                        private val myColor: Color,
                        private val myFont: Font,
                        private val myIsCentered: Boolean) : DrawCommand {

  private var myTruncatedText = ""
  @SwingCoordinate private var myX = 0
  @SwingCoordinate private var myY = 0

  constructor(s: String) : this(parse(s, 6))

  private constructor(sp: Array<String>) : this(sp[0].toInt(), sp[1],
      stringToRect(sp[2]), stringToColor(sp[3]), stringToFont(sp[4]), sp[5].toBoolean())

  override fun getLevel(): Int {
    return myLevel
  }

  override fun serialize(): String {
    return Joiner.on(',').join(javaClass.simpleName,
        myLevel,
        myText,
        rectToString(myRectangle),
        colorToString(myColor),
        fontToString(myFont),
        myIsCentered)
  }

  override fun paint(g: Graphics2D, sceneContext: SceneContext) {
    val fontMetrics = g.getFontMetrics(myFont)

    if (myTruncatedText.isEmpty()) {
      myTruncatedText = truncateText(fontMetrics)

      myX = myRectangle.x
      myY = myRectangle.y + myRectangle.height

      if (myIsCentered) {
        myX = (myRectangle.width - fontMetrics.stringWidth(myTruncatedText)) / 2
        myY = (myRectangle.height - fontMetrics.ascent) / 2
      }
    }

    val g2 = g.create()

    g2.color = myColor
    g2.font = myFont
    g2.drawString(myTruncatedText, myX, myY)

    g2.dispose()
  }

  private fun truncateText(metrics: FontMetrics): String {
    if (metrics.stringWidth(myText) <= myRectangle.width) {
      return myText
    }

    val ellipsisWidth = metrics.stringWidth(ELLIPSIS)
    val array = myText.toCharArray()

    for (i in array.size downTo 0) {
      if (metrics.charsWidth(array, 0, i) + ellipsisWidth < myRectangle.width) {
        return String(array, 0, i) + ELLIPSIS
      }
    }

    return ELLIPSIS
  }
}

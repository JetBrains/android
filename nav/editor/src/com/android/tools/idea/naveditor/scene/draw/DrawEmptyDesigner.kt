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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.common.SwingPoint
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DrawCommandBase
import com.android.tools.idea.naveditor.scene.NavColors.TEXT
import com.intellij.util.ui.JBUI
import icons.StudioIcons.NavEditor.Toolbar.ADD_DESTINATION
import java.awt.Font
import java.awt.Graphics2D

private val text1 = "Click "
private val text2 = " to add a destination"
@SwingCoordinate
private val FONT_SIZE = JBUI.scale(13)
@SwingCoordinate
private val VERTICAL_OFFSET = JBUI.scale(3)

class DrawEmptyDesigner(private val point: SwingPoint) : DrawCommandBase() {
  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.color = TEXT
    g.font = Font("Default", 0, JBUI.scale(FONT_SIZE))

    var x = point.x.toInt()
    val y = point.y.toInt()

    g.drawString(text1, x, y)
    x += g.fontMetrics.stringWidth(text1)

    ADD_DESTINATION.paintIcon(null, g, x, y - ADD_DESTINATION.iconHeight + JBUI.scale(VERTICAL_OFFSET))
    x += ADD_DESTINATION.iconWidth

    g.drawString(text2, x, y)
  }
}
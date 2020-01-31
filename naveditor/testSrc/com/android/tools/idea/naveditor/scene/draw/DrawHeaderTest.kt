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
package com.android.tools.idea.naveditor.scene.draw

import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.ui.JBColor
import java.awt.Font
import java.awt.geom.Rectangle2D

private val RECT = Rectangle2D.Float(10f, 20f, 80f, 40f)
private const val SCALE = 1.5f
private const val TEXT = "text"
private val ICON_RECT1 = Rectangle2D.Float(10f, 20f, 21f, 21f)
private val ICON_RECT2 = Rectangle2D.Float(69f, 20f, 21f, 21f)
private val TEXT_RECT = Rectangle2D.Float(34f, 23f, 37f, 15f)
private val TEXT_COLOR = JBColor(0x656565, 0xbababb)
private val FONT = Font("Default", Font.PLAIN, 18)

class DrawHeaderTest : NavTestCase() {
  fun testDrawHeader() {
    val drawHeader = DrawHeader(RECT, SCALE, TEXT, true, true)

    assertEquals(3, drawHeader.commands.size)
    assertEquals(drawHeader.commands[0], DrawIcon(ICON_RECT1, DrawIcon.IconType.START_DESTINATION))
    assertEquals(drawHeader.commands[1], DrawIcon(ICON_RECT2, DrawIcon.IconType.DEEPLINK))
    assertEquals(drawHeader.commands[2], DrawTruncatedText(0, TEXT, TEXT_RECT, TEXT_COLOR, FONT, false))
  }
}

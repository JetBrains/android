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

import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import junit.framework.TestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Point2D

private val CENTER = Point2D.Float(10f, 10f)
private val MAX_DISTANCE = 5

class DrawLineToMouseTest : TestCase() {
  fun testDrawActionHandleDrag() {
    for (x in -MAX_DISTANCE..MAX_DISTANCE) {
      for (y in -MAX_DISTANCE..MAX_DISTANCE) {
        verifyDrawActionHandleDrag(x, y)
      }
    }
  }

  private fun verifyDrawActionHandleDrag(mouseX: Int, mouseY: Int) {
    val sceneContext = mock<SceneContext>(SceneContext::class.java)
    `when`<Int>(sceneContext.getMouseX()).thenReturn(mouseX)
    `when`<Int>(sceneContext.getMouseY()).thenReturn(mouseY)

    val g = mock<Graphics2D>(Graphics2D::class.java)
    `when`<Graphics>(g.create()).thenReturn(g)

    val drawLineToMouse = DrawLineToMouse(CENTER)

    val inOrder = inOrder(g)
    drawLineToMouse.paint(g, sceneContext)

    assertEquals(drawLineToMouse.line.x1, CENTER.x)
    assertEquals(drawLineToMouse.line.y1, CENTER.y)
    assertEquals(drawLineToMouse.line.x2, mouseX.toFloat())
    assertEquals(drawLineToMouse.line.y2, mouseY.toFloat())

    inOrder.verify(g).setColor(SELECTED)
    inOrder.verify(g).setStroke(LINE_TO_MOUSE_STROKE)
    inOrder.verify(g).draw(drawLineToMouse.line)
  }
}
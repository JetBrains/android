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
package com.android.tools.idea.naveditor.scene.decorator

import com.android.tools.idea.common.scene.HitProvider
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawFilledRectangle
import com.android.tools.idea.common.scene.draw.DrawFilledRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawLine
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.common.scene.draw.DrawRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.DEFAULT_FONT_NAME
import com.android.tools.idea.naveditor.scene.DRAW_ACTIVITY_BORDER_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_BACKGROUND_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_FRAME_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_NAV_SCREEN_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_SCREEN_LABEL_LEVEL
import com.android.tools.idea.naveditor.scene.NavColorSet
import org.mockito.Mockito
import java.awt.BasicStroke
import java.awt.Font
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D

class ActivityDecoratorTest : NavTestCase() {
  fun testContent() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        activity("f1")
      }
    }

    val sceneComponent = SceneComponent(model.surface.scene!!, model.find("f1")!!, Mockito.mock(HitProvider::class.java))
    sceneComponent.setPosition(50, 150)
    sceneComponent.setSize(100, 200, false)

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    ActivityDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent)
    val roundRect = RoundRectangle2D.Float(419f, 469f, 50f, 100f, 6f, 6f)
    val stroke = BasicStroke(REGULAR_FRAME_THICKNESS)
    assertEquals(
      listOf(
        DrawFilledRoundRectangle(DRAW_FRAME_LEVEL, roundRect, NavColorSet.COMPONENT_BACKGROUND_COLOR),
        DrawRoundRectangle(DRAW_FRAME_LEVEL, roundRect, NavColorSet.FRAME_COLOR, REGULAR_FRAME_THICKNESS),
        DrawFilledRectangle(DRAW_BACKGROUND_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f), NavColorSet.PLACEHOLDER_BACKGROUND_COLOR),
        DrawLine(DRAW_NAV_SCREEN_LEVEL, Point2D.Float(423f, 473f), Point2D.Float(465f, 556f), NavColorSet.PLACEHOLDER_BORDER_COLOR, stroke),
        DrawLine(DRAW_NAV_SCREEN_LEVEL, Point2D.Float(423f, 556f), Point2D.Float(465f, 473f), NavColorSet.PLACEHOLDER_BORDER_COLOR, stroke),
        DrawRectangle(DRAW_ACTIVITY_BORDER_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f), NavColorSet.ACTIVITY_BORDER_COLOR,
                      ACTIVITY_BORDER_WIDTH),
        DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Activity", Rectangle2D.Float(419f, 556f, 50f, 13f), NavColorSet.TEXT_COLOR,
                          Font(DEFAULT_FONT_NAME, Font.BOLD, 9), true)),
      displayList.commands)
  }

  fun testHighlightedContent() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        activity("f1")
      }
    }

    val sceneComponent = SceneComponent(model.surface.scene!!, model.find("f1")!!, Mockito.mock(HitProvider::class.java))
    sceneComponent.setPosition(50, 150)
    sceneComponent.setSize(100, 200, false)
    sceneComponent.drawState = SceneComponent.DrawState.SELECTED

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    ActivityDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent)
    val roundRect = RoundRectangle2D.Float(419f, 469f, 50f, 100f, 6f, 6f)
    val stroke = BasicStroke(REGULAR_FRAME_THICKNESS)
    assertEquals(
      listOf(
        DrawFilledRoundRectangle(DRAW_FRAME_LEVEL, roundRect, NavColorSet.COMPONENT_BACKGROUND_COLOR),
        DrawRoundRectangle(DRAW_FRAME_LEVEL, roundRect, NavColorSet.SELECTED_FRAME_COLOR, HIGHLIGHTED_FRAME_THICKNESS),
        DrawFilledRectangle(DRAW_BACKGROUND_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f), NavColorSet.PLACEHOLDER_BACKGROUND_COLOR),
        DrawLine(DRAW_NAV_SCREEN_LEVEL, Point2D.Float(423f, 473f), Point2D.Float(465f, 556f), NavColorSet.PLACEHOLDER_BORDER_COLOR, stroke),
        DrawLine(DRAW_NAV_SCREEN_LEVEL, Point2D.Float(423f, 556f), Point2D.Float(465f, 473f), NavColorSet.PLACEHOLDER_BORDER_COLOR, stroke),
        DrawRectangle(DRAW_ACTIVITY_BORDER_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f), NavColorSet.ACTIVITY_BORDER_COLOR,
                      ACTIVITY_BORDER_WIDTH),
        DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Activity", Rectangle2D.Float(419f, 556f, 50f, 13f), NavColorSet.TEXT_COLOR,
                          Font(DEFAULT_FONT_NAME, Font.BOLD, 9), true)),
      displayList.commands)

  }
}
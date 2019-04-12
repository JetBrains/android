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
import com.android.tools.idea.common.scene.draw.DrawFilledRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawRectangle
import com.android.tools.idea.common.scene.draw.DrawRoundRectangle
import com.android.tools.idea.common.scene.draw.DrawTruncatedText
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.DEFAULT_FONT_NAME
import com.android.tools.idea.naveditor.scene.DRAW_ACTIVITY_BORDER_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_FRAME_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_NAV_SCREEN_LEVEL
import com.android.tools.idea.naveditor.scene.DRAW_SCREEN_LABEL_LEVEL
import com.android.tools.idea.naveditor.scene.NavColors.ACTIVITY_BORDER
import com.android.tools.idea.naveditor.scene.NavColors.COMPONENT_BACKGROUND
import com.android.tools.idea.naveditor.scene.NavColors.FRAME
import com.android.tools.idea.naveditor.scene.NavColors.SELECTED
import com.android.tools.idea.naveditor.scene.NavColors.TEXT
import com.android.tools.idea.naveditor.scene.draw.DrawPlaceholder
import org.mockito.Mockito
import java.awt.Font
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
    sceneComponent.setSize(100, 200)

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    ActivityDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent)
    val roundRect = RoundRectangle2D.Float(419f, 469f, 50f, 100f, 6f, 6f)
    assertEquals(
      listOf(
        DrawFilledRoundRectangle(DRAW_FRAME_LEVEL, roundRect, COMPONENT_BACKGROUND),
        DrawRoundRectangle(DRAW_FRAME_LEVEL, roundRect, FRAME, REGULAR_FRAME_THICKNESS),
        DrawPlaceholder(DRAW_NAV_SCREEN_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f)),
        DrawRectangle(DRAW_ACTIVITY_BORDER_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f), ACTIVITY_BORDER,
                      ACTIVITY_BORDER_WIDTH),
        DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Activity", Rectangle2D.Float(419f, 556f, 50f, 13f), TEXT,
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
    sceneComponent.setSize(100, 200)
    sceneComponent.drawState = SceneComponent.DrawState.SELECTED

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    ActivityDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), sceneComponent)
    val roundRect = RoundRectangle2D.Float(419f, 469f, 50f, 100f, 6f, 6f)
    assertEquals(
      listOf(
        DrawFilledRoundRectangle(DRAW_FRAME_LEVEL, roundRect, COMPONENT_BACKGROUND),
        DrawRoundRectangle(DRAW_FRAME_LEVEL, roundRect, SELECTED, HIGHLIGHTED_FRAME_THICKNESS),
        DrawPlaceholder(DRAW_NAV_SCREEN_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f)),
        DrawRectangle(DRAW_ACTIVITY_BORDER_LEVEL, Rectangle2D.Float(423f, 473f, 42f, 83f), ACTIVITY_BORDER,
                      ACTIVITY_BORDER_WIDTH),
        DrawTruncatedText(DRAW_SCREEN_LABEL_LEVEL, "Activity", Rectangle2D.Float(419f, 556f, 50f, 13f), TEXT,
                          Font(DEFAULT_FONT_NAME, Font.BOLD, 9), true)),
      displayList.commands)

  }
}
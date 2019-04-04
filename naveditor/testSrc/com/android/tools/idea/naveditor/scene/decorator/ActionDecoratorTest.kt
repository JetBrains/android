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

import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.ArrowDirection
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.scene.draw.DrawArrow
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.DRAW_ACTION_LEVEL
import com.android.tools.idea.naveditor.scene.NavColors.ACTION
import com.android.tools.idea.naveditor.scene.draw.DrawAction
import com.android.tools.idea.naveditor.scene.draw.DrawIcon
import com.android.tools.idea.naveditor.scene.draw.DrawSelfAction
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

class ActionDecoratorTest : NavTestCase() {
  fun testRegularPopAction() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("f1") {
          action("f1_to_f2", popUpTo = "f2")
        }
        fragment("f2")
      }
    }

    val f1 = model.surface.sceneManager?.scene?.getSceneComponent(model.find("f1"))!!
    f1.setPosition(50, 150)
    f1.setSize(100, 200)

    val f2 = model.surface.sceneManager?.scene?.getSceneComponent(model.find("f1"))!!
    f2.setPosition(50, 450)
    f2.setSize(100, 200)

    val f1_to_f2 = model.surface.sceneManager?.scene?.getSceneComponent(model.find("f1_to_f2"))!!

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    ActionDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), f1_to_f2)

    val drawAction = displayList.commands[0]
    assertNotNull(drawAction as DrawAction) // TODO: Convert DrawAction to data class
    assertEquals(DrawArrow(DRAW_ACTION_LEVEL, ArrowDirection.UP, Rectangle2D.Float(561.75f, 532.0f, 6.0f, 5.0f), ACTION),
                 displayList.commands[1])
    assertEquals(DrawIcon(Rectangle2D.Float(487.2728f, 654.0313f, 8.0f, 8.0f), DrawIcon.IconType.POP_ACTION, ACTION),
                 displayList.commands[2])
  }

  fun testSelfPopAction() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("f1") {
          action("f1_to_f1", popUpTo = "f1", destination = "f1")
        }
      }
    }

    val f1 = model.surface.sceneManager?.scene?.getSceneComponent(model.find("f1"))!!
    f1.setPosition(50, 150)
    f1.setSize(100, 200)

    val f1_to_f1 = model.surface.sceneManager?.scene?.getSceneComponent(model.find("f1_to_f1"))!!

    val sceneView = model.surface.currentSceneView!!
    val displayList = DisplayList()

    ActionDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), f1_to_f1)

    assertEquals(DrawArrow(DRAW_ACTION_LEVEL, ArrowDirection.UP, Rectangle2D.Float(440f, 573.0f, 6.0f, 5.0f), ACTION),
                 displayList.commands[0])
    assertEquals(DrawSelfAction(Point2D.Float(459f, 519f), Point2D.Float(443f, 577f), ACTION),
                 displayList.commands[1])
    assertEquals(DrawIcon(Rectangle2D.Float(477f, 536.34937f, 8.0f, 8.0f), DrawIcon.IconType.POP_ACTION, ACTION),
                 displayList.commands[2])
  }
}

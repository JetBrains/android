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

import com.android.tools.adtui.common.SwingRectangle
import com.android.tools.idea.common.model.Scale
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.NavColors.ACTION
import com.android.tools.idea.naveditor.scene.draw.DrawAction
import com.android.tools.idea.naveditor.scene.draw.DrawSelfAction
import com.android.tools.idea.naveditor.scene.draw.assertDrawCommandsEqual
import org.mockito.Mockito
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

private val SCALE = Scale(0.5)

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

    val sceneView = model.surface.focusedSceneView!!
    val displayList = DisplayList()

    ActionDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), f1_to_f2)

    assertDrawCommandsEqual(DrawAction(SwingRectangle(Rectangle2D.Float(419f, 619f, 50f, 100f)),
                                       SwingRectangle(Rectangle2D.Float(526.5f, 400f, 76.5f, 128f)),
                                       SCALE, ACTION, true), displayList.commands[0])
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

    val sceneView = model.surface.focusedSceneView!!
    val displayList = DisplayList()

    ActionDecorator.buildListComponent(displayList, 0, SceneContext.get(sceneView), f1_to_f1)

    assertDrawCommandsEqual(DrawSelfAction(SwingRectangle(Rectangle2D.Float(409f, 469.0f, 50.0f, 100.0f)), SCALE, ACTION, true),
                            displayList.commands[0])
  }

  fun testInvalidComponent() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("f1") {
          action("f1_to_f2", popUpTo = "f2")
        }
      }
    }

    val f1_to_f2 = model.surface.sceneManager?.scene?.getSceneComponent(model.find("f1_to_f2"))!!
    f1_to_f2.setPosition(0, 0)
    f1_to_f2.setSize(0, 0)

    val sceneView = model.surface.focusedSceneView!!
    val context = SceneContext.get(sceneView)
    val displayList = DisplayList()
    ActionDecorator.buildListComponent(displayList, 0, context, f1_to_f2)

    val graphics = Mockito.mock(Graphics2D::class.java)
    Mockito.`when`(graphics.create()).thenReturn(graphics)

    displayList.paint(graphics, context)
  }
}

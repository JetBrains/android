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
package com.android.tools.idea.naveditor.editor

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.actions.SelectAllAction
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.actions.SelectNextAction
import com.android.tools.idea.uibuilder.actions.SelectPreviousAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock

/**
 * Tests for actions used by the nav editor
 */
class SelectActionsTest : NavTestCase() {
  fun testSelectNextAction() {
    val model = model("nav.xml") {
      navigation("root") {
        action("action1", destination = "fragment1")
        fragment("fragment1") {
          action("action2", destination = "fragment2")
        }
        fragment("fragment2")
        fragment("fragment3")
        navigation("nested") {
          action("action3", destination = "fragment2")
          fragment("fragment4") {
            action("action4", destination = "fragment1")
            action("action5", destination = "fragment4")
          }
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    surface.model = model

    val action = SelectNextAction(surface)

    performAction(action, surface, "action1")
    performAction(action, surface, "fragment1")
    performAction(action, surface, "action2")
    performAction(action, surface, "fragment2")
    performAction(action, surface, "fragment3")
    performAction(action, surface, "nested")
    performAction(action, surface, "action3")
    performAction(action, surface, "action4")
    performAction(action, surface, "root")
    performAction(action, surface, "action1")
  }

  fun testSelectPreviousAction() {
    val model = model("nav.xml") {
      navigation("root") {
        action("action1", destination = "fragment1")
        fragment("fragment1") {
          action("action2", destination = "fragment2")
        }
        fragment("fragment2")
        fragment("fragment3")
        navigation("nested") {
          action("action3", destination = "fragment2")
          fragment("fragment4") {
            action("action4", destination = "fragment1")
            action("action5", destination = "fragment4")
          }
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    surface.model = model

    val action = SelectPreviousAction(surface)

    performAction(action, surface, "action4")
    performAction(action, surface, "action3")
    performAction(action, surface, "nested")
    performAction(action, surface, "fragment3")
    performAction(action, surface, "fragment2")
    performAction(action, surface, "action2")
    performAction(action, surface, "fragment1")
    performAction(action, surface, "action1")
    performAction(action, surface, "root")
    performAction(action, surface, "action4")
  }

  fun testSelectAllAction() {
    val model = model("nav.xml") {
      navigation {
        action("action1")
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }

    val fragment1 = model.find("fragment1")!!
    val fragment2 = model.find("fragment2")!!
    val fragment3 = model.find("fragment3")!!

    val surface = model.surface as NavDesignSurface
    val action = SelectAllAction(surface)

    action.actionPerformed(mock(AnActionEvent::class.java))
    assertEquals(listOf(fragment1, fragment2, fragment3), model.surface.selectionModel.selection)
  }

  private fun performAction(action: AnAction, surface: NavDesignSurface, id: String) {
    action.actionPerformed(mock(AnActionEvent::class.java))
    val component = surface.model?.find(id)!!
    AndroidTestCase.assertEquals(listOf(component), surface.selectionModel.selection)
  }
}
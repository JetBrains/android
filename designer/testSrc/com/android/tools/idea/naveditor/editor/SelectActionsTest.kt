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
import com.android.tools.idea.naveditor.actions.SelectNextAction
import com.android.tools.idea.naveditor.actions.SelectPreviousAction
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.AnActionEvent
import org.mockito.Mockito.mock

/**
 * Tests for actions used by the nav editor
 */
class SelectNextActionTest : NavTestCase() {

  fun testSelectNextAction() {
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
    val action = SelectNextAction(surface)

    val event = mock(AnActionEvent::class.java)
    action.actionPerformed(event)
    assertEquals(listOf(fragment1), model.surface.selectionModel.selection)

    action.actionPerformed(event)
    assertEquals(listOf(fragment2), model.surface.selectionModel.selection)

    action.actionPerformed(event)
    assertEquals(listOf(fragment3), model.surface.selectionModel.selection)

    action.actionPerformed(event)
    assertEquals(listOf(fragment1), model.surface.selectionModel.selection)
  }
}

class SelectPreviousActionTest : NavTestCase() {
  fun testSelectPreviousAction() {
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
    val action = SelectPreviousAction(surface)

    val event = mock(AnActionEvent::class.java)
    action.actionPerformed(event)
    assertEquals(listOf(fragment3), model.surface.selectionModel.selection)

    action.actionPerformed(event)
    assertEquals(listOf(fragment2), model.surface.selectionModel.selection)

    action.actionPerformed(event)
    assertEquals(listOf(fragment1), model.surface.selectionModel.selection)

    action.actionPerformed(event)
    assertEquals(listOf(fragment3), model.surface.selectionModel.selection)
  }
}

class SelectAllActionTest : NavTestCase() {

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

}
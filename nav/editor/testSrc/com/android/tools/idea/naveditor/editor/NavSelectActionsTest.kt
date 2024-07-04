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

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.uibuilder.actions.SelectAllAction
import com.android.tools.idea.uibuilder.actions.SelectNextAction
import com.android.tools.idea.uibuilder.actions.SelectPreviousAction
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.android.AndroidTestCase

/**
 * Tests for actions used by the nav editor
 */
class NavSelectActionsTest : NavTestCase() {
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
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    val action = SelectNextAction()

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
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    val action = SelectPreviousAction()

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
      navigation("root") {
        action("action1")
        fragment("fragment1")
        fragment("fragment2")
        fragment("fragment3")
      }
    }

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    surface.selectionModel.setSelection(ImmutableList.of())

    val root = model.treeReader.find("root")!!
    val action1 = model.treeReader.find("action1")!!
    val fragment1 = model.treeReader.find("fragment1")!!
    val fragment2 = model.treeReader.find("fragment2")!!
    val fragment3 = model.treeReader.find("fragment3")!!

    val action = SelectAllAction()

    action.actionPerformed(TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) surface else null })
    assertEquals(listOf(root, action1, fragment1, fragment2, fragment3), surface.selectionModel.selection)
  }

  private fun performAction(action: AnAction, surface: NavDesignSurface, id: String) {
    action.actionPerformed(TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) surface else null })
    val component = surface.model?.treeReader?.find(id)!!
    AndroidTestCase.assertEquals(listOf(component), surface.selectionModel.selection)
  }
}
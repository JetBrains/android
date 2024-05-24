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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent

class StartDestinationToolbarActionTest : NavTestCase() {

  fun testStartDestinationToolbarAction() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1")
      }
    }

    val component = model.treeReader.find("fragment1")!!

    val surface = model.surface as NavDesignSurface
    surface.selectionModel.setSelection(listOf(component))

    val dataContext = SimpleDataContext.builder()
      .add(DESIGN_SURFACE, surface)
      .build()

    val action = StartDestinationToolbarAction.instance
    val actionEvent = TestActionEvent(dataContext)
    action.actionPerformed(actionEvent)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
      """.trimIndent(),
      NlTreeDumper().toTree(model.treeReader.components)
    )

    assert(component.isStartDestination)
  }

  fun testActivityCannotBeStartDestination() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1")
        activity("activity1")
        fragment("fragment2")
      }
    }

    val fragment1 = model.treeReader.find("fragment1")!!

    val surface = model.surface as NavDesignSurface
    surface.selectionModel.setSelection(listOf(fragment1))

    val action = StartDestinationToolbarAction.instance
    val dataContext = SimpleDataContext.builder()
      .add(DESIGN_SURFACE, surface)
      .build()
    val actionEvent = TestActionEvent(dataContext)
    action.actionPerformed(actionEvent)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
            NlComponent{tag=<activity>, instance=2}
            NlComponent{tag=<fragment>, instance=3}
      """.trimIndent(),
      NlTreeDumper().toTree(model.treeReader.components)
    )

    assert(fragment1.isStartDestination)

    val fragment2 = model.treeReader.find("fragment2")!!
    surface.selectionModel.setSelection(listOf(fragment2))
    action.actionPerformed(actionEvent)
    assert(fragment2.isStartDestination)

    val activity1 = model.treeReader.find("activity1")!!
    surface.selectionModel.setSelection(listOf(activity1))
    action.actionPerformed(actionEvent)
    // Activity cannot be a start destination, so fragment2 should still be selected.
    assert(fragment2.isStartDestination)
  }
}
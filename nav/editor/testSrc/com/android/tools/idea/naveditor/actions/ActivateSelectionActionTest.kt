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
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class ActivateSelectionActionTest : NavTestCase() {
  fun testActivateSelectionAction() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment")
        navigation("nested")
      }
    }

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    val spy = spy(surface)
    val action = ActivateSelectionAction()
    val event = TestActionEvent.createTestEvent { if (DESIGN_SURFACE.`is`(it)) spy else null }

    surface.selectionModel.setSelection(listOf())
    action.actionPerformed(event)
    verify(spy, never()).notifyComponentActivate(any())

    val nested = model.treeReader.find("nested")!!
    val fragment = model.treeReader.find("fragment")!!

    surface.selectionModel.setSelection(listOf(nested, fragment))
    action.actionPerformed(event)
    verify(spy, never()).notifyComponentActivate(any())

    surface.selectionModel.setSelection(listOf(nested))
    action.actionPerformed(event)
    verify(spy).notifyComponentActivate(nested)
  }
}
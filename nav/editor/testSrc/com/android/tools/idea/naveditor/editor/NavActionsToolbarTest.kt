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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.Collections

class NavActionsToolbarTest : NavTestCase() {

  fun testAddActions() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1") {
          action("a1", "f2")
        }
        fragment("f2")
        activity("a1")
      }
    }

    val actionManager = mock(NavActionManager::class.java)
    val surface = model.surface

    surface.selectionModel.clear()
    val issueModel = mock(IssueModel::class.java)
    whenever(surface.issueModel).thenReturn(issueModel)
    whenever(surface.actionManager).thenReturn(actionManager)
    whenever(actionManager.getPopupMenuActions(any())).thenReturn(DefaultActionGroup())
    // We use any ?: Collections.emptyList() below because any() returns null and Kotlin will
    // complain during the null checking
    whenever(actionManager.getToolbarActions(Mockito.any() ?: Collections.emptyList())).thenReturn(DefaultActionGroup())
    ActionsToolbar(project, surface)

    val components = listOf(model.treeReader.find("root")!!)

    verify(actionManager).getToolbarActions(eq(components))

    val f1 = listOf(model.treeReader.find("f1")!!)
    surface.selectionModel.setSelection(f1)

    verify(actionManager).getToolbarActions(eq(f1))

    val f1AndRoot = listOf(model.treeReader.find("f1")!!, model.treeReader.components[0])
    surface.selectionModel.setSelection(f1AndRoot)

    verify(actionManager).getToolbarActions(eq(f1AndRoot))
  }

  fun <T> any(): T = Mockito.any() as T
  fun <T> eq(v: T): T = Mockito.eq(v) as T
}
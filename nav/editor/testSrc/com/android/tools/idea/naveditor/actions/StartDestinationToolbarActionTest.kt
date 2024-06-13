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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.AnActionEvent
import org.mockito.Mockito

class StartDestinationToolbarActionTest : NavTestCase() {

  fun testStartDestinationToolbarAction() {
    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation {
        fragment("fragment1")
      }
    }

    val component = model.find("fragment1")!!

    val surface = model.surface as NavDesignSurface
    surface.selectionModel.setSelection(listOf(component))

    val action = StartDestinationToolbarAction.instance
    val actionEvent = Mockito.mock(AnActionEvent::class.java)
    whenever(actionEvent.getData(DESIGN_SURFACE)).thenReturn(surface)
    action.actionPerformed(actionEvent)

    assertEquals(
      """
        NlComponent{tag=<navigation>, instance=0}
            NlComponent{tag=<fragment>, instance=1}
      """.trimIndent(),
      NlTreeDumper().toTree(model.components)
    )

    assert(component.isStartDestination)
  }
}
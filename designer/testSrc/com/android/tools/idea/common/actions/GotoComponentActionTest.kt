/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.common.actions

import com.android.SdkConstants
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.model.DefaultSelectionModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class GotoComponentActionTest {
  @get:Rule val projectRule = AndroidProjectRule.Companion.inMemory()

  private lateinit var model: NlModel
  private lateinit var nlDesignSurface: NlDesignSurface
  private lateinit var designSplitEditor: DesignToolsSplitEditor

  @Before
  fun setUp() {
    model =
      NlModelBuilderUtil.model(
          projectRule,
          SdkConstants.FD_RES_LAYOUT,
          "layout.xml",
          ComponentUtil.component(SdkConstants.LINEAR_LAYOUT),
        )
        .build()
    val selectionModel = DefaultSelectionModel()
    nlDesignSurface = mock {
      on { it.project }.thenReturn(projectRule.project)
      on { it.selectionModel }.thenReturn(selectionModel)
      on { it.models }.thenReturn(listOf(model))
    }
    projectRule.fixture.configureFromExistingVirtualFile(model.virtualFile)

    designSplitEditor = mock { on { it.isDesignMode() }.thenReturn(true) }
  }

  @Test
  fun `action selects split mode if in design mode`() = runTest {
    val gotoAction = GotoComponentAction() { designSplitEditor }
    val testEvent =
      TestActionEvent.createTestEvent(
        gotoAction,
        SimpleDataContext.getSimpleContext(DESIGN_SURFACE, nlDesignSurface),
      )

    withContext(Dispatchers.EDT) { gotoAction.actionPerformed(testEvent) }

    verify(designSplitEditor).selectSplitMode(false)
    // Regression test for b/404201578
    // In the past this action would have deactivated the surface but this is not needed and causes
    // the surface to become unresponsive after using the action.
    verify(nlDesignSurface, times(0)).deactivate()
  }
}

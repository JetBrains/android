/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.TestActionEvent.createTestEvent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SetColorBlindModeActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  // Regression test for b/274076939
  @Test
  fun testColorBlindModeChange() {
    val model = invokeAndWaitIfNeeded {
      NlModelBuilderUtil.model(
          AndroidBuildTargetReference.gradleOnly(projectRule.module.androidFacet!!),
          projectRule.fixture,
          SdkConstants.FD_RES_LAYOUT,
          "model.xml",
          ComponentDescriptor("LinearLayout"),
        )
        .build()
    }
    val surface = NlSurfaceBuilder.build(projectRule.project, projectRule.testRootDisposable)
    surface.addModelWithoutRender(model).join()

    val setColorBlindModeAction = SetColorBlindModeAction(ColorBlindMode.PROTANOPES)
    val event =
      createTestEvent(
        DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, surface)
      )

    assertEquals(ColorBlindMode.NONE, surface.colorBlindMode)

    setColorBlindModeAction.setSelected(event, true)
    assertEquals(ColorBlindMode.PROTANOPES, surface.colorBlindMode)

    setColorBlindModeAction.setSelected(event, false)
    assertEquals(ColorBlindMode.NONE, surface.colorBlindMode)
  }
}

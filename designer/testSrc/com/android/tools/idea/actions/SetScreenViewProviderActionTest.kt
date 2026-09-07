/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.TestActionEvent.createTestEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetScreenViewProviderActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testKeepPopupOnPerformIsNever() {
    val action = SetScreenViewProviderAction(NlScreenViewProvider.BLUEPRINT)
    assertEquals(KeepPopupOnPerform.Never, action.templatePresentation.keepPopupOnPerform)
  }

  @Test
  fun testScreenViewProviderChange() {
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
    surface.addModelsWithoutRender(listOf(model))

    val setBlueprintAction = SetScreenViewProviderAction(NlScreenViewProvider.BLUEPRINT)
    val setRenderAction = SetScreenViewProviderAction(NlScreenViewProvider.RENDER)
    val setRenderAndBlueprintAction = SetScreenViewProviderAction(NlScreenViewProvider.RENDER_AND_BLUEPRINT)
    val event = createTestEvent(DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, surface))

    assertEquals(NlScreenViewProvider.RENDER_AND_BLUEPRINT, surface.screenViewProvider)
    assertTrue(setRenderAndBlueprintAction.isSelected(event))
    assertFalse(setRenderAction.isSelected(event))
    assertFalse(setBlueprintAction.isSelected(event))

    setBlueprintAction.setSelected(event, true)
    assertEquals(NlScreenViewProvider.BLUEPRINT, surface.screenViewProvider)
    assertFalse(setRenderAndBlueprintAction.isSelected(event))
    assertFalse(setRenderAction.isSelected(event))
    assertTrue(setBlueprintAction.isSelected(event))

    setRenderAction.setSelected(event, true)
    assertEquals(NlScreenViewProvider.RENDER, surface.screenViewProvider)
    assertFalse(setRenderAndBlueprintAction.isSelected(event))
    assertTrue(setRenderAction.isSelected(event))
    assertFalse(setBlueprintAction.isSelected(event))
  }
}

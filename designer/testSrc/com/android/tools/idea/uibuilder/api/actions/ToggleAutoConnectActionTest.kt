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
package com.android.tools.idea.uibuilder.api.actions

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito

class ToggleAutoConnectActionTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()

    registerApplicationService(PropertiesComponent::class.java, PropertiesComponentMock())
  }

  fun testToggle() {
    val action = ToggleAutoConnectAction()

    val mockEditor =
      Mockito.mock(ViewEditor::class.java).also {
        val mockScene = Mockito.mock(Scene::class.java)
        whenever(it.scene).thenReturn(mockScene)
        whenever(mockScene.designSurface).thenReturn(null)
      }

    val mockHandler = Mockito.mock(ViewHandler::class.java)
    val mockParent = Mockito.mock(NlComponent::class.java)
    val selectedChildren = emptyList<NlComponent>()

    assertEquals(
      ToggleAutoConnectAction.isAutoconnectOn(),
      action.isSelected(mockEditor, mockHandler, mockParent, selectedChildren),
    )

    action.setSelected(mockEditor, mockHandler, mockParent, selectedChildren, true)
    assertTrue(ToggleAutoConnectAction.isAutoconnectOn())

    action.setSelected(mockEditor, mockHandler, mockParent, selectedChildren, false)
    assertFalse(ToggleAutoConnectAction.isAutoconnectOn())
  }
}

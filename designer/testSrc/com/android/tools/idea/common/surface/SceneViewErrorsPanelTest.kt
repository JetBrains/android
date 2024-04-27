/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.swing.FakeUi
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

class SceneViewErrorsPanelTest {

  private lateinit var fakeUi: FakeUi
  private lateinit var panelParent: JPanel

  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  @Before
  fun setUp() {
    invokeAndWaitIfNeeded {
      panelParent =
        JPanel().apply {
          layout = BorderLayout()
          size = Dimension(1000, 800)
        }
      fakeUi = FakeUi(panelParent, 1.0, true)
      fakeUi.root.validate()
    }
  }

  @Test
  fun testVisibilityIsControlledByConstructorParameter() {
    var panelStyle = SceneViewErrorsPanel.Style.SOLID
    val sceneViewErrorsPanel = SceneViewErrorsPanel { panelStyle }
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)

    assertTrue(sceneViewErrorsPanel.isVisible)
    panelStyle = SceneViewErrorsPanel.Style.HIDDEN
    assertFalse(sceneViewErrorsPanel.isVisible)
  }

  @Test
  fun testPanelComponents() {
    val sceneViewErrorsPanel = SceneViewErrorsPanel()
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)
    invokeAndWaitIfNeeded { fakeUi.root.validate() }

    assertNotNull(fakeUi.findComponent<JBLabel> { it.text.contains("Render problem") })
  }

  @Test
  fun testPreferredAndMinimumSizes() {
    val sceneViewErrorsPanel = SceneViewErrorsPanel()
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)

    assertEquals(35, sceneViewErrorsPanel.minimumSize.height)
    assertEquals(150, sceneViewErrorsPanel.minimumSize.width)
    assertEquals(35, sceneViewErrorsPanel.preferredSize.height)
    assertEquals(150, sceneViewErrorsPanel.preferredSize.width)
  }
}

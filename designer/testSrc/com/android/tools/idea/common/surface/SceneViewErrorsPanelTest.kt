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
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.util.Disposer
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

class SceneViewErrorsPanelTest {

  private lateinit var testDisposable: Disposable

  private lateinit var fakeUi: FakeUi
  private lateinit var panelParent: JPanel

  @Before
  fun setUp() {
    testDisposable = Disposer.newDisposable()
    ApplicationManager.setApplication(MockApplication(testDisposable), testDisposable)
    invokeAndWaitIfNeeded {
      panelParent = JPanel().apply {
        layout = BorderLayout()
        size = Dimension(1000, 800)
      }
      fakeUi = FakeUi(panelParent, 1.0, true)
      fakeUi.root.validate()
    }
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun testVisibilityIsControlledByConstructorParameter() {
    var isPanelVisible = true
    val sceneViewErrorsPanel = SceneViewErrorsPanel { isPanelVisible }
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)

    assertTrue(sceneViewErrorsPanel.isVisible)
    isPanelVisible = false
    assertFalse(sceneViewErrorsPanel.isVisible)
  }

  @Test
  fun testPanelComponents() {
    val sceneViewErrorsPanel = SceneViewErrorsPanel()
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)
    invokeAndWaitIfNeeded { fakeUi.root.validate() }

    assertNotNull(fakeUi.findComponent<JBLabel> { it.text.contains("Some issues were found when trying to render this preview") })
    assertNotNull(fakeUi.findComponent<JBLabel> { it.icon == StudioIcons.Common.ERROR })
    val hyperlink = fakeUi.findComponent<HyperlinkLabel>()!!
    assertEquals("Open Issues Panel (I)", hyperlink.highlightedRegionsBoundsMap.keys.single())
  }

  @Test
  fun testPreferredAndMinimumSizes() {
    val sceneViewErrorsPanel = SceneViewErrorsPanel()
    panelParent.add(sceneViewErrorsPanel, BorderLayout.CENTER)

    assertEquals(200, sceneViewErrorsPanel.minimumSize.height)
    assertEquals(200, sceneViewErrorsPanel.minimumSize.width)
    assertEquals(200, sceneViewErrorsPanel.preferredSize.height)
    assertEquals(200, sceneViewErrorsPanel.preferredSize.width)
  }
}
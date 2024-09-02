/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.common.surface.sceneview

import com.android.tools.idea.common.model.DisplaySettings
import com.intellij.openapi.application.runInEdt
import com.intellij.testFramework.ApplicationRule
import java.awt.Dimension
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LabelPanelTest {

  @Rule @JvmField val rule = ApplicationRule()
  private lateinit var scope: CoroutineScope

  @Before
  fun setUp() {
    scope = CoroutineScope(CoroutineName(javaClass.simpleName))
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun `change name`() = runInEdt {
    val settings =
      DisplaySettings().apply {
        setDisplayName("displayName")
        setParameterName("parameterName")
        setTooltip("Tooltip")
      }
    val organizationEnabled = MutableStateFlow(false)
    val label = LabelPanel(settings, scope, organizationEnabled).apply { size = Dimension(250, 50) }
    assertTrue(label.isVisible)
    assertEquals("displayName", label.text)
    assertEquals("Tooltip", label.text)

    settings.setDisplayName("New displayName")
    assertEquals("New displayName", label.text)

    settings.setTooltip(null)
    assertEquals("New displayName", label.toolTipText)

    organizationEnabled.value = true
    assertEquals("parameterName", label.text)
    assertEquals("parameterName", label.toolTipText)

    settings.setParameterName(null)
    assertEquals("New displayName", label.text)
    assertEquals("New displayName", label.toolTipText)
  }

  @Test
  fun `change visibility`() = runInEdt {
    val settings = DisplaySettings().apply { setTooltip("Tooltip") }
    val label =
      LabelPanel(settings, scope, MutableStateFlow(false)).apply { size = Dimension(250, 50) }
    assertFalse(label.isVisible)

    settings.setDisplayName("Name")
    assertTrue(label.isVisible)
  }

  @Test
  fun `not updated when disposed`() = runInEdt {
    val scope = CoroutineScope(CoroutineName(javaClass.simpleName))
    val settings =
      DisplaySettings().apply {
        setDisplayName("displayName")
        setParameterName("parameterName")
        setTooltip("Tooltip")
      }
    val label =
      LabelPanel(settings, scope, MutableStateFlow(false)).apply { size = Dimension(250, 50) }
    scope.cancel()
    settings.setDisplayName("New Name")
    // Still old name
    assertEquals("displayName", label.text)
  }
}

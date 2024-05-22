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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.swing.FakeUi
import com.intellij.openapi.application.invokeAndWaitIfNeeded
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
  fun `change name`() {
    val displayName = MutableStateFlow("Name")
    val tooltip = MutableStateFlow("Tooltip")
    val label = LabelPanel(displayName, tooltip, scope).apply { size = Dimension(250, 50) }
    val ui = FakeUi(label)
    invokeAndWaitIfNeeded { ui.layout() }
    assertTrue(label.isVisible)
    assertEquals("Name", label.text)
    displayName.value = "New Name"
    invokeAndWaitIfNeeded { ui.layout() }
    assertEquals("New Name", label.text)
  }

  @Test
  fun `change visibility`() {
    val displayName = MutableStateFlow<String?>(null)
    val tooltip = MutableStateFlow<String?>("Tooltip")
    val label = LabelPanel(displayName, tooltip, scope).apply { size = Dimension(250, 50) }
    val ui = FakeUi(label)
    invokeAndWaitIfNeeded { ui.layout() }
    assertFalse(label.isVisible)
    displayName.value = "Name"
    invokeAndWaitIfNeeded { ui.layout() }
    assertTrue(label.isVisible)
  }

  @Test
  fun `not updated when disposed`() {
    val scope = CoroutineScope(CoroutineName(javaClass.simpleName))
    val displayName = MutableStateFlow("Name")
    val tooltip = MutableStateFlow("Tooltip")
    val label = LabelPanel(displayName, tooltip, scope).apply { size = Dimension(250, 50) }
    val ui = FakeUi(label)
    invokeAndWaitIfNeeded { ui.layout() }
    scope.cancel()
    displayName.value = "New Name"
    invokeAndWaitIfNeeded { ui.layout() }
    // Still old name
    assertEquals("Name", label.text)
  }
}

/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.util.InspectorBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.JComponent

@RunsInEdt
class DeviceViewPanelTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    InspectorBuilder.setUpDemo(projectRule)
  }

  @After
  fun tearDown() {
    InspectorBuilder.tearDownDemo()
  }

  @Test
  fun testFocusableActionButtons() {
    val inspector = InspectorBuilder.createLayoutInspectorForDemo(projectRule)
    val settings = DeviceViewSettings()
    val panel = DeviceViewPanel(inspector, settings, projectRule.fixture.projectDisposable)
    val toolbarPanel = findComponentAt(panel, BorderLayout.NORTH)
    val leftPanel = findComponentAt(toolbarPanel, BorderLayout.CENTER)
    val actionToolBar = findComponentAt(leftPanel, BorderLayout.CENTER)
    actionToolBar.components.forEach { assertThat(it.isFocusable).isTrue() }
  }

  private fun findComponentAt(component: JComponent, borderConstraint: String): JComponent {
    val layout = component.layout as BorderLayout
    return layout.getLayoutComponent(borderConstraint) as JComponent
  }
}
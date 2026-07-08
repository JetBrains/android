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
package com.android.tools.editor

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JPanel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class EditorActionsFloatingToolbarProviderTest {
  private val disposableRule = DisposableRule()

  @get:Rule val chain = RuleChain.outerRule(ApplicationRule()).around(EdtRule()).around(disposableRule)!!

  // Regression test for b/352448794
  @Test
  fun testToolbarIsImportant() {
    val panel = JPanel()
    val zoomGroup = DefaultActionGroup()
    val otherGroup = DefaultActionGroup()
    val groups =
      object : EditorActionsToolbarActionGroups {
        override val zoomControlsGroup: ActionGroup = zoomGroup
        override val otherGroups: List<ActionGroup> = listOf(otherGroup)
      }

    val provider =
      object : EditorActionsFloatingToolbarProvider(panel, disposableRule.disposable) {
        override fun getActionGroups(): EditorActionsToolbarActionGroups = groups

        // Expose updateToolbar() as public so it can be called directly in this unit test.
        public override fun updateToolbar() {
          super.updateToolbar()
        }
      }

    // Trigger the toolbar creation and layout population
    provider.updateToolbar()

    val importantProperties = mutableListOf<Boolean?>()

    // Recursively traverse the toolbar components to search for toolbars that should be flagged.
    fun checkComponents(component: Component) {
      if (component is JComponent) {
        val important = component.getClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY) as? Boolean
        if (important != null) {
          importantProperties.add(important)
        }
      }
      if (component is Container) {
        component.components.forEach { checkComponents(it) }
      }
    }

    checkComponents(provider.floatingToolbar)

    // Ensure we found both toolbars (zoomControlsGroup and otherGroups), and both were flagged as important.
    assertThat(importantProperties).isNotEmpty()
    assertThat(importantProperties).containsExactly(true, true)
  }
}

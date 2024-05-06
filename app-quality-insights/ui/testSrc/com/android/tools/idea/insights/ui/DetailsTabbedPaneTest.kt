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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JButton
import javax.swing.JPanel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class DetailsTabbedPaneTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule val edtRule = EdtRule()
  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(controllerRule)

  private lateinit var stackTraceConsole: StackTraceConsole

  @Before
  fun setUp() {
    stackTraceConsole =
      StackTraceConsole(controllerRule.controller, projectRule.project, controllerRule.tracker)
    Disposer.register(controllerRule.disposable, stackTraceConsole)
  }

  @Test
  fun `creates tabs according to definition`() {
    val panel = JPanel()
    val button = JButton("blah")
    val definitions =
      listOf(
        TabbedPaneDefinition("Stack trace", stackTraceConsole.consoleView.component),
        TabbedPaneDefinition("Panel", panel),
        TabbedPaneDefinition("Button", button)
      )

    val detailsTabbedPane = DetailsTabbedPane("name", definitions, stackTraceConsole)
    val fakeUi = FakeUi(detailsTabbedPane.component)

    val tabbedPane = fakeUi.findComponent<JBTabbedPane>()!!
    assertThat(tabbedPane.tabCount).isEqualTo(3)
    assertThat(tabbedPane.getComponentAt(0)).isEqualTo(stackTraceConsole.consoleView.component)
    assertThat(tabbedPane.getComponentAt(1)).isEqualTo(panel)
    assertThat(tabbedPane.getComponentAt(2)).isEqualTo(button)
    assertThat(tabbedPane.getTitleAt(0)).isEqualTo("Stack trace")
    assertThat(tabbedPane.getTitleAt(1)).isEqualTo("Panel")
    assertThat(tabbedPane.getTitleAt(2)).isEqualTo("Button")
  }
}

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
package com.android.tools.idea.insights.ui

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.adtui.workbench.WorkBenchManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppInsightsProjectLevelControllerRule
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons
import java.awt.Dimension
import javax.swing.JLabel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunsInEdt
class AppInsightsContentPanelTest {

  private val projectRule = ProjectRule()
  private val controllerRule = AppInsightsProjectLevelControllerRule(projectRule)

  @get:Rule
  val ruleChain: RuleChain =
    RuleChain.outerRule(EdtRule())
      .around(projectRule)
      .around(controllerRule)
      .around(FlagRule(StudioFlags.CRASHLYTICS_INSIGHT_IN_TOOLWINDOW, true))

  private val fakeToolWindowList =
    mutableListOf<AppInsightsToolWindowDefinition>().apply {
      add(createMockToolWindow("Insights"))
      add(createMockToolWindow("Details"))
      add(createMockToolWindow("Notes"))
    }

  private val name = "CRASHLYTICS"
  private lateinit var workBench: FakeWorkBench<AppInsightsToolWindowContext>

  @Before
  fun setup() {
    val disposable = Disposer.newDisposable()
    Disposer.register(projectRule.disposable, disposable)
    workBench = FakeWorkBench(projectRule.project, name, disposable)
  }

  @After
  fun tearDown() {
    Disposer.dispose(workBench)
    PropertiesComponent.getInstance().unsetValue("$name.workbench.toolwindow.order.updated")
  }

  @Test
  fun `test restore default layout when properties component value not set`() {
    val propertiesComponent = PropertiesComponent.getInstance()
    assertThat(propertiesComponent.isValueSet("$name.workbench.toolwindow.order.updated")).isFalse()

    AppInsightsContentPanel(
      controllerRule.controller,
      projectRule.project,
      projectRule.disposable,
      AppInsightsIssuesTableCellRenderer,
      name,
      fakeToolWindowList,
      null,
      { workBench },
    ) {
      JLabel()
    }

    assertThat(propertiesComponent.isValueSet("$name.workbench.toolwindow.order.updated")).isTrue()
    assertThat(workBench.showToolWindowCounter).isEqualTo(1)
    assertThat(workBench.restoreDefaultLayoutCounter).isEqualTo(1)

    AppInsightsContentPanel(
      controllerRule.controller,
      projectRule.project,
      projectRule.disposable,
      AppInsightsIssuesTableCellRenderer,
      name,
      fakeToolWindowList,
      null,
      { workBench },
    ) {
      JLabel()
    }

    assertThat(propertiesComponent.isValueSet("$name.workbench.toolwindow.order.updated")).isTrue()
    // Assert the functions are not called again
    assertThat(workBench.showToolWindowCounter).isEqualTo(1)
    assertThat(workBench.restoreDefaultLayoutCounter).isEqualTo(1)
  }

  private class FakeWorkBench<T>(project: Project, name: String, disposable: Disposable) :
    WorkBench<T>(project, name, null, disposable) {
    var restoreDefaultLayoutCounter = 0
      private set

    var showToolWindowCounter = 0
      private set

    private val tools = mutableListOf<ToolWindowDefinition<T>>()

    override fun init(
      context: T & Any,
      definitions: MutableList<ToolWindowDefinition<T>>,
      minimizedWindows: Boolean,
    ) {
      super.init(context, definitions, minimizedWindows)
      tools.addAll(definitions)
    }

    override fun restoreDefaultLayout() {
      super.restoreDefaultLayout()
      restoreDefaultLayoutCounter++
    }

    override fun showToolWindow(name: String) {
      super.showToolWindow(name)
      assertThat(name).isEqualTo(tools.first().name)
      showToolWindowCounter++
    }

    override fun dispose() {
      super.dispose()
      WorkBenchManager.getInstance().unregister(this)
    }
  }

  private fun createMockToolWindow(name: String) =
    mock<AppInsightsToolWindowDefinition>().apply {
      doReturn(name).whenever(this).name
      doReturn(name).whenever(this).title
      doReturn(StudioIcons.AppQualityInsights.DETAILS).whenever(this).icon
      doReturn(Side.RIGHT).whenever(this).side
      doReturn(Split.TOP).whenever(this).split
      doReturn(AutoHide.DOCKED).whenever(this).autoHide
      doReturn(Dimension(1, 1)).whenever(this).buttonSize
      val factory: (Disposable) -> ToolContent<AppInsightsToolWindowContext> = { _ ->
        object : ToolContent<AppInsightsToolWindowContext> {
          override fun dispose() = Unit

          override fun getComponent() = JLabel()

          override fun setToolContext(toolContext: AppInsightsToolWindowContext?) = Unit
        }
      }
      whenever(this.factory).thenReturn(factory)
    }
}

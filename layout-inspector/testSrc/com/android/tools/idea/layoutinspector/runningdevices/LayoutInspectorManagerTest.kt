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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

class LayoutInspectorManagerTest {

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val disposableRule = DisposableRule()

  @get:Rule
  val edtRule = EdtRule()

  @Before
  fun setUp() {
    val mockLayoutInspectorProjectService = mock<LayoutInspectorProjectService>()
    whenever(mockLayoutInspectorProjectService.getLayoutInspector(any(), any())).thenAnswer { mock<LayoutInspector>() }
    projectRule.project.replaceService(LayoutInspectorProjectService::class.java, mockLayoutInspectorProjectService, disposableRule.disposable)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnOff() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(projectRule.project)

    val tabContext = createTabContext()

    layoutInspectorManager.enableLayoutInspector(tabContext, true)

    assertWorkbenchWasAdded(tabContext)

    layoutInspectorManager.enableLayoutInspector(tabContext, false)

    assertWorkbenchWasRemoved(tabContext)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTimesForSameTab() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(projectRule.project)

    val tabContext = createTabContext()

    layoutInspectorManager.enableLayoutInspector(tabContext, true)
    layoutInspectorManager.enableLayoutInspector(tabContext, true)

    assertWorkbenchWasAdded(tabContext)

    layoutInspectorManager.enableLayoutInspector(tabContext, false)

    assertWorkbenchWasRemoved(tabContext)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOffMultipleTimesForSameTab() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(projectRule.project)

    val tabContext = createTabContext()

    layoutInspectorManager.enableLayoutInspector(tabContext, true)

    assertWorkbenchWasAdded(tabContext)

    layoutInspectorManager.enableLayoutInspector(tabContext, false)
    layoutInspectorManager.enableLayoutInspector(tabContext, false)

    assertWorkbenchWasRemoved(tabContext)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTabs() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(projectRule.project)

    val tabContext1 = createTabContext(serialNumber = "serial1")
    val tabContext2 = createTabContext(serialNumber = "serial2")

    layoutInspectorManager.enableLayoutInspector(tabContext1, true)
    layoutInspectorManager.enableLayoutInspector(tabContext2, true)

    assertWorkbenchWasAdded(tabContext1)
    assertWorkbenchWasAdded(tabContext2)

    layoutInspectorManager.enableLayoutInspector(tabContext1, false)

    assertWorkbenchWasRemoved(tabContext1)
    assertWorkbenchWasAdded(tabContext2)

    layoutInspectorManager.enableLayoutInspector(tabContext2, false)
    assertWorkbenchWasRemoved(tabContext2)
  }

  @Test
  @RunsInEdt
  fun testStateUpdatedOnDisposal() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(projectRule.project)

    val observedState = mutableListOf<Set<RunningDevicesTabContext>>()
    layoutInspectorManager.addStateListener { observedState.add(it) }

    val disposable = Disposer.newDisposable()
    Disposer.register(disposableRule.disposable, disposable)

    val tabContext = createTabContext(disposable = disposable)

    layoutInspectorManager.enableLayoutInspector(tabContext, true)

    assertWorkbenchWasAdded(tabContext)

    Disposer.dispose(disposable)

    assertThat(observedState).isEqualTo(listOf(
      emptySet(),
      setOf(tabContext),
      emptySet()
    ))
  }

  private fun assertWorkbenchWasAdded(tabContext: RunningDevicesTabContext) {
    assertThat(tabContext.tabContentPanel.parents().filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(1)
    assertThat(tabContext.tabContentPanelContainer.components.filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(1)
  }

  private fun assertWorkbenchWasRemoved(tabContext: RunningDevicesTabContext) {
    assertThat(tabContext.tabContentPanel.parents().filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(0)
    assertThat(tabContext.tabContentPanelContainer.components.filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(0)
    assertThat(tabContext.tabContentPanel.parent).isEqualTo(tabContext.tabContentPanelContainer)
  }

  private fun createTabContext(
    serialNumber: String = "serial",
    disposable: Disposable = disposableRule.disposable
  ): RunningDevicesTabContext {
    val content = JPanel()
    val container = JPanel()
    container.add(content)

    return RunningDevicesTabContext(
      projectRule.project,
      disposable,
      serialNumber,
      content,
      container
    )
  }

  private fun Component.parents(): List<Container> {
    val parents = mutableListOf<Container>()
    var component = this
    while (component.parent != null) {
      parents.add(component.parent)
      component = component.parent
    }
    return parents
  }
}
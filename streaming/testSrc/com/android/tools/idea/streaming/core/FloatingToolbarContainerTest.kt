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
package com.android.tools.idea.streaming.core

import com.android.testutils.ImageDiffUtil
import com.android.testutils.TestUtils
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Color
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.border.EmptyBorder

/** Test for [FloatingToolbarContainer]. */
@RunsInEdt
class FloatingToolbarContainerTest {

  companion object {
    @JvmField @ClassRule val iconRule = IconLoaderRule() // Enable icon loading in a headless test environment.
  }

  private val disposableRule = DisposableRule()
  @get:Rule val rule = RuleChain(ApplicationRule(), disposableRule, EdtRule())
  private lateinit var fakeUi: FakeUi

  @Test
  fun testCollapsibleVertical() {
    val panel = createHostPanel()
    fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    val toolbar = FloatingToolbarContainer(horizontal = false, inactiveAlpha = 0.5).apply { createTestToolbars(collapsible = true) }
    panel.add(toolbar, BorderLayout.EAST)
    assertAppearance("CollapsibleVerticalInactive")

    fakeUi.mouse.moveTo(toolbar.x + toolbar.width / 2, toolbar.y + toolbar.height - toolbar.width / 2)
    assertAppearance("CollapsibleVerticalActive")

    fakeUi.mouse.moveTo(0, 0)
    assertAppearance("CollapsibleVerticalInactive")
  }

  @Test
  fun testNonCollapsibleHorizontal() {
    val panel = createHostPanel()
    fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    val toolbar = FloatingToolbarContainer(horizontal = true, inactiveAlpha = 0.7).apply { createTestToolbars(collapsible = false) }
    panel.add(toolbar, BorderLayout.SOUTH)
    assertAppearance("NonCollapsibleVerticalInactive")

    fakeUi.mouse.moveTo(toolbar.x + toolbar.width - toolbar.height / 2, toolbar.y + toolbar.height / 2)
    assertAppearance("NonCollapsibleVerticalActive")

    fakeUi.mouse.moveTo(0, 0)
    assertAppearance("NonCollapsibleVerticalInactive")
  }

  @Test
  fun testEmptyToolbar() {
    val panel = createHostPanel()
    fakeUi = FakeUi(panel, createFakeWindow = true, parentDisposable = disposableRule.disposable)
    val toolbar = FloatingToolbarContainer(horizontal = false, inactiveAlpha = 0.8).apply {
      addToolbar("FloatingToolbar", DefaultActionGroup(), collapsible = false)
      addToolbar("FloatingToolbar", DefaultActionGroup(), collapsible = false)
    }
    panel.add(toolbar, BorderLayout.EAST)
    fakeUi.updateToolbars()
    // Empty toolbar should not be visible.
    val image = fakeUi.render()
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        assertThat(image.getRGB(x, y)).isEqualTo(panel.background.rgb)
      }
    }
  }

  private fun createHostPanel(): BorderLayoutPanel {
    return BorderLayoutPanel().apply {
      setSize(200, 200)
      background = Color(150, 200, 255)
      val scrollBarWidth = UIUtil.getScrollBarWidth()
      border = EmptyBorder(scrollBarWidth, scrollBarWidth, scrollBarWidth, scrollBarWidth)
    }
  }

  private fun FloatingToolbarContainer.createTestToolbars(collapsible: Boolean) {
    val actionGroup1 = DefaultActionGroup().apply {
      add(TestToggleAction("Left Top", AllIcons.Actions.MoveToLeftTop))
      add(TestToggleAction("Right Top", AllIcons.Actions.MoveToRightTop))
      add(TestToggleAction("Right Bottom", AllIcons.Actions.MoveToRightBottom))
      add(TestToggleAction("Left Bottom", AllIcons.Actions.MoveToLeftBottom))
    }
    addToolbar("FloatingToolbar", actionGroup1, collapsible = collapsible)

    val actionGroup2 = DefaultActionGroup().apply {
      add(TestAction("Fit Content", AllIcons.General.FitContent))
    }
    addToolbar("FloatingToolbar", actionGroup2, collapsible = false)
  }

  private fun assertAppearance(goldenImageName: String) {
    ActivityTracker.getInstance().inc()
    fakeUi.updateToolbarsIfNecessary()
    val image = fakeUi.render()
    ImageDiffUtil.assertImageSimilar(getGoldenFile(goldenImageName), image, 0.0)
  }

  private fun getGoldenFile(name: String): Path =
      TestUtils.resolveWorkspacePathUnchecked("$GOLDEN_FILE_PATH/$name.png")

  private class TestAction(name: String, icon: Icon) : AnAction(name, name, icon) {

    override fun actionPerformed(event: AnActionEvent) {
    }
  }

  private class TestToggleAction(name: String, icon: Icon) : ToggleAction(name, name, icon) {

    override fun isSelected(event: AnActionEvent): Boolean =
      toggleState == templateText

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      if (state) {
        toggleState = templateText
      }
    }

    companion object {
      private var toggleState: String = "Right Bottom"
    }
  }
}

private const val GOLDEN_FILE_PATH = "tools/adt/idea/streaming/testData/FloatingToolbarContainerTest/golden"

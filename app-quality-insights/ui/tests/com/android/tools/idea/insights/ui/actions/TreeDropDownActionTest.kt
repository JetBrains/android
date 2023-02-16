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
package com.android.tools.idea.insights.ui.actions

import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeComponentPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.insights.GroupAware
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.WithCount
import com.android.tools.idea.insights.ui.TreeDropDownPopup
import com.android.tools.idea.insights.waitForCondition
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.ibm.icu.impl.Assert.fail
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.CheckedTreeNode
import java.awt.BorderLayout
import java.util.Enumeration
import javax.swing.JPanel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

data class SimpleValue(val groupingKey: String, val title: String)

private val VALUE1 = WithCount(1, SimpleValue("1", "Title1"))
private val VALUE2 = WithCount(2, SimpleValue("1", "Title2"))
private val VALUE3 = WithCount(1, SimpleValue("3", "Title 3"))

class TreeDropDownActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val popupRule = JBPopupRule()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(popupRule)!!

  @org.junit.Ignore("b/250064419")
  @Test
  fun `open popup shows correct selection`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val panel = JPanel(BorderLayout())
      val fakeUi = FakeUi(panel)

      val flow = MutableSharedFlow<MultiSelection<WithCount<SimpleValue>>>()

      val dropdown =
        TreeDropDownAction(
          "values",
          flow,
          projectRule.project.coroutineScope,
          groupNameSupplier = SimpleValue::groupingKey,
          nameSupplier = SimpleValue::title,
          onSelected = {},
          getLocationOnScreen = { fakeUi.getPosition(this) }
        )
      val actionGroups = DefaultActionGroup().apply { add(dropdown) }
      val toolbar =
        ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
          targetComponent = panel
        }
      panel.add(toolbar.component, BorderLayout.CENTER)

      toolbar.updateActionsImmediately()

      val actionButton = toolbar.component.getComponent(0) as ActionButton
      actionButton.click()

      dropdown.titleState.waitForValue("All values")

      assertThat(lastPopup.root.childCount).isEqualTo(0)

      flow.emit(MultiSelection(setOf(VALUE1, VALUE3), listOf(VALUE1, VALUE2, VALUE3)))

      dropdown.titleState.waitForValue("2 values")

      actionButton.click()

      val (group1, group2) = lastPopup.root.checkedChildren()
      group1.checkedChildren().let { children ->
        assertThat(children).hasSize(2)
        val (one, two) = children
        assertThat(one.isChecked).isTrue()
        assertThat(two.isChecked).isFalse()
      }
      assertThat(group2.isChecked).isTrue()
      assertThat(group2.checkedChildren()).isEmpty()
    }

  @Test
  fun `empty selection disables the action`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val panel = JPanel(BorderLayout())
      val fakeUi = FakeUi(panel)

      val flow = MutableSharedFlow<MultiSelection<WithCount<SimpleValue>>>()

      val dropdown =
        TreeDropDownAction(
          "values",
          flow,
          projectRule.project.coroutineScope,
          groupNameSupplier = SimpleValue::groupingKey,
          nameSupplier = SimpleValue::title,
          onSelected = {},
          getLocationOnScreen = { fakeUi.getPosition(this) }
        )
      val actionGroups = DefaultActionGroup().apply { add(dropdown) }
      val toolbar =
        ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
          targetComponent = panel
        }
      panel.add(toolbar.component, BorderLayout.CENTER)

      val actionEvent: AnActionEvent = mock()
      val presentation: Presentation = mock()
      `when`(actionEvent.presentation).thenReturn(presentation)

      dropdown.update(actionEvent)
      verify(presentation, times(1)).isEnabled = true
      verify(presentation, times(1)).text = "All values"

      flow.emit(MultiSelection(emptySet(), emptyList()))
      dropdown.isDisabled.first { it }
      dropdown.update(actionEvent)

      verify(presentation, times(1)).isEnabled = false
      verify(presentation, times(2)).text = "All values"
      verifyNoMoreInteractions(presentation)
    }

  @Test
  fun `search filters correctly`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val panel = JPanel(BorderLayout())
      val fakeUi = FakeUi(panel)

      val flow =
        MutableStateFlow(
          MultiSelection(setOf(VALUE1, VALUE2, VALUE3), listOf(VALUE1, VALUE2, VALUE3))
        )

      val dropdown =
        TreeDropDownAction(
          "values",
          flow,
          projectRule.project.coroutineScope,
          groupNameSupplier = SimpleValue::groupingKey,
          nameSupplier = SimpleValue::title,
          onSelected = {},
          getLocationOnScreen = { fakeUi.getPosition(this) }
        )

      waitForCondition(1000) { dropdown.selectionState.value.items.size == 3 }

      val actionGroups = DefaultActionGroup().apply { add(dropdown) }
      val toolbar =
        ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
          targetComponent = panel
        }
      panel.add(toolbar.component, BorderLayout.CENTER)
      toolbar.updateActionsImmediately()

      val actionButton = toolbar.component.getComponent(0) as ActionButton
      actionButton.click()
      dropdown.titleState.waitForValue("All values")

      fun verifyAllShown() {
        val (group1, group2) = lastPopup.root.checkedChildren()
        assertThat(group1.userObject).isEqualTo("1")
        val children = group1.checkedChildren()
        assertThat(children).hasSize(2)
        val (one, two) = children
        assertThat(one.userObject).isEqualTo(VALUE1)
        assertThat(two.userObject).isEqualTo(VALUE2)
        assertThat(group2.userObject).isEqualTo(VALUE3)
      }

      verifyAllShown()
      lastPopup.searchTextField.text = "title"
      verifyAllShown()
      lastPopup.searchTextField.text = "TITLE"
      verifyAllShown()

      run {
        lastPopup.searchTextField.text = "1"
        assertThat(lastPopup.root.checkedChildren()).hasSize(1)
        val group1 = lastPopup.root.checkedChildren().single()
        assertThat(group1.userObject).isEqualTo("1")
        val children = group1.checkedChildren()
        assertThat(children).hasSize(2)
        val (one, two) = children
        assertThat(one.userObject).isEqualTo(VALUE1)
        assertThat(two.userObject).isEqualTo(VALUE2)
      }
      run {
        lastPopup.searchTextField.text = "le2"
        val group1 = lastPopup.root.checkedChildren().single()
        assertThat(group1.userObject).isEqualTo("1")
        val children = group1.checkedChildren()
        assertThat(children.single().userObject).isEqualTo(VALUE2)
      }
      run {
        lastPopup.searchTextField.text = "title 3"
        assertThat(lastPopup.root.checkedChildren()).hasSize(1)
        val group1 = lastPopup.root.checkedChildren().single()
        assertThat(group1.userObject).isEqualTo(VALUE3)
        assertThat(group1.childCount).isEqualTo(0)
      }
    }

  @Test
  fun `single child is expanded`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val panel = JPanel(BorderLayout())
      val fakeUi = FakeUi(panel)

      val flow = MutableStateFlow(MultiSelection(setOf(VALUE1, VALUE2), listOf(VALUE1, VALUE2)))

      val dropdown =
        TreeDropDownAction(
          "values",
          flow,
          projectRule.project.coroutineScope,
          groupNameSupplier = SimpleValue::groupingKey,
          nameSupplier = SimpleValue::title,
          onSelected = {},
          getLocationOnScreen = { fakeUi.getPosition(this) }
        )

      waitForCondition(1000) { dropdown.selectionState.value.items.size == 2 }

      val actionGroups = DefaultActionGroup().apply { add(dropdown) }
      val toolbar =
        ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
          targetComponent = panel
        }
      panel.add(toolbar.component, BorderLayout.CENTER)
      toolbar.updateActionsImmediately()

      val actionButton = toolbar.component.getComponent(0) as ActionButton
      actionButton.click()
      dropdown.titleState.waitForValue("All values")

      assertThat(lastPopup.root.childCount).isEqualTo(1)
      assertThat(
          lastPopup.treeTable.tree.isExpanded(
            TreePath(arrayOf(lastPopup.root, lastPopup.root.firstChild))
          )
        )
        .isTrue()
    }

  private val lastPopup: TreeDropDownPopup<SimpleValue, GroupAware.Empty>
    get() =
      popupRule.fakePopupFactory
        .getChildPopups(mock())
        .filterIsInstance<FakeComponentPopup>()
        .map { it.contentPanel }
        .filterIsInstance<TreeDropDownPopup<SimpleValue, GroupAware.Empty>>()
        .first()
}

fun TreeNode.checkedChildren(): List<CheckedTreeNode> =
  (children() as Enumeration<CheckedTreeNode>).toList()

suspend fun <T> StateFlow<T>.waitForValue(value: T, timeout: Long = 1000) {
  val received = mutableListOf<T>()
  try {
    withTimeout(timeout) { takeWhile { it != value }.collect { received.add(it) } }
  } catch (ex: TimeoutCancellationException) {
    fail("Timed out waiting for value $value. Received values so far $received")
  }
}

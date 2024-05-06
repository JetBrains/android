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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
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
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.ThreeStateCheckBox
import java.awt.BorderLayout
import java.util.Enumeration
import javax.swing.JPanel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

data class SimpleGroup(val name: String) : GroupAware<SimpleGroup> {
  override fun compareTo(other: SimpleGroup) = name.compareTo(other.name)

  override val groupName = name
}

private val VALUE1 = WithCount(4, SimpleValue("1", "Title1"))
private val VALUE2 = WithCount(2, SimpleValue("1", "Title2"))
private val VALUE3 = WithCount(1, SimpleValue("3", "Title 3"))
private val VALUE4 = WithCount(3, SimpleValue("2", "Title2"))
private val VALUE5 = WithCount(1, SimpleValue("2", "Title 3"))

class TreeDropDownActionTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val popupRule = JBPopupRule()

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(popupRule)!!

  private val scope: CoroutineScope
    get() = AndroidCoroutineScope(projectRule.testRootDisposable)

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
          scope,
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
          scope,
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
          scope,
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
          scope,
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

  @Test
  fun `popup shows items in descending order of total count and respects filters`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val panel = JPanel(BorderLayout())
      val fakeUi = FakeUi(panel)

      val flow =
        MutableStateFlow(
          MultiSelection(
            setOf(VALUE1, VALUE2, VALUE3, VALUE4, VALUE5),
            listOf(VALUE1, VALUE2, VALUE3, VALUE4, VALUE5)
          )
        )

      val dropdown =
        TreeDropDownAction(
          "values",
          flow,
          scope,
          groupNameSupplier = SimpleValue::groupingKey,
          nameSupplier = SimpleValue::title,
          onSelected = {},
          getLocationOnScreen = { fakeUi.getPosition(this) }
        )

      waitForCondition(1000) { dropdown.selectionState.value.items.size == 5 }

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

      // Expected Order (brackets represent grouping)
      // (VALUE1, VALUE2), (VALUE4, VALUE5), (VALUE3)
      val (group1, group2, group3) = lastPopup.root.checkedChildren()
      assertThat(group1.userObject).isEqualTo("1")
      assertThat(group1.checkedChildren().map { it.userObject })
        .containsExactly(VALUE1, VALUE2)
        .inOrder()
      assertThat(group2.userObject).isEqualTo("2")
      assertThat(group2.checkedChildren().map { it.userObject })
        .containsExactly(VALUE4, VALUE5)
        .inOrder()
      assertThat(group3.userObject).isEqualTo(VALUE3)

      // Ordering should respect the filter.
      // Expect: VALUE4, VALUE2
      lastPopup.searchTextField.text = "title2"
      assertThat(lastPopup.root.checkedChildren().map { it.userObject })
        .containsExactly("2", "1")
        .inOrder()
      assertThat(lastPopup.root.checkedChildren().map { it.checkedChildren().single().userObject })
        .containsExactly(VALUE4, VALUE2)
        .inOrder()
    }

  @Test
  fun `popup shows unavailable message when items exceed limit`(): Unit =
    runBlocking(AndroidDispatchers.uiThread) {
      val panel = JPanel(BorderLayout())
      val items =
        (1..MAX_DROPDOWN_ITEMS + 1)
          .asSequence()
          .map { WithCount(1, SimpleValue("$it", "Title")) }
          .toList()

      val flow = MutableStateFlow(MultiSelection(items.toSet(), items))

      val dropdown =
        TreeDropDownAction(
          "values",
          flow,
          scope,
          groupNameSupplier = SimpleValue::groupingKey,
          nameSupplier = SimpleValue::title,
          onSelected = {},
          getLocationOnScreen = { FakeUi(panel).getPosition(this) }
        )

      dropdown.selectionState.first { it.items.size == MAX_DROPDOWN_ITEMS + 1 }

      val actionGroups = DefaultActionGroup().apply { add(dropdown) }
      val toolbar =
        ActionManager.getInstance().createActionToolbar("AppInsights", actionGroups, true).apply {
          targetComponent = panel
        }
      panel.add(toolbar.component, BorderLayout.CENTER)
      toolbar.updateActionsImmediately()

      val actionButton = toolbar.component.getComponent(0) as ActionButton
      actionButton.click()

      // Check the error message is displayed.
      val popup =
        popupRule.fakePopupFactory
          .getChildPopups(mock())
          .filterIsInstance<FakeComponentPopup>()
          .map { it.contentPanel }
          .last()

      assertThat((popup.components.single() as JBLabel).text)
        .isEqualTo("Filter unavailable: too many items.")
    }

  @Test
  fun `secondary group selection causes updates in primary and vice versa`() =
    runBlocking(AndroidDispatchers.uiThread) {
      val panel = JPanel(BorderLayout())
      val flow =
        MutableStateFlow(
          MultiSelection(
            setOf(VALUE1, VALUE2, VALUE3, VALUE4, VALUE5),
            listOf(VALUE1, VALUE2, VALUE3, VALUE4, VALUE5)
          )
        )
      val dropdown =
        TreeDropDownAction(
          "values",
          flow,
          scope,
          groupNameSupplier = SimpleValue::groupingKey,
          nameSupplier = SimpleValue::title,
          secondaryGroupSupplier = {
            setOf(SimpleGroup(if (it.groupingKey == "1") "ONE" else "OTHER"))
          },
          onSelected = {},
          getLocationOnScreen = { FakeUi(panel).getPosition(this) }
        )

      waitForCondition(1000) { dropdown.selectionState.value.items.size == 5 }

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

      // Check secondary groups are shown.
      val fakeUi = FakeUi(lastPopup)
      val secondaryGroups = fakeUi.findAllComponents<ThreeStateCheckBox>()
      assertThat(secondaryGroups.map { it.text }).containsExactly("ONE", "OTHER").inOrder()
      assertThat(secondaryGroups.all { it.state == ThreeStateCheckBox.State.SELECTED }).isTrue()

      // Unchecking a secondary group box should uncheck all of its associated options.
      secondaryGroups[0].doClick()
      val uncheckedNodes = lastPopup.root.checkedChildren().filterNot { it.isChecked }
      assertThat(uncheckedNodes.map { it.userObject as String }).containsExactly("1")
      val uncheckedLeaves =
        uncheckedNodes.flatMap { it.children().asSequence() }.filterIsInstance<CheckedTreeNode>()
      assertThat(uncheckedLeaves.filterNot { it.isChecked }.map { it.userObject })
        .containsExactly(VALUE1, VALUE2)

      // Checking one of the leaves should update its associated secondary group to DONT_CARE state
      lastPopup.helper.setNodeState(lastPopup.treeTable.tree, uncheckedLeaves[0], true)
      assertThat(secondaryGroups[0].state).isEqualTo(ThreeStateCheckBox.State.DONT_CARE)
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

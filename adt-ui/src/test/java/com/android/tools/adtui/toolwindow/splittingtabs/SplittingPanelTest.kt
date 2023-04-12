/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.toolwindow.splittingtabs

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.HORIZONTAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplitOrientation.VERTICAL
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanelTest.TreeNode.Leaf
import com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanelTest.TreeNode.Parent
import com.android.tools.adtui.toolwindow.splittingtabs.actions.SplitAction
import com.android.tools.adtui.toolwindow.splittingtabs.state.PanelState
import com.android.tools.adtui.toolwindow.splittingtabs.state.SplittingTabsStateProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons.Actions.Close
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Tests for [SplittingPanel] and a few for [SplitAction] which require elaborate setup.
 */
@RunsInEdt
class SplittingPanelTest {
  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  private val contentManager by lazy { ToolWindowHeadlessManagerImpl.MockToolWindow(projectRule.project).contentManager }

  // The mock content manager doesn't assign a parent to the content component so we need to provide one
  private val contentRootPanel = JPanel().also { it.size = Dimension(100, 100) }

  private val fakeUi = FakeUi(contentRootPanel, createFakeWindow = true)

  @After
  fun tearDown() {
    contentRootPanel.removeAll()
  }

  @Test
  fun init_addsComponent() {
    val component = JLabel("Component")

    val splittingPanel = SplittingPanel(
      createContent(null, "Tab", false),
      clientState = null,
      object : ChildComponentFactory {
        override fun createChildComponent(state: String?, popupActionGroup: ActionGroup): JComponent = component
      })

    assertThat(splittingPanel.component).isSameAs(component)
  }

  @Test
  fun split_selectsContent() {
    val content1 = createContent(null, "Tab1", false)
    val content2 = createSplittingPanelContent(contentRootPanel) { _, _ -> JPanel() }
    contentManager.setSelectedContent(content1)

    fakeUi.getComponent<SplittingPanel>().split(VERTICAL)

    assertThat(contentManager.selectedContent).isSameAs(content2)
  }

  @Test
  fun split() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, _ -> JLabel("${count.incrementAndGet()}") }

    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             0.5f,
             Parent(HORIZONTAL, 0.5f, Leaf("1"), Leaf("3")),
             Parent(HORIZONTAL, 0.5f, Leaf("2"), Leaf("4"))))
  }

  @Test
  fun close_1() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("1") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             0.5f,
             Leaf("3"),
             Parent(HORIZONTAL, 0.5f, Leaf("2"), Leaf("4"))))
    assertThat(splittingPanel.isComponentDisposed()).isTrue()
  }

  @Test
  fun close_2() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("2") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             0.5f,
             Parent(HORIZONTAL, 0.5f, Leaf("1"), Leaf("3")),
             Leaf("4")))
    assertThat(splittingPanel.isComponentDisposed()).isTrue()
  }

  @Test
  fun close_3() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("3") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             0.5f,
             Leaf("1"),
             Parent(HORIZONTAL, 0.5f, Leaf("2"), Leaf("4"))))
    assertThat(splittingPanel.isComponentDisposed()).isTrue()
  }

  @Test
  fun close_4() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("4") }

    splittingPanel.close()

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             0.5f,
             Parent(HORIZONTAL, 0.5f, Leaf("1"), Leaf("3")),
             Leaf("2")))
    assertThat(splittingPanel.isComponentDisposed()).isTrue()
  }

  @Test
  fun disposeContent_disposesSplits() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val disposableLabels = fakeUi.findAllComponents<DisposableLabel>()

    Disposer.dispose(content)

    assertThat(disposableLabels.count { !it.disposed }).isEqualTo(0)
  }

  @Test
  fun closeAll_removesContent() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))

    while (true) {
      (fakeUi.findComponent<SplittingPanel>() ?: break).close()
    }

    assertThat(contentManager.contents).isEmpty()
    assertThat(contentRootPanel.componentCount).isEqualTo(0)
  }

  @Test
  fun findFirstSplitter_noSplits() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }

    assertThat(content.findFirstSplitter()).isSameAs(fakeUi.getComponent<SplittingPanel> { it.isNamed("1") })
  }

  @Test
  fun findFirstSplitter_withSplits() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { _, _ -> DisposableLabel("${count.incrementAndGet()}") }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))

    assertThat(content.findFirstSplitter()).isSameAs(fakeUi.getComponent<SplittingPanel> { it.isNamed("1") })
  }

  @Test
  fun findFirstSplitter_noSplitters() {
    val content = createContent(JPanel(), "Tab", /* isLockable= */ false)

    assertThat(content.findFirstSplitter()).isNull()
  }

  @Test
  fun getState_stateProvider() {
    createSplittingPanelContent(contentRootPanel) { _, _ -> JLabelWithState("State") }

    assertThat(fakeUi.getComponent<SplittingPanel>().getState()).isEqualTo("State")
  }

  @Test
  fun getState_notStateProvider() {
    createSplittingPanelContent(contentRootPanel) { _, _ -> JPanel() }

    assertThat(fakeUi.getComponent<SplittingPanel>().getState()).isNull()
  }

  @Test
  fun splitAction_vertical() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { _, _ -> JLabel("${count.incrementAndGet()}") }

    SplitAction.Vertical().actionPerformed(content)

    assertThat(buildTree(contentRootPanel)).isEqualTo(Parent(VERTICAL, 0.5f, Leaf("1"), Leaf("2")))
  }

  @Test
  fun splitAction_horizontal() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { _, _ -> JLabel("${count.incrementAndGet()}") }

    SplitAction.Horizontal().actionPerformed(content)

    assertThat(buildTree(contentRootPanel)).isEqualTo(Parent(HORIZONTAL, 0.5f, Leaf("1"), Leaf("2")))
  }

  @Test
  fun splitAction_noSplitter_doesNotCrash() {
    val content = createContent(JPanel(), "Tab", /* isLockable= */ false)

    SplitAction.Horizontal().actionPerformed(content)
  }

  private fun SplittingPanel.isNamed(name: String): Boolean = (component as JLabel).text == name

  @Test
  fun buildStateFromComponent() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, _ -> JLabelWithState("${count.incrementAndGet()}") }

    split(SplitCommand("1", VERTICAL, 0.3f), SplitCommand("1", HORIZONTAL, 0.7f), SplitCommand("2", HORIZONTAL, 0.6f))

    val state = SplittingPanel.buildStateFromComponent(contentRootPanel.getComponent(0) as JComponent)

    assertThat(state).isEqualTo(
      PanelState(
        VERTICAL,
        0.3f,
        PanelState(HORIZONTAL, 0.7f, PanelState("1"), PanelState("3")),
        PanelState(HORIZONTAL, 0.6f, PanelState("2"), PanelState("4"))))
  }

  @Test
  fun buildComponentFromState() {
    val state = PanelState(
      VERTICAL,
      0.3f,
      PanelState(HORIZONTAL, 0.7f, PanelState("1"), PanelState("3")),
      PanelState(HORIZONTAL, 0.6f, PanelState("2"), PanelState("4")))
    val content = createContent(null, "Tab", false)

    val component = SplittingPanel.buildComponentFromState(content, state, object : ChildComponentFactory {
      override fun createChildComponent(state: String?, popupActionGroup: ActionGroup): JComponent = JLabelWithState(state!!)
    })

    assertThat(buildTree(component)).isEqualTo(
      Parent(VERTICAL,
             0.3f,
             Parent(HORIZONTAL, 0.7f, Leaf("1"), Leaf("3")),
             Parent(HORIZONTAL, 0.6f, Leaf("2"), Leaf("4")))
    )
  }

  @Test
  fun popupActionGroup_presentation() {
    val content = createSplittingPanelContent(contentRootPanel) { _, actionGroup ->
      JLabelWithPopupActionGroup("Text", actionGroup)
    }

    val actions = (content.findFirstSplitter()?.component as JLabelWithPopupActionGroup).popupActionGroup.getChildren(TestActionEvent())

    assertThat(actions.map { it.templateText }).containsExactly("Split Right", "Split Down", "Close").inOrder()
    assertThat(actions.map { it.templatePresentation.icon }).containsExactly(VERTICAL.icon, HORIZONTAL.icon, Close).inOrder()
  }

  @Test
  fun popupActionGroup_splitVertical() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { _, actionGroup ->
      JLabelWithPopupActionGroup("${count.incrementAndGet()}", actionGroup)
    }
    val event: AnActionEvent = TestActionEvent()
    val action = (content.findFirstSplitter()?.component as JLabelWithPopupActionGroup).popupActionGroup.getChildren(event)[0]

    action.actionPerformed(event)

    assertThat(buildTree(contentRootPanel)).isEqualTo(Parent(VERTICAL, 0.5f, Leaf("1"), Leaf("2")))
  }

  @Test
  fun popupActionGroup_splitHorizontal() {
    val count = AtomicInteger(0)
    val content = createSplittingPanelContent(contentRootPanel) { _, actionGroup ->
      JLabelWithPopupActionGroup("${count.incrementAndGet()}", actionGroup)
    }
    val event: AnActionEvent = TestActionEvent()
    val action = (content.findFirstSplitter()?.component as JLabelWithPopupActionGroup).popupActionGroup.getChildren(event)[1]

    action.actionPerformed(event)

    assertThat(buildTree(contentRootPanel)).isEqualTo(Parent(HORIZONTAL, 0.5f, Leaf("1"), Leaf("2")))
  }

  @Test
  fun popupActionGroup_close() {
    val count = AtomicInteger(0)
    createSplittingPanelContent(contentRootPanel) { _, actionGroup ->
      JLabelWithPopupActionGroup("${count.incrementAndGet()}", actionGroup)
    }
    split(SplitCommand("1", VERTICAL), SplitCommand("1", HORIZONTAL), SplitCommand("2", HORIZONTAL))
    val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed("1") }
    val event: AnActionEvent = TestActionEvent()
    val action = (splittingPanel.component as JLabelWithPopupActionGroup).popupActionGroup.getChildren(event)[2]

    action.actionPerformed(event)

    assertThat(buildTree(contentRootPanel)).isEqualTo(
      Parent(VERTICAL,
             0.5f,
             Leaf("3"),
             Parent(HORIZONTAL, 0.5f, Leaf("2"), Leaf("4"))))
    assertThat(splittingPanel.isComponentDisposed()).isTrue()
  }

  private fun createSplittingPanelContent(contentRootPanel: JPanel, createChildComponent: (String?, ActionGroup) -> JComponent): Content {
    val content = createContent(/* component= */ null, "Tab", /* isLockable= */ false)
    val splittingPanel = SplittingPanel(content, null, object : ChildComponentFactory {
      override fun createChildComponent(state: String?, popupActionGroup: ActionGroup): JComponent =
        createChildComponent(state, popupActionGroup)
    })
    content.component = splittingPanel
    contentManager.addContent(content)
    contentRootPanel.add(splittingPanel) // The mock ContentManager doesn't assign a parent.
    splittingPanel.size = splittingPanel.parent.size

    return content
  }

  fun createContent(component: JComponent?, displayName: String?, isLockable: Boolean): Content {
    val content = contentManager.factory.createContent(component, displayName, isLockable)
    Disposer.register(contentManager, content)
    return content
  }


  private fun split(vararg splitCommands: SplitCommand) {
    for (command in splitCommands) {
      val splittingPanel = fakeUi.getComponent<SplittingPanel> { it.isNamed(command.name) }
      splittingPanel.split(command.orientation)
      if (command.proportion != null) {
        (splittingPanel.parent as OnePixelSplitter).proportion = command.proportion
      }
    }
  }

  private fun buildTree(component: Component): TreeNode {
    return when (component) {
      contentRootPanel -> buildTree(contentRootPanel.getComponent(0))
      is SplittingPanel -> Leaf((component.component as JLabel).text)
      is Splitter -> Parent(
        SplitOrientation.fromSplitter(component),
        component.proportion,
        buildTree(component.firstComponent),
        buildTree(component.secondComponent))
      else -> throw IllegalStateException("Unexpected component found: ${component::class.qualifiedName}")
    }
  }

  private abstract class TreeNode {
    abstract fun toString(indent: String): String

    data class Leaf(val name: String) : TreeNode() {
      override fun toString(): String = toString("")
      override fun toString(indent: String): String = "$indent$name"
    }

    data class Parent(val orientation: SplitOrientation, val proportion: Float, val first: TreeNode, val second: TreeNode) : TreeNode() {
      override fun toString(): String = toString("")
      override fun toString(indent: String): String =
        "$indent${orientation.name} (${proportion * 100}%)\n${first.toString("$indent ")}\n${second.toString("$indent ")}"
    }
  }

  /**
   * Instructions for splitting a [SplittingPanel].
   *
   * A null proportion means [#split] will not modify the proportion created by the tested code.
   */
  private data class SplitCommand(val name: String, val orientation: SplitOrientation, val proportion: Float? = null)

  private open class DisposableLabel(text: String) : JLabel(text), Disposable {

    var disposed = false
    override fun dispose() {
      disposed = true
    }
  }

  private class JLabelWithState(val componentState: String) : JLabel(componentState), SplittingTabsStateProvider {
    override fun getState(): String = componentState
  }

  private class JLabelWithPopupActionGroup(text: String, val popupActionGroup: ActionGroup) : DisposableLabel(text)
}

private fun SplittingPanel.isComponentDisposed() = Disposer.isDisposed(component as Disposable)


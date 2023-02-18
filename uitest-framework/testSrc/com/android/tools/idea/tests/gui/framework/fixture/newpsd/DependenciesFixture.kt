/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractDependencyNode
import com.android.tools.idea.gradle.structure.configurables.ui.IssuesViewerPanel
import com.android.tools.idea.tests.gui.framework.clickToolButton
import com.android.tools.idea.tests.gui.framework.dragAndClickItem
import com.android.tools.idea.tests.gui.framework.findByType
import com.android.tools.idea.tests.gui.framework.getList
import com.android.tools.idea.tests.gui.framework.waitForIdle
import com.intellij.ui.table.TableView
import org.fest.swing.core.FocusOwnerFinder.inEdtFocusOwner
import org.fest.swing.core.Robot
import org.fest.swing.driver.JComboBoxDriver
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JListFixture
import org.fest.swing.fixture.JTableFixture
import org.fest.swing.fixture.JTreeFixture
import org.fest.swing.timing.Wait
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JTree
import javax.swing.text.AttributeSet
import javax.swing.text.ElementIterator
import javax.swing.text.html.HTML
import javax.swing.tree.DefaultMutableTreeNode

// The EditorComboBoxFixture and EditorComboBoxDriver are to manage the mismatch between EditorComboBox, which we use in
// declared dependency configuration management, and the test infrastructure in fest-swing.  It's not clear to me whether
// fest-swing makes too many assumptions of the JComboBox implementation details, or whether EditorComboBox fails to fulfil
// its required contracts (or both), but these classes are sufficient to smooth the differences between them.
//
// See also the comment above com.android.tools.idea.observable.ui.TextPropertyTest
class EditorComboBoxFixture(val robot: Robot, val target: JComboBox<*>) : JComboBoxFixture(robot, target) {
  override fun createDriver(robot: Robot): JComboBoxDriver = EditorComboBoxDriver(robot)
}

class EditorComboBoxDriver(val r: Robot) : JComboBoxDriver(r) {
  // The EditorComboBox has a peculiarity in that requesting it to focus actually gives focus to a sub-component.
  // fest-swing, however, assumes (particularly in focusAndWaitForFocusGain) that the target component will
  // later be the reported focus owner, and considers the operation a failure if it isn't.  Locally override the
  // preemptive test for equality in org.fest.swing.core.BasicRobot.focus(java.awt.Component, boolean) with this
  // more generous test.
  override fun focus(target: Component) {
    if (targetContainsCurrentFocus(target)) return
    super.focus(target)
  }

  override fun focusAndWaitForFocusGain(target: Component) {
    if (targetContainsCurrentFocus(target)) return
    super.focusAndWaitForFocusGain(target)
  }

  private fun targetContainsCurrentFocus(target: Component): Boolean {
    val currentOwner = inEdtFocusOwner()
    if (target is Container) {
      // TODO(xof): think about synchronized(treeLock)
      var c = currentOwner
      while (c != null && c !== target && c !is Window) {
        c = c.parent
      }
      return c === target
    }
    else {
      return false
    }
  }

  // Another mismatch between fest-swing and the EditorComboBox is the absence of any meaningful entries in the editor component's
  // InputMap and ActionMap; with nothing there, fest-swing fails to selectAllText, which is a component part of replaceText.
  override fun selectAllText(comboBox: JComboBox<*>) {
    val editor = accessibleEditorOf(comboBox) as? JComponent ?: return
    focus(editor)
    focusAndWaitForFocusGain(editor)
    // TODO(xof): how to get hold of the keystroke more generically?  Something in DefaultEditorKit?
    robot.pressAndReleaseKey(KeyEvent.VK_A, InputEvent.CTRL_MASK)
  }

  private fun accessibleEditorOf(comboBox: JComboBox<*>): Component? {
    return GuiActionRunner.execute(object : GuiQuery<Component>() {
      override fun executeInEDT(): Component? {
        return comboBox.editor.editorComponent
      }
    })
  }
}

class DependenciesFixture(
  val robot: Robot,
  val container: Container
) : ConfigPanelFixture() {

  override fun target(): Container = container
  override fun robot(): Robot = robot

  fun findDependenciesTable(): JTableFixture =
    JTableFixture(robot(), robot().finder().findByType<TableView<*>>(container))

  class DependenciesTreeFixture(
    val robot: Robot,
    val tree: JTree
  ) : JTreeFixture(robot, tree) {
    fun waitForTreeEntry(text: String) {
      Wait.seconds(5)
        .expecting("a tree entry '$text'")
        .until { treeContains(text) }
      waitForIdle()
    }

    private fun treeContains(text: String): Boolean {
      return GuiQuery.get {
        val root = tree.model.root as DefaultMutableTreeNode
        root.children().asSequence().forEach {
          if (it is DefaultMutableTreeNode) {
            when (val userObject = it.userObject) {
              is AbstractDependencyNode<*, *> -> if (text == userObject.name) return@get true
              is String -> if (text == userObject) return@get true
              else -> Unit
            }
          }
        }
        return@get false
      } ?: false
    }
  }

  fun findDependenciesTree() = DependenciesTreeFixture(robot(), robot().finder().findByName("dependenciesTree", JTree::class.java))

  fun findConfigurationCombo(): JComboBoxFixture =
    EditorComboBoxFixture(robot(), robot().finder().findByName(container, "configuration", JComboBox::class.java, true))

  fun clickAddLibraryDependency(): AddLibraryDependencyDialogFixture {
    clickToolButton("Add Dependency")
    val listFixture = JListFixture(robot(), getList())
    listFixture.dragAndClickItem(0 /* 1 Library Dependency */)  // Search by title does not work here.
    return AddLibraryDependencyDialogFixture.find(robot(), "Add Library Dependency")
  }

  fun clickAddModuleDependency(): AddModuleDependencyDialogFixture {
    clickToolButton("Add Dependency")
    waitForIdle()
    val listFixture = JListFixture(robot(), getList())
    listFixture.drag(2).dragAndClickItem(2 /* 3 Module Dependency */)  // Search by title does not work here.
    return AddModuleDependencyDialogFixture.find(robot(), "Add Module Dependency")
  }

  private fun findQuickFixHyperlink(panels: List<IssuesViewerPanel>, text: String): Pair<JEditorPane, Point>? {
    fun findIssuesView(panel: IssuesViewerPanel): JEditorPane? {
      return GuiActionRunner.execute(object : GuiQuery<JEditorPane>() {
        override fun executeInEDT(): JEditorPane? {
          return panel.issuesView
        }
      })
    }

    fun findAndScrollToHyperlinkText(editorPane: JEditorPane, text: String): Point? {
      return GuiActionRunner.execute(object : GuiQuery<Point>() {
        override fun executeInEDT(): Point? {
          val iterator = ElementIterator(editorPane.document.defaultRootElement)
          var e = iterator.current()
          while (e != null) {
            val a = e.attributes.getAttribute(HTML.Tag.A)
            if (a is AttributeSet) {
              val startOffset = e.startOffset
              val endOffset = e.endOffset
              val eText = e.document.getText(startOffset, endOffset - startOffset)
              if (eText == text) {
                val startRectangle = editorPane.modelToView(startOffset)
                val endRectangle = editorPane.modelToView(endOffset - 1)
                val x = when {
                  startRectangle.y == endRectangle.y -> (startRectangle.x + endRectangle.x + endRectangle.width) / 2
                  else -> (startRectangle.x + editorPane.width - editorPane.margin.right) / 2
                }
                val y = (startRectangle.y + startRectangle.height / 2)
                editorPane.scrollRectToVisible(startRectangle)
                return Point(x, y)
              }
            }
            e = iterator.next()
          }
          return null
        }
      })
    }

    panels.forEach { panel ->
      val editorPane = findIssuesView(panel) ?: return@forEach
      val point = findAndScrollToHyperlinkText(editorPane, text)
      if (point != null) {
        return editorPane to point
      }
    }
    return null
  }

  fun clickQuickFixHyperlink(text: String) {
    var paneAndPoint: Pair<JEditorPane, Point>? = null
    val panels = robot().finder().findAll { it is IssuesViewerPanel }.map { it as IssuesViewerPanel }
    Wait.seconds(5)
      .expecting("Quickfix hyperlink '$text'")
      .until { paneAndPoint = findQuickFixHyperlink(panels, text); paneAndPoint != null }
    val editorPane = paneAndPoint!!.first
    val point = paneAndPoint!!.second
    robot.click(editorPane, point)
  }
}

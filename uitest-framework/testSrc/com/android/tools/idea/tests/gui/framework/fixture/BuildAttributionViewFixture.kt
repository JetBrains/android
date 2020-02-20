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
package com.android.tools.idea.tests.gui.framework.fixture

import com.google.common.truth.Truth.assertThat
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBSplitter
import com.intellij.ui.treeStructure.Tree
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiTask
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.fixture.JTreeFixture
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class BuildAttributionViewFixture(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {
  val tree: BuildAttributionTreeFixture = BuildAttributionTreeFixture(robot, robot.finder().findByType(target, Tree::class.java))
  val visiblePage: JPanelFixture
    get() {
      val infoPanelContainer = robot().finder().findByType(target(), JBSplitter::class.java).secondComponent as JPanel
      return JPanelFixture(robot(), finder().findByName(infoPanelContainer, "infoPage", JPanel::class.java, true))
    }

  fun findHyperlabelByTextContainsAndClick(text: String) =
    HyperlinkLabelFixture(robot(), finder().find(visiblePage.target()) { it is HyperlinkLabel && it.text.contains(text) } as HyperlinkLabel)
      .clickLink(text)

  fun checkInitState() {
    tree.requireSelectedNodeNameContain("Plugins with tasks determining this build's duration")
    tree.requireRootContainInOrder(listOf(
      "Build: finished at",
      "Plugins with tasks determining this build's duration",
      "Tasks determining this build's duration",
      "Warnings"
    ))
  }

  fun checkWarningsNode(expectedChildren: List<String>, expectedWarningsCount: Int) {
    selectPageByPath(" Warnings ($expectedWarningsCount)", "Warnings")
    expandSelectedNodeWithKeyStroke()
    tree.requireSelectedNodeContainInOrder(expectedChildren)
  }

  fun requireOpenedPagePathAndHeader(selectedTreePath: String, pageHeaderText: String) {
    tree.requireSelection(selectedTreePath)
    visiblePage.label("pageHeader").requireText(pageHeaderText)
  }

  fun selectAndCheckBuildSummaryNode() {
    tree.selectRow(0)
    GuiTask.execute {
      val headerText = visiblePage.label("pageHeader").text()
      val headerTextExpectedPrefix = "Build finished at "
      assertThat(headerText).startsWith(headerTextExpectedPrefix)
      val dateTimeText = headerText!!.substring(headerTextExpectedPrefix.length)
      tree.requireSelectedNodeNameContain("Build: finished at $dateTimeText")
    }
  }

  fun selectPageByPath(treePath: String, expectedPageHeaderPattern: String) {
    tree.selectPath(treePath)
    requireOpenedPagePathAndHeader(treePath, expectedPageHeaderPattern)
  }

  fun expandSelectedNodeWithKeyStroke() {
    robot().pressAndReleaseKey(KeyEvent.VK_RIGHT)
  }

  fun selectedNextNodeWithKeyStroke() {
    robot().pressAndReleaseKey(KeyEvent.VK_DOWN)
  }

  class BuildAttributionTreeFixture(
    val robot: Robot,
    val tree: JTree
  ) : JTreeFixture(robot, tree) {

    fun requireSelectedNodeNameContain(namePattern: String) {
      GuiTask.execute {
        assertThat(tree.selectionCount).isEqualTo(1)
        assertThat(nodeToString(tree.selectionPath.lastPathComponent as DefaultMutableTreeNode)).containsMatch(namePattern)
      }
    }

    fun requireSelectedNodeContainInOrder(namePatterns: List<String>) {
      GuiTask.execute {
        val node = tree.selectionPath.lastPathComponent as DefaultMutableTreeNode
        requireNodeContainInOrder(node, namePatterns)
      }
    }

    fun requireRootContainInOrder(nameRegexes: List<String>) {
      GuiTask.execute {
        val root = tree.model.root as DefaultMutableTreeNode
        requireNodeContainInOrder(root, nameRegexes)
      }
    }

    private fun requireNodeContainInOrder(parentNode: DefaultMutableTreeNode, nameRegexes: List<String>) {
      assertThat(parentNode.children().asSequence().count()).isEqualTo(nameRegexes.size)
      parentNode.children().asSequence().forEachIndexed { index, node ->
        if (node is DefaultMutableTreeNode) {
          val nodeText = nodeToString(node)
          assertThat(nodeText).containsMatch(nameRegexes[index])
        }
      }
    }

    private fun nodeToString(node: DefaultMutableTreeNode): String =
      tree.convertValueToText(node, false, false, false, 0, false)
  }
}
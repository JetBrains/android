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
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.treeStructure.Tree
import org.fest.swing.core.NameMatcher
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiTask
import org.fest.swing.fixture.JCheckBoxFixture
import org.fest.swing.fixture.JComboBoxFixture
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.fixture.JTreeFixture
import java.awt.event.KeyEvent
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.text.JTextComponent

class BuildAnalyzerViewFixture(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {
  val pageComboBox: JComboBoxFixture
    get() = JComboBoxFixture(robot(), finder().findByName(target(), "dataSetCombo", ComboBox::class.java, true))
  val overviewPage: OverviewPageFixture
    get() = OverviewPageFixture(robot(), finder().findByName(target(), "build-overview", JPanel::class.java))
  val tasksPage: BuildAnalyzerMasterDetailsPageFixture
    get() = BuildAnalyzerMasterDetailsPageFixture(robot(), finder().findByName(target(), "tasks-view", JPanel::class.java))
  val warningsPage: BuildAnalyzerMasterDetailsPageFixture
    get() = BuildAnalyzerMasterDetailsPageFixture(robot(), finder().findByName(target(), "warnings-view", JPanel::class.java))
  val tasksGroupingCheckbox: JCheckBoxFixture
    get() = JCheckBoxFixture(robot(), finder().findByName(target(), "tasksGroupingCheckBox", JCheckBox::class.java))

  private fun verifyTasksGroupingCheckboxNotShown() {
    assertThat(finder().findAll(target(), NameMatcher("tasksGroupingCheckBox", JCheckBox::class.java, true))).isEmpty()
  }

  fun openOverviewPage(): OverviewPageFixture {
    robot().waitForIdle()
    pageComboBox.selectItem("Overview")
    return overviewPage.also {
      it.requireVisible()
      verifyTasksGroupingCheckboxNotShown()
    }
  }

  fun openTasksPage(): BuildAnalyzerMasterDetailsPageFixture {
    robot().waitForIdle()
    pageComboBox.selectItem("Tasks")
    return tasksPage.also {
      it.requireVisible()
      tasksGroupingCheckbox.requireVisible()
    }
  }

  fun openWarningsPage(): BuildAnalyzerMasterDetailsPageFixture {
    robot().waitForIdle()
    pageComboBox.selectItem("Warnings")
    return warningsPage.also {
      it.requireVisible()
      verifyTasksGroupingCheckboxNotShown()
    }
  }

  class OverviewPageFixture(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {
    fun verifyLinkPresent(linkToVerify: String) {
      findHyperlinkLabelByTextContains(linkToVerify, robot(), target()).requireVisible()
    }
  }

  class BuildAnalyzerMasterDetailsPageFixture(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {
    val tree: JTreeFixture
      get() = JTreeFixture(robot(), robot().finder().findByType(target(), Tree::class.java))

    /**
     * Find details page by it's name. Page name is the same as the corresponding node id
     * (see [TaskViewDetailPagesFactory.createDetailsPage(nodeDescriptor)]).
     *
     * Note, pages are created lazily so they could not be found until first open.
     */
    fun findDetailsPanel(name: String): DetailsPageFixture =
      DetailsPageFixture(robot(), finder().findByName(target(), name, JPanel::class.java, true))

    /**
     *  This supposed to select parent node of currently selected node when collapsed,
     *  or collapse current node when expanded.
     */
    fun pressKeyboardLeftOnTree() {
      tree.focus()
      robot().pressAndReleaseKey(KeyEvent.VK_LEFT)
    }

    /**
     *  This supposed to expand currently selected node when collapsed or select next node if already expanded.
     */
    fun pressKeyboardRightOnTree() {
      tree.focus()
      robot().pressAndReleaseKey(KeyEvent.VK_RIGHT)
    }

    /**
     *  This supposed to select next node visible.
     */
    fun pressKeyboardDownOnTree() {
      tree.focus()
      robot().pressAndReleaseKey(KeyEvent.VK_DOWN)
    }

  }

  class DetailsPageFixture(robot: Robot, target: JPanel) : JPanelFixture(robot, target) {

    fun clickGenerateReport() {
      JTextComponentWithHtmlFixture.create(robot(), finder().findByType(target(), JTextComponent::class.java))
        .clickOnLink("Generate report")
    }

    fun clickNavigationLink(linkText: String) {
      JTextComponentWithHtmlFixture.create(robot(), finder().findByType(target(), JTextComponent::class.java))
        .clickOnLink(linkText)
    }
  }
}

private fun findHyperlinkLabelByTextContains(text: String, robot: Robot, target: JPanel): HyperlinkLabelFixture {
  return HyperlinkLabelFixture(robot, robot.finder().find(target) { it is HyperlinkLabel && it.text.contains(text) } as HyperlinkLabel)
}

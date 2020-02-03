/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui.tree

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.controllers.TaskIssueReporter
import com.android.build.attribution.ui.controllers.TreeNodeSelector
import com.android.build.attribution.ui.panels.AbstractBuildAttributionInfoPanel
import com.android.build.attribution.ui.warningIcon
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.SimpleNode
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import javax.swing.Icon
import javax.swing.JPanel

class AbstractBuildAttributionNodeTest {

  @get:Rule
  val projectRule: ProjectRule = ProjectRule()

  @get:Rule
  val edtRule = EdtRule()

  @Test
  fun testPresentation() {
    val node = TestNode(mockRoot, warningIcon(), "1234")

    val coloredText = node.presentation.coloredText
    Truth.assertThat(coloredText).hasSize(2)
    Truth.assertThat(coloredText[0].text).isEqualTo(" Test Name")
    Truth.assertThat(coloredText[0].attributes).isEqualTo(SimpleTextAttributes.REGULAR_ATTRIBUTES)
    Truth.assertThat(coloredText[1].text).isEqualTo(" 1234")
    Truth.assertThat(coloredText[1].attributes).isEqualTo(SimpleTextAttributes.GRAYED_ATTRIBUTES)
    Truth.assertThat(node.presentation.getIcon(true)).isEqualTo(warningIcon())
  }

  @Test
  fun testPresentationWithoutWarningsSuffix() {
    val node = TestNode(mockRoot, warningIcon(), null)

    val coloredText = node.presentation.coloredText
    Truth.assertThat(coloredText).hasSize(1)
    Truth.assertThat(coloredText[0].text).isEqualTo(" Test Name")
    Truth.assertThat(coloredText[0].attributes).isEqualTo(SimpleTextAttributes.REGULAR_ATTRIBUTES)
    Truth.assertThat(node.presentation.getIcon(true)).isEqualTo(warningIcon())
  }

  @Test
  fun testPresentationWithoutIcon() {
    val node = TestNode(mockRoot, null, "1234")

    val coloredText = node.presentation.coloredText
    Truth.assertThat(coloredText).hasSize(2)
    Truth.assertThat(coloredText[0].text).isEqualTo(" Test Name")
    Truth.assertThat(coloredText[0].attributes).isEqualTo(SimpleTextAttributes.REGULAR_ATTRIBUTES)
    Truth.assertThat(coloredText[1].text).isEqualTo(" 1234")
    Truth.assertThat(coloredText[1].attributes).isEqualTo(SimpleTextAttributes.GRAYED_ATTRIBUTES)
    Truth.assertThat(node.presentation.getIcon(true)).isNull()
  }

  @Test
  fun testFirstLevelNodeId() {
    val node = TestNode(mockRoot, warningIcon(), "1234")
    Truth.assertThat(node.nodeId).isEqualTo("Test Name")
  }

  @Test
  fun testSecondLevelNodeId() {
    val node1 = TestNode(mockRoot, warningIcon(), "1234")
    val node2 = TestNode(node1, warningIcon(), "1234")
    Truth.assertThat(node2.nodeId).isEqualTo("Test Name > Test Name")
  }

  @Test
  fun testComponentCreation() {
    val node = spy(TestNode(mockRoot, warningIcon(), "1234"))

    Truth.assertThat(node.component).isSameAs(testComponent)
    Truth.assertThat(node.component.name).isEqualTo("infoPage")
    verify(node, times(1)).createComponent()
  }

  private val testComponent = object : AbstractBuildAttributionInfoPanel() {
    override fun createHeader() = JPanel()
    override fun createBody() = JPanel()
  }
  private val mockRoot = object : ControllersAwareBuildAttributionNode(null) {
    override fun buildChildren(): Array<SimpleNode> {
      Assert.fail("This method is not supposed to be called in tests where this mock is used as root.")
      return emptyArray()
    }

    override val nodeSelector = Mockito.mock(TreeNodeSelector::class.java)
    override val analytics = BuildAttributionUiAnalytics(projectRule.project)
    override val issueReporter = Mockito.mock(TaskIssueReporter::class.java)
  }

  private open inner class TestNode(
    parent: ControllersAwareBuildAttributionNode,
    override val presentationIcon: Icon?,
    override val issuesCountsSuffix: String?
  ) : AbstractBuildAttributionNode(parent, "Test Name") {
    override val pageType = BuildAttributionUiEvent.Page.PageType.UNKNOWN_PAGE
    override val timeSuffix = "5678"

    override fun createComponent() = testComponent

    override fun buildChildren(): Array<SimpleNode> {
      Assert.fail("Should not be called in this test.")
      return emptyArray()
    }
  }
}
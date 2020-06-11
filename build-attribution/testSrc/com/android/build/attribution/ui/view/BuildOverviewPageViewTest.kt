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
package com.android.build.attribution.ui.view

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.model.BuildAnalyzerViewModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.tools.adtui.TreeWalker
import com.google.common.truth.Truth
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.UIUtil
import org.junit.Test
import org.mockito.Mockito
import java.awt.Component
import javax.swing.JEditorPane
import javax.swing.JLabel

class BuildOverviewPageViewTest {

  private val model = BuildAnalyzerViewModel(MockUiData())
  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  @Test
  fun testPage() {
    val view = BuildOverviewPageView(model, mockHandlers)
    Truth.assertThat(view.component.name).isEqualTo("build-overview")
    val text = TreeWalker(view.component).descendants()
      .mapNotNull { visibleText(it) }
      .joinToString(separator = "\n")

    val expectedBuildFinishedString = DateFormatUtil.formatDateTime(model.reportUiData.buildSummary.buildFinishedTimestamp)
    Truth.assertThat(text).isEqualTo("""
      <b>Build finished on $expectedBuildFinishedString</b>
      Total build duration was 20.0s.
      
      Includes:
      Build configuration: 4.0s
      Critical path tasks execution: 15.0s
      
      <b>Common views into this build</b>
      [Tasks impacting build duration]
      [Plugins with tasks impacting build duration]
      [All warnings]
    """.trimIndent())
  }

  @Test
  fun testLinks() {
    val view = BuildOverviewPageView(model, mockHandlers)
    val linksPanel = TreeWalker(view.component).descendants().single { it.name == "links" }
    val links = TreeWalker(linksPanel).descendants().filterIsInstance(HyperlinkLabel::class.java)
    Truth.assertThat(links).hasSize(3)

    // Act / assert links handling
    links[0].doClick()
    Mockito.verify(mockHandlers).changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.UNGROUPED)

    links[1].doClick()
    Mockito.verify(mockHandlers).changeViewToTasksLinkClicked(TasksDataPageModel.Grouping.BY_PLUGIN)

    links[2].doClick()
    Mockito.verify(mockHandlers).changeViewToWarningsLinkClicked()
  }

  @Test
  fun testAdditionalControls() {
    val view = BuildOverviewPageView(model, mockHandlers)
    Truth.assertThat(view.additionalControls.name).isEqualTo("build-overview-additional-controls")
    Truth.assertThat(view.additionalControls.components).isEmpty()
  }

  private fun visibleText(component: Component): String? = when (component) {
    is JLabel -> component.text
    is JEditorPane -> clearHtml(component.text)
    is HyperlinkLabel -> "[${component.text}]"
    else -> null
  }

  private fun clearHtml(html: String): String = UIUtil.getHtmlBody(html)
    .trimIndent()
    .replace("\n","")
    .replace("<br>","\n")
}
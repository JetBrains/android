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

import com.android.build.attribution.BuildAttributionWarningsFilter
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.defaultTotalBuildDurationMs
import com.android.build.attribution.ui.mockDownloadsData
import com.android.build.attribution.ui.model.BuildOverviewPageModel
import com.android.build.attribution.ui.model.TasksDataPageModel
import com.android.tools.adtui.TreeWalker
import com.google.common.truth.Truth
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.UIUtil
import org.junit.Test
import org.mockito.Mockito
import java.awt.Component
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel

class BuildOverviewPageViewTest {

  private val warningSuppressions = BuildAttributionWarningsFilter()
  private val model = BuildOverviewPageModel(MockUiData(), warningSuppressions)
  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  @Test
  fun testPage() {
    val view = BuildOverviewPageView(model, mockHandlers)
    Truth.assertThat(view.component.name).isEqualTo("build-overview")
    val descendantNames = TreeWalker(view.component).descendants().mapNotNull { it.name }
    Truth.assertThat(descendantNames).containsAllOf("info", "links", "memory")
  }

  @Test
  fun testInfoContent() {
    val view = BuildOverviewPageView(model, mockHandlers)
    val infoPanel = TreeWalker(view.component).descendants().single { it.name == "info" }
    val text = TreeWalker(infoPanel).descendants()
      .mapNotNull { visibleText(it) }
      .joinToString(separator = "\n")

    val expectedBuildFinishedString = DateFormatUtil.formatDateTime(model.reportUiData.buildSummary.buildFinishedTimestamp)
    Truth.assertThat(text).isEqualTo("""
      <b>Build finished on $expectedBuildFinishedString</b>
      Total build duration was 20.0s
      
      Includes:
      Build configuration: 4.0s - <a href="configuration-cache">Optimize this</a>
      Critical path tasks execution: 15.0s
      
      """.trimIndent())
  }

  @Test
  fun testLinks() {
    val view = BuildOverviewPageView(model, mockHandlers)
    val linksPanel = TreeWalker(view.component).descendants().single { it.name == "links" }

    val linksPanelContent = TreeWalker(linksPanel).descendants()
      .mapNotNull { visibleText(it) }
      .joinToString(separator = "\n")

    Truth.assertThat(linksPanelContent).isEqualTo("""
      <b>Common views into this build</b>
      [Tasks impacting build duration]
      [Plugins with tasks impacting build duration]
      [All warnings]
    """.trimIndent())

    val links = TreeWalker(linksPanel).descendants().filterIsInstance(HyperlinkLabel::class.java)
    Truth.assertThat(links).hasSize(3)

    // Act / assert links handling
    links[0].doClick()
    Mockito.verify(mockHandlers).changeViewToTasksLinkClicked(null)

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

  @Test
  fun testMemoryUtilizationInfo() {
    val model = BuildOverviewPageModel(MockUiData(gcTimeMs = (defaultTotalBuildDurationMs * 0.8).toLong()), warningSuppressions)
    val view = BuildOverviewPageView(model, mockHandlers)
    val memoryPanel = TreeWalker(view.component).descendants().single { it.name == "memory" }

    Truth.assertThat(model.shouldWarnAboutGC).isTrue()
    Truth.assertThat(memoryPanel.isVisible).isTrue()

    val button = TreeWalker(memoryPanel).descendants().filterIsInstance(JButton::class.java).single()
    Truth.assertThat(button.text).isEqualTo("Edit memory settings")
    button.doClick()
    Mockito.verify(mockHandlers).openMemorySettings()
  }

  @Test
  fun testNoGcSettingWarning() {
    val mockData = MockUiData().apply {
      buildSummary = mockBuildOverviewData(javaVersionUsed = 11, isGarbageCollectorSettingSet = false)
    }
    val model = BuildOverviewPageModel(mockData, warningSuppressions)
    val view = BuildOverviewPageView(model, mockHandlers)
    val memoryPanel = TreeWalker(view.component).descendants().single { it.name == "memory" }

    Truth.assertThat(model.shouldWarnAboutNoGCSetting).isTrue()
    Truth.assertThat(memoryPanel.isVisible).isTrue()

    val textPane = TreeWalker(memoryPanel).descendants().single { it.name == "no-gc-setting-warning" }

    Truth.assertThat(textPane.isVisible).isTrue()
    Truth.assertThat(visibleText(textPane)).contains("The default garbage collector was used in this build running with JDK 11.")
  }

  @Test
  fun testDownloadsOverviewInfo() {
    val mockData = MockUiData().apply {
      downloadsData = mockDownloadsData()
    }
    val model = BuildOverviewPageModel(mockData, warningSuppressions)
    val view = BuildOverviewPageView(model, mockHandlers)
    val html = view.generateInfoPanelHtml()

    Truth.assertThat(html).isEqualTo("""
      <b>Build finished on 1/30/20, 12:21 PM</b><br/>
      Total build duration was 20.0s<br/>
      <br/>
      Includes:<br/>
      Build configuration: 4.0s - <a href='configuration-cache'>Optimize this</a><br/>
      Critical path tasks execution: 15.0s<br/>
      <a href='downloads-view'>Files download</a>: 1.5s <icon alt='&lt;html&gt;
      This build had 8 network requests,&lt;br/&gt;
      downloaded in total 310 kB in 1.5s.
      &lt;/html&gt;' src='AllIcons.General.ContextHelp'><br/>
    """.trimIndent())
  }

  @Test
  fun testInfoContentForDownloadsAnalyzerDisabled() {
    val mockData = MockUiData().apply {
      downloadsData = DownloadsAnalyzer.AnalyzerIsDisabled
    }
    verifyFileDownloadsInfoNotVisible(mockData)
  }

  @Test
  fun testInfoContentForDownloadsAnalyzerWhenNoDataBecauseOfGradle() {
    val mockData = MockUiData().apply {
      downloadsData = DownloadsAnalyzer.GradleDoesNotProvideEvents
    }
    verifyFileDownloadsInfoNotVisible(mockData)
  }

  @Test
  fun testInfoContentForEmptyDownloadsAnalyzer() {
    val mockData = MockUiData().apply {
      downloadsData = DownloadsAnalyzer.ActiveResult(emptyList())
    }
    verifyFileDownloadsInfoNotVisible(mockData)
  }

  private fun verifyFileDownloadsInfoNotVisible(mockData: MockUiData) {
    val model = BuildOverviewPageModel(mockData, warningSuppressions)
    val view = BuildOverviewPageView(model, mockHandlers)
    val html = view.generateInfoPanelHtml()

    Truth.assertThat(html).isEqualTo("""
      <b>Build finished on 1/30/20, 12:21 PM</b><br/>
      Total build duration was 20.0s<br/>
      <br/>
      Includes:<br/>
      Build configuration: 4.0s - <a href='configuration-cache'>Optimize this</a><br/>
      Critical path tasks execution: 15.0s<br/>
    """.trimIndent())
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
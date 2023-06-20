/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.hyperlinks

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.sdk.sources.SdkSourcePositionFinder
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.filters.CompositeFilter
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.impl.HyperlinkInfoFactoryImpl
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [SdkSourceRedirectFilter]
 */
@RunsInEdt
class SdkSourceRedirectFilterTest {
  private val projectRule = ProjectRule()

  private val mockSdkSourcePositionFinder: SdkSourcePositionFinder = mock()

  @get:Rule
  val rule = RuleChain(
    projectRule,
    ProjectServiceRule(projectRule, SdkSourcePositionFinder::class.java, mockSdkSourcePositionFinder),
    EdtRule()
  )

  private val project get() = projectRule.project

  @Test
  fun applyFilter_noFileLinks() {
    val info = OpenUrlHyperlinkInfo("")
    val line = "1 Foo 2"
    val delegate = TestFilter("Foo", info)
    val filter = SdkSourceRedirectFilter(project, delegate)
    filter.apiLevel = 30

    val result = filter.applyFilter(line, 100)

    assertThat(result?.resultItems?.map { it.toInfo(line) }).containsExactly(ResultInfo("Foo", "OpenUrlHyperlinkInfo"))
  }

  @Test
  fun applyFilter_nullFileLinks() {
    val line = "1 Foo 2"
    val delegate = TestFilter("Foo", null)
    val filter = SdkSourceRedirectFilter(project, delegate)
    filter.apiLevel = 30

    val result = filter.applyFilter(line, 100)

    assertThat(result?.resultItems?.map { it.toInfo(line) }).containsExactly(ResultInfo("Foo", null))
  }

  @Test
  fun applyFilter_withOpenFileHyperlinkInfo() {
    val file = LightVirtualFile()
    val info = OpenFileHyperlinkInfo(project, file, 10)
    val line = "1 Foo 2"
    val delegate = TestFilter("Foo", info)
    val filter = SdkSourceRedirectFilter(project, delegate)
    filter.apiLevel = 30

    val result = filter.applyFilter(line, 100)

    assertThat(result?.resultItems?.map { it.toInfo(line) }).containsExactly(ResultInfo("Foo", "SdkSourceRedirectLinkInfo"))
    val hyperlinkInfo = result?.firstHyperlinkInfo as SdkSourceRedirectLinkInfo
    assertThat(hyperlinkInfo.apiLevel).isEqualTo(30)
    assertThat(hyperlinkInfo.files).containsExactly(file)
  }

  @Test
  fun applyFilter_withOpenFileHyperlink1Info() {
    val file1 = LightVirtualFile()
    val file2 = LightVirtualFile()
    val info = HyperlinkInfoFactoryImpl().createMultipleFilesHyperlinkInfo(listOf(file1, file2), 10, project)
    val line = "1 Foo 2"
    val delegate = TestFilter("Foo", info)
    val filter = SdkSourceRedirectFilter(project, delegate)
    filter.apiLevel = 22

    val result = filter.applyFilter(line, 100)

    assertThat(result?.resultItems?.map { it.toInfo(line) }).containsExactly(ResultInfo("Foo", "SdkSourceRedirectLinkInfo"))
    val hyperlinkInfo = result?.firstHyperlinkInfo as SdkSourceRedirectLinkInfo
    assertThat(hyperlinkInfo.apiLevel).isEqualTo(22)
    assertThat(hyperlinkInfo.files).containsExactly(file1, file2)
  }

  @Test
  fun applyFilter_withMultipleResults() {
    val file1 = LightVirtualFile()
    val file2 = LightVirtualFile()
    val info1 = HyperlinkInfoFactoryImpl().createMultipleFilesHyperlinkInfo(listOf(file1, file2), 10, project)
    val info2 = OpenFileHyperlinkInfo(project, file1, 10)
    val info3 = OpenUrlHyperlinkInfo("")
    val line = "1 Foo Bar 2"
    val delegate = CompositeFilter(project, listOf(
      TestFilter("1", info1),
      TestFilter("Foo", info2),
      TestFilter("Bar", info3),
      TestFilter("2", null),
    )).apply { setForceUseAllFilters(true) }
    val filter = SdkSourceRedirectFilter(project, delegate)
    filter.apiLevel = 22

    val result = filter.applyFilter(line, 100)

    assertThat(result?.resultItems?.map { it.toInfo(line) }).containsExactly(
      ResultInfo("1", "SdkSourceRedirectLinkInfo"),
      ResultInfo("Foo", "SdkSourceRedirectLinkInfo"),
      ResultInfo("Bar", "OpenUrlHyperlinkInfo"),
      ResultInfo("2", null),
      )
  }

  @Test
  fun applyFilter_withoutSdk() {
    val file1 = LightVirtualFile()
    val file2 = LightVirtualFile()
    val info1 = HyperlinkInfoFactoryImpl().createMultipleFilesHyperlinkInfo(listOf(file1, file2), 10, project)
    val info2 = OpenFileHyperlinkInfo(project, file1, 10)
    val info3 = OpenUrlHyperlinkInfo("")
    val line = "1 Foo Bar 2"
    val delegate = CompositeFilter(project, listOf(
      TestFilter("1", info1),
      TestFilter("Foo", info2),
      TestFilter("Bar", info3),
      TestFilter("2", null),
    )).apply { setForceUseAllFilters(true) }
    val filter = SdkSourceRedirectFilter(project, delegate)
    filter.apiLevel = null

    val result = filter.applyFilter(line, 100)

    assertThat(result?.resultItems?.map { it.toInfo(line) }).containsExactly(
      ResultInfo("1", "MultipleFilesHyperlinkInfo"),
      ResultInfo("Foo", "OpenFileHyperlinkInfo"),
      ResultInfo("Bar", "OpenUrlHyperlinkInfo"),
      ResultInfo("2", null),
    )
  }

  private class TestFilter(private val text: String, private val hyperlinkInfo: HyperlinkInfo?) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
      val start = line.indexOf(text)
      if (start < 0) {
        return null
      }

      return Filter.Result(start, start + text.length, hyperlinkInfo)
    }
  }

  private data class ResultInfo(val text: String, val classname: String?)

  private fun ResultItem.toInfo(line: String): ResultInfo {
    return ResultInfo(line.substring(highlightStartOffset, highlightEndOffset), hyperlinkInfo?.let { it::class.simpleName })
  }
}
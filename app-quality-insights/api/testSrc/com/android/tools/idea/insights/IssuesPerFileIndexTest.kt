/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class IssuesPerFileIndexTest {

  @get:Rule val projectRule = ProjectRule()

  @Test
  fun testSeparateIndicesPerInsightProvider() {
    val index = IssuesPerFileIndex(projectRule.project)
    val firstProviderKey = InsightsProviderKey("firstProviderKey")
    val secondProviderKey = InsightsProviderKey("secondProviderKey")
    index.updateIssueIndex(createIssues(listOf(ISSUE1, ISSUE2)), firstProviderKey)

    val firstIssuesPerFileName = index.getIssuesPerFilename(firstProviderKey)
    assertThat(firstIssuesPerFileName.size()).isEqualTo(6)

    index.updateIssueIndex(createIssues(emptyList()), secondProviderKey)
    val secondIssuesPerFileName = index.getIssuesPerFilename(secondProviderKey)
    assertThat(secondIssuesPerFileName.isEmpty).isTrue()
  }

  private fun createIssues(issues: List<AppInsightsIssue>) =
    LoadingState.Ready(Selection(null, issues))
}

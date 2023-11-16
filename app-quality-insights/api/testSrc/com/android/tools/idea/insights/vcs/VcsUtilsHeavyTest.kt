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
package com.android.tools.idea.insights.vcs

import com.android.tools.idea.insights.PROJECT_ROOT_PREFIX
import com.android.tools.idea.insights.RepoInfo
import com.android.tools.idea.insights.VCS_CATEGORY
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test

class VcsUtilsHeavyTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val vcsInsightsRule = InsightsVcsTestRule(projectRule)

  @get:Rule val rule = RuleChain(projectRule, vcsInsightsRule)

  @Test
  fun `can locate repo`() {
    val repoInfo =
      RepoInfo(vcsKey = VCS_CATEGORY.TEST_VCS, rootPath = PROJECT_ROOT_PREFIX, revision = "123")

    // Act
    val found = repoInfo.locateRepository(projectRule.project)

    // Assert
    assertThat(found).isNotNull()
    assertThat(found!!.root).isEqualTo(vcsInsightsRule.projectBaseDir)
    assertThat(found.vcs).isEqualTo(vcsInsightsRule.vcs)
  }

  @Test
  fun `can not locate repo due to invalid root path`() {
    val repoInfo = RepoInfo(vcsKey = VCS_CATEGORY.TEST_VCS, rootPath = "", revision = "123")

    // Act
    val found = repoInfo.locateRepository(projectRule.project)

    // Assert
    assertThat(found).isNull()
  }

  @Test
  fun `can not locate repo due to no Git support`() {
    // ... as in our test setup, we only registered [MockVcsForAppInsights].
    val repoInfo =
      RepoInfo(vcsKey = VCS_CATEGORY.GIT, rootPath = PROJECT_ROOT_PREFIX, revision = "123")

    // Act
    val found = repoInfo.locateRepository(projectRule.project)

    // Assert
    assertThat(found).isNull()
  }

  @Test
  fun `create vcs document`() {
    val file = projectRule.fixture.configureByText("Foo.kt", "class Foo {}")
    val document =
      createVcsDocument(VCS_CATEGORY.TEST_VCS, file.virtualFile, "1", projectRule.project)

    assertThat(document).isNotNull()
    assertThat(document!!.text).isEqualTo("class Foo {}")
  }
}

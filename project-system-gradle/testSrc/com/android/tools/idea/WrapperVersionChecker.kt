/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea

import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.util.CompatibleGradleVersion
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class WrapperVersionChecker {

  val projectRule = AndroidGradleProjectRule();

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  val project: Project
    get() = projectRule.project

  @Test
  fun `check gradle versions are mapped to respective sha-256`() {
    listOf(
      "9.2.1",
      "9.2.0",
      "9.1.0",
      "9.0.0",
      "8.14.2",
    ).forEach {
      val version = GradleVersion.version(it)
      val distributionSha = GradleWrapper.getDistributionSha256(version, true)
      assertWithMessage("Cannot find SHA-256 for Gradle distribution $version. " +
                        "Run ./update-gradle-sha256-list script to update the 'gradle-sha256-list.txt' file.")
        .that(distributionSha ?: "[no entry found]")
        .hasLength(64)
    }
  }

  @Test
  fun `check SHA-256 list can be read from file`() {
    assertThat(GradleWrapper.distributionsChecksums).isNotEmpty()
  }

  @Test
  fun `check distribution checksums contain only SHA-256 and filenames`() {
    GradleWrapper.distributionsChecksums.onEach { (filename, sha) ->
      assertThat(sha).hasLength(64)
      assertThat(filename).startsWith("gradle-")
      assertThat(filename).endsWith("-bin.zip")
    }
  }

  @Test
  fun `check if NPW gradle version is found in SHA-256 list`() {
    val version = CompatibleGradleVersion.getCompatibleGradleVersion(AgpVersions.newProject).version
    val distributionSha256 = GradleWrapper.getDistributionSha256(version, true)
    assertWithMessage("Cannot find SHA-256 for NPW Gradle distribution $version. " +
                      "Run ./update-gradle-sha256-list script to update the 'gradle-sha256-list.txt' file.")
      .that(distributionSha256 ?: "[no entry found]")
      .hasLength(64)
  }

  @Test
  @RunsInEdt
  fun `local distribution with a non-standard name gets calculated SHA-256`() {
    val wrapper = GradleWrapper.create(File(project.basePath!!), project)
    wrapper.updateDistribution(
      File(project.basePath!!, "fork-distribution.zip")
        .apply {
          createNewFile()
          writeText("Demo file content")
        })
    assertThat(GradleWrapper.distributionsChecksums.values)
      .doesNotContain("fork-distribution.zip")
    assertThat(GradleWrapper.distributionsChecksums.keys)
      .doesNotContain("128cb5a7d324a4936b0ffbb7770161f72b2f51c676adbb45af792e7679d9672f")

    assertWithMessage("SHA-256 calculated for the local distribution is not as expected")
      .that(wrapper.distributionSha256)
      .isEqualTo("128cb5a7d324a4936b0ffbb7770161f72b2f51c676adbb45af792e7679d9672f")
  }

  @Test
  @RunsInEdt
  fun `local distribution with a standard name gets correct SHA-256`() {
    val wrapper = GradleWrapper.create(File(project.basePath!!), project)
    wrapper.updateDistribution(
      File(project.basePath!!, "gradle-9.2.0-bin.zip")
        .apply {
          createNewFile()
          writeText("Demo file content")
        })
    assertWithMessage("SHA-256 calculated for the local standard distribution is not as expected")
      .that(wrapper.distributionSha256)
      .isEqualTo("df67a32e86e3276d011735facb1535f64d0d88df84fa87521e90becc2d735444")
  }


  @Test
  @RunsInEdt
  fun `inability to read local distribution file leaves no SHA-256 in wrapper`() {
    val wrapper = GradleWrapper.create(File(project.basePath!!), project)
    wrapper.updateDistribution(File(project.basePath!!, "missing_file.zip"))
    assertWithMessage("SHA-256 was not expected to be found in wrapper")
      .that(wrapper.distributionSha256)
      .isNull()
  }
}
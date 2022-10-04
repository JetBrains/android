/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.builder.model.LintOptions
import com.android.tools.idea.gradle.model.IdeLintOptions
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.android.tools.idea.gradle.project.model.IdeModelTestUtils.assertEqualsOrSimilar
import com.android.tools.idea.gradle.project.model.IdeModelTestUtils.verifyUsageOfImmutableCollections
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

/** Tests for [IdeLintOptions].  */
class IdeLintOptionsTest {
  @Test
  fun constructor() {
    val original: LintOptions = object : LintOptions {
      override val disable: Set<String>
        get() = ImmutableSet.of("disable")
      override val enable: Set<String>
        get() = ImmutableSet.of("enable")
      override val check: Set<String>
        get() = ImmutableSet.of("check")
      override val isAbortOnError: Boolean
        get() = true
      override val isAbsolutePaths: Boolean
        get() = true
      override val isNoLines: Boolean
        get() = false
      override val isQuiet: Boolean
        get() = false
      override val isCheckAllWarnings: Boolean
        get() = false
      override val isIgnoreWarnings: Boolean
        get() = false
      override val isWarningsAsErrors: Boolean
        get() = false
      override val isCheckTestSources: Boolean
        get() = false
      override val isIgnoreTestSources: Boolean
        get() = false
      override val isCheckGeneratedSources: Boolean
        get() = false
      override val isExplainIssues: Boolean
        get() = true
      override val isShowAll: Boolean
        get() = false
      override val lintConfig: File?
        get() = File("lintConfig")
      override val textReport: Boolean
        get() = false
      override val textOutput: File?
        get() = File("textOutput")
      override val htmlReport: Boolean
        get() = true
      override val htmlOutput: File?
        get() = File("htmlOutput")
      override val xmlReport: Boolean
        get() = true
      override val xmlOutput: File?
        get() = File("xmlOutput")
      override val sarifReport: Boolean
        get() = false
      override val sarifOutput: File?
        get() = null
      override val isCheckReleaseBuilds: Boolean
        get() = true
      override val isCheckDependencies: Boolean
        get() = false
      override val baselineFile: File?
        get() = File("baselineFile")
      override val severityOverrides: Map<String, Int>?
        get() = ImmutableMap.of("key", 1)
    }
    val copy: IdeLintOptions = IdeLintOptionsImpl(
      baselineFile = original.baselineFile,
      lintConfig = original.lintConfig,
      severityOverrides = original.severityOverrides,
      isCheckTestSources = false,
      isCheckDependencies = false,
      disable = original.disable,
      enable = original.enable,
      check = original.check,
      isAbortOnError = true,
      isAbsolutePaths = true,
      isNoLines = false,
      isQuiet = false,
      isCheckAllWarnings = false,
      isIgnoreWarnings = false,
      isWarningsAsErrors = false,
      isIgnoreTestSources = false,
      isIgnoreTestFixturesSources = false,
      isCheckGeneratedSources = false,
      isCheckReleaseBuilds = true,
      isExplainIssues = true,
      isShowAll = false,
      textReport = false,
      textOutput = original.textOutput,
      htmlReport = true,
      htmlOutput = original.htmlOutput,
      xmlReport = true,
      xmlOutput = original.xmlOutput,
      sarifReport = false,
      sarifOutput = null)
    Truth.assertThat(copy.baselineFile).isEqualTo(original.baselineFile)
    Truth.assertThat(copy.lintConfig).isEqualTo(original.lintConfig)
    Truth.assertThat(copy.severityOverrides).isEqualTo(original.severityOverrides)
    Truth.assertThat(copy.isCheckTestSources).isEqualTo(original.isCheckTestSources)
    Truth.assertThat(copy.isCheckDependencies).isEqualTo(original.isCheckDependencies)
    assertEqualsOrSimilar(original, copy)
    verifyUsageOfImmutableCollections(copy)
  }
}
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
import com.android.tools.idea.gradle.model.stubs.LintOptionsStub
import com.android.tools.idea.gradle.model.IdeLintOptions
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl
import com.google.common.truth.Truth
import org.junit.Test

/** Tests for [IdeLintOptions].  */
class IdeLintOptionsTest {
  @Test
  fun constructor() {
    val original: LintOptions = LintOptionsStub()
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
  }
}
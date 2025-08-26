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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeLintOptions
import java.io.File

import java.io.Serializable

data class IdeLintOptionsImpl(
  override val baselineFile: FileImpl? = null,
  override val lintConfig: FileImpl? = null,
  override val severityOverrides: Map<String, Int>? = null,
  override val isCheckTestSources: Boolean = false,
  override val isCheckDependencies: Boolean = false,
  override val disable: Set<String> = mutableSetOf(), // instead of emptySet, because of ModelSerializationTest
  override val enable: Set<String> = mutableSetOf(),
  override val check: Set<String>? = null,
  override val isAbortOnError: Boolean = true,
  override val isAbsolutePaths: Boolean = true,
  override val isNoLines: Boolean = false,
  override val isQuiet: Boolean = false,
  override val isCheckAllWarnings: Boolean = false,
  override val isIgnoreWarnings: Boolean = false,
  override val isWarningsAsErrors: Boolean = false,
  override val isIgnoreTestSources: Boolean = false,
  override val isIgnoreTestFixturesSources: Boolean = false,
  override val isCheckGeneratedSources: Boolean = false,
  override val isCheckReleaseBuilds: Boolean = true,
  override val isExplainIssues: Boolean = true,
  override val isShowAll: Boolean = false,
  override val textReport: Boolean = false,
  override val textOutput: FileImpl? = null,
  override val htmlReport: Boolean = true,
  override val htmlOutput: FileImpl? = null,
  override val xmlReport: Boolean = true,
  override val xmlOutput: FileImpl? = null,
  override val sarifReport: Boolean = false,
  override val sarifOutput: FileImpl? = null
) : Serializable, IdeLintOptions {
  constructor(
    baselineFile: File?,
    lintConfig: File?,
    severityOverrides: Map<String, Int>?,
    isCheckTestSources: Boolean,
    isCheckDependencies: Boolean,
    disable: Set<String>,
    enable: Set<String>,
    check: Set<String>?,
    isAbortOnError: Boolean,
    isAbsolutePaths: Boolean,
    isNoLines: Boolean,
    isQuiet: Boolean,
    isCheckAllWarnings: Boolean,
    isIgnoreWarnings: Boolean,
    isWarningsAsErrors: Boolean,
    isIgnoreTestSources: Boolean,
    isIgnoreTestFixturesSources: Boolean,
    isCheckGeneratedSources: Boolean,
    isCheckReleaseBuilds: Boolean,
    isExplainIssues: Boolean,
    isShowAll: Boolean,
    textReport: Boolean,
    textOutput: File?,
    htmlReport: Boolean,
    htmlOutput: File?,
    xmlReport: Boolean,
    xmlOutput: File?,
    sarifReport: Boolean,
    sarifOutput: File?,
  ) : this(
    baselineFile?.toImpl(),
    lintConfig?.toImpl(),
    severityOverrides,
    isCheckTestSources,
    isCheckDependencies,
    disable,
    enable,
    check,
    isAbortOnError,
    isAbsolutePaths,
    isNoLines,
    isQuiet,
    isCheckAllWarnings,
    isIgnoreWarnings,
    isWarningsAsErrors,
    isIgnoreTestSources,
    isIgnoreTestFixturesSources,
    isCheckGeneratedSources,
    isCheckReleaseBuilds,
    isExplainIssues,
    isShowAll,
    textReport,
    textOutput?.toImpl(),
    htmlReport,
    htmlOutput?.toImpl(),
    xmlReport,
    xmlOutput?.toImpl(),
    sarifReport,
    sarifOutput?.toImpl()
  )
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.stubs

import com.android.builder.model.LintOptions
import com.android.ide.common.gradle.model.UnusedModelMethodException
import java.io.File

data class LintOptionsStub(
  private val baselineFile: File? = null,
  private val checkTestSources: Boolean = true,
  private val severityOverrides: Map<String, Int>? = mapOf()
) : LintOptions {
  override fun getBaselineFile(): File? = baselineFile
  override fun isCheckTestSources(): Boolean = checkTestSources
  override fun getSeverityOverrides(): Map<String, Int>? = severityOverrides

  override fun isAbortOnError(): Boolean = throw UnusedModelMethodException("getAbortOnError")
  override fun isAbsolutePaths(): Boolean = throw UnusedModelMethodException("getAbsolutePaths")
  override fun isCheckAllWarnings(): Boolean = throw UnusedModelMethodException("getCheckAllWarnings")
  override fun isCheckDependencies(): Boolean = throw UnusedModelMethodException("getCheckDependencies")
  override fun isCheckGeneratedSources(): Boolean = throw UnusedModelMethodException("getCheckGeneratedSources")
  override fun isCheckReleaseBuilds(): Boolean = throw UnusedModelMethodException("getCheckReleaseBuilds")
  override fun isExplainIssues(): Boolean = throw UnusedModelMethodException("getExplainIssues")
  override fun isIgnoreTestSources(): Boolean = throw UnusedModelMethodException("isIgnoreTestSources")
  override fun isIgnoreWarnings(): Boolean = throw UnusedModelMethodException("getIgnoreWarnings")
  override fun isNoLines(): Boolean = throw UnusedModelMethodException("getNoLines")
  override fun isQuiet(): Boolean = throw UnusedModelMethodException("getQuiet")
  override fun isShowAll(): Boolean = throw UnusedModelMethodException("getShowAll")
  override fun isWarningsAsErrors(): Boolean = throw UnusedModelMethodException("getWarningsAsErrors")
  override fun getCheck(): Set<String>? = throw UnusedModelMethodException("getCheck")
  override fun getDisable(): Set<String> = throw UnusedModelMethodException("getDisable")
  override fun getEnable(): Set<String> = throw UnusedModelMethodException("getEnable")
  override fun getHtmlOutput(): File? = throw UnusedModelMethodException("getHtmlOutput")
  override fun getHtmlReport(): Boolean = throw UnusedModelMethodException("getHtmlReport")
  override fun getLintConfig(): File? = throw UnusedModelMethodException("getLintConfig")
  override fun getTextOutput(): File? = throw UnusedModelMethodException("getTextOutput")
  override fun getTextReport(): Boolean = throw UnusedModelMethodException("getTextReport")
  override fun getXmlOutput(): File? = throw UnusedModelMethodException("getXmlOutput")
  override fun getXmlReport(): Boolean = throw UnusedModelMethodException("getXmlReport")
}
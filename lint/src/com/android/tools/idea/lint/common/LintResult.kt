/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.Issue
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

sealed class LintResult {
  open fun getModule(): Module? = null

  /** Issues that were present and enabled in the inspection profile. */
  abstract val enabledIssues: Set<Issue>

  /**
   * Issues that were present and disabled in the inspection profile.
   *
   * Setting to null (rather than an empty set) allows IDE features that use Lint (such as
   * UnusedResourcesProcessor and WrongThreadInterproceduralAction) to ONLY enable the relevant
   * issues ([enabledIssues]), without also enabling discovered third party issues.
   *
   * If null, ONLY issues in [enabledIssues] will be enabled.
   *
   * If non-null, issues that are not in [enabledIssues] nor in [disabledIssues] may still be
   * reported; this is so that newly discovered third party issues (found during this lint run, but
   * that were not yet registered in the profile) will still report incidents in this run.
   */
  abstract val disabledIssues: Set<Issue>?
}

/**
 * Result object which doesn't record anything. Used in scenarios where you're depending on lint
 * infrastructure (such as [ApiLookup] and need to construct a client but you don't need to record
 * any potential warnings.
 */
class LintIgnoredResult : LintResult() {
  override val enabledIssues: Set<Issue> = emptySet()
  override val disabledIssues: Set<Issue> = emptySet()
}

data class LintBatchResult(
  val project: Project,
  val problemMap: Map<Issue, Map<File, List<LintProblemData>>>,
  val scope: AnalysisScope,
  override val enabledIssues: Set<Issue>,
  override val disabledIssues: Set<Issue>?,
) : LintResult()

class LintEditorResult(
  private val myModule: Module,
  val mainFile: VirtualFile,
  val mainFileContent: String,
  override val enabledIssues: Set<Issue>,
  override val disabledIssues: Set<Issue>?,
) : LintResult() {
  val problems: List<LintProblemData> = ArrayList()

  @Volatile
  var isDirty = false
    private set

  fun markDirty() {
    isDirty = true
  }

  override fun getModule(): Module {
    return myModule
  }
}

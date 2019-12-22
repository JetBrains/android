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
import com.intellij.openapi.project.Project
import java.io.File

data class LintBatchResult(val project: Project,
                           val problemMap: Map<Issue, Map<File, List<LintProblemData>>>,
                           val scope: AnalysisScope,
                           private val issues: Set<Issue>) : LintResult() {
  override fun getIssues(): Set<Issue> {
    return issues
  }
}
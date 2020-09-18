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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions

import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.TestHistoryConfiguration
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Customized import test group action which supports additional test format
 * such as UTP test results.
 */
class ImportTestGroup : com.intellij.execution.testframework.sm.runner.history.actions.ImportTestsGroup() {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return EMPTY_ARRAY
    return (getUtpTestHistoryActions(project) + getIntelliJStandardTestHistoryActions(project))
      .sortedByDescending { it.first }
      .map { it.second }
      .toList().toTypedArray()
  }

  private fun getIntelliJStandardTestHistoryActions(project: Project): Sequence<Pair<Long, AnAction>> {
    val testHistoryRoot = TestStateStorage.getTestHistoryRoot(project)
    return TestHistoryConfiguration.getInstance(project).files.asSequence()
      .map { fileName: String? -> File(testHistoryRoot, fileName) }
      .filter { file: File -> file.exists() }
      .map { Pair(it.lastModified(), ImportTestsFromHistoryAction(project, it)) }
  }

  private fun getUtpTestHistoryActions(project: Project): Sequence<Pair<Long, AnAction>> {
    val action = createImportUtpResultActionFromAndroidGradlePluginOutput(project) ?: return sequenceOf()
    return sequenceOf(Pair(action.timestamp, action.action))
  }
}
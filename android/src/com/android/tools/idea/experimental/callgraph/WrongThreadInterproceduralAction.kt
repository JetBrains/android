/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.experimental.callgraph

import com.android.tools.idea.lint.LintIdeClient
import com.android.tools.idea.lint.LintIdeRequest
import com.android.tools.lint.checks.WrongThreadInterproceduralDetector
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintDriver
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.util.*
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance(WrongThreadInterproceduralAction::class.java)

/** Uses a call graph to more precisely check for thread annotation violations. */
class WrongThreadInterproceduralAction : BaseAnalysisAction(ACTION_NAME, ACTION_NAME) {

  companion object {
    private const val ACTION_NAME = "Wrong Thread (Interprocedural) Action"
  }

  override fun analyze(project: Project, scope: AnalysisScope) {
    val time = measureTimeMillis {
      val client = LintIdeClient.forBatch(project, mutableMapOf(), scope, setOf(WrongThreadInterproceduralDetector.ISSUE))
      try {
        val files = ArrayList<VirtualFile>()
        scope.accept { files.add(it) }
        val modules = ModuleManager.getInstance(project).modules.toList()
        val request = LintIdeRequest(client, project, files, modules, /*incremental*/ false)
        request.setScope(WrongThreadInterproceduralDetector.SCOPE)
        val issue = object : IssueRegistry() {
          override fun getIssues() = listOf(WrongThreadInterproceduralDetector.ISSUE)
        }
        LintDriver(issue, client, request).analyze()
      }
      finally {
        Disposer.dispose(client)
      }
    }
    LOG.info("Interprocedural thread check: ${time}ms")
  }
}
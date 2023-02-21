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
package com.android.tools.idea.lint.actions

import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintIdeRequest
import com.android.tools.idea.lint.common.LintIdeSupport
import com.android.tools.lint.checks.WrongThreadInterproceduralDetector
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.util.ArrayList
import java.util.EnumSet
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance(WrongThreadInterproceduralAction::class.java)

/**
 * An internal action for running the interprocedural thread annotation Lint check. Useful for
 * timing and debugging.
 */
class WrongThreadInterproceduralAction : BaseAnalysisAction(ACTION_NAME, ACTION_NAME) {

  companion object {
    private const val ACTION_NAME = "Wrong Thread (Interprocedural) Action"
  }

  override fun analyze(project: Project, scope: AnalysisScope) {
    ProgressManager.getInstance()
      .run(
        object :
          Task.Backgroundable(
            project,
            "Finding interprocedural thread annotation violations",
            true
          ) {

          override fun run(indicator: ProgressIndicator) {
            val time = measureTimeMillis {
              // The Lint check won't run unless explicitly enabled by default..
              val wasEnabledByDefault =
                WrongThreadInterproceduralDetector.ISSUE.isEnabledByDefault()
              val detectorIssue = WrongThreadInterproceduralDetector.ISSUE.setEnabledByDefault(true)
              val client =
                LintIdeSupport.get()
                  .createBatchClient(
                    LintBatchResult(project, mutableMapOf(), scope, setOf(detectorIssue))
                  )
              try {
                val files = ArrayList<VirtualFile>()
                scope.accept { files.add(it) }
                val modules = ModuleManager.getInstance(project).modules.toList()
                val request = LintIdeRequest(client, project, files, modules, /*incremental*/ false)
                request.setScope(EnumSet.of(Scope.ALL_JAVA_FILES))
                val issue =
                  object : IssueRegistry() {
                    override val vendor: Vendor = AOSP_VENDOR
                    override val issues: List<Issue>
                      get() = listOf(WrongThreadInterproceduralDetector.ISSUE)
                  }
                client.createDriver(request, issue).analyze()
              } finally {
                Disposer.dispose(client)
                WrongThreadInterproceduralDetector.ISSUE.setEnabledByDefault(wasEnabledByDefault)
              }
            }
            LOG.info("Interprocedural thread check: ${time}ms")
          }
        }
      )
  }
}

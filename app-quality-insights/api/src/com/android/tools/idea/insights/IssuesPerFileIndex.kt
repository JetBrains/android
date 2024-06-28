/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.analysis.Cause
import com.android.tools.idea.insights.analysis.CrashFrame
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.SetMultimap
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

/**
 * A project level service used to update, store, and retrieve crash frames on a per-file basis.
 * It's kept up to date by the [AppInsightsProjectLevelController] and its entries are used by
 * external annotator.
 */
@Service(Service.Level.PROJECT)
class IssuesPerFileIndex(private val project: Project) {

  private val mutableIssuesPerFilename: AtomicReference<SetMultimap<String, IssueInFrame>> =
    AtomicReference(ImmutableSetMultimap.of())

  /**
   * A view of [AppInsightsIssue]s grouped by the filename they are associated to.
   *
   * Issues are wrapped in [IssueInFrame] objects that provide context of the stacktrace frame where
   * they occur. These objects are group by and map to their corresponding files by filename.
   */
  val issuesPerFilename: SetMultimap<String, IssueInFrame>
    get() = mutableIssuesPerFilename.get()

  private fun computeIssuesPerFilename(
    issues: LoadingState<Selection<AppInsightsIssue>>
  ): SetMultimap<String, IssueInFrame> =
    when (issues) {
      is LoadingState.Ready -> {
        val fileCache = HashMultimap.create<String, IssueInFrame>()
        for (issue in issues.value.items) {
          for (exception in issue.sampleEvent.stacktraceGroup.exceptions) {
            var previousFrame: Frame? = null
            for (frame in exception.stacktrace.frames) {
              if (frame.file.isNotEmpty()) {
                fileCache.put(
                  frame.file,
                  IssueInFrame(
                    CrashFrame(
                      frame,
                      if (previousFrame == null) Cause.Throwable(exception.type)
                      else Cause.Frame(previousFrame),
                    ),
                    issue,
                  ),
                )
              }
              previousFrame = frame
            }
          }
        }
        fileCache
      }
      else -> ImmutableSetMultimap.of()
    }

  fun updateIssueIndex(issues: LoadingState<Selection<AppInsightsIssue>>) {
    val newIndex = computeIssuesPerFilename(issues)
    if (mutableIssuesPerFilename.getAndSet(newIndex) != newIndex) {
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }
}

data class IssueInFrame(val crashFrame: CrashFrame, val issue: AppInsightsIssue)

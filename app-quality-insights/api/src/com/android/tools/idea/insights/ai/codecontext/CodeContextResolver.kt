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
package com.android.tools.idea.insights.ai.codecontext

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.experiments.AppInsightsExperimentFetcher
import com.android.tools.idea.insights.experiments.Experiment
import com.android.tools.idea.insights.experiments.ExperimentGroup
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.execution.filters.ExceptionWorker.parseExceptionLine
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.readText
import com.intellij.psi.search.ProjectScope

data class CodeContextTrackingInfo(val fileCount: Int, val lineCount: Int, val charCount: Int) {
  companion object {
    val EMPTY = CodeContextTrackingInfo(0, 0, 0)
  }

  fun toCodeContextDetailsProto():
    AppQualityInsightsUsageEvent.InsightFetchDetails.CodeContextDetails.Builder =
    AppQualityInsightsUsageEvent.InsightFetchDetails.CodeContextDetails.newBuilder().apply {
      fileCount = this@CodeContextTrackingInfo.fileCount.toLong()
      lineCount = this@CodeContextTrackingInfo.lineCount.toLong()
      charCount = this@CodeContextTrackingInfo.charCount.toLong()
    }
}

data class CodeContextData(
  val codeContext: List<CodeContext>,
  val experimentType: Experiment,
  val codeContextTrackingInfo: CodeContextTrackingInfo = CodeContextTrackingInfo.EMPTY,
) {
  companion object {
    /**
     * The default experiment state for users who disable context sharing settings or for whatever
     * reason not assigned to an experiment
     */
    val UNASSIGNED = CodeContextData(emptyList(), Experiment.UNKNOWN)
    val CONTROL = CodeContextData(emptyList(), Experiment.CONTROL)
  }
}

/** A simple value class for containing info related to a piece of code context. */
data class CodeContext(
  val className: String,
  // The fully qualified path of the source file.
  val filePath: String,
  val content: String,
  val language: Language,
)

/** Pulls source code from the editor based on the provided stack trace. */
interface CodeContextResolver {
  /**
   * Gets the source files for the given [stack].
   *
   * @param stack [StacktraceGroup] for which the files are needed.
   * @param overrideSourceLimit override source limits for [Experiment.CONTROL]
   */
  suspend fun getSource(
    stack: StacktraceGroup,
    overrideSourceLimit: Boolean = false,
  ): CodeContextData
}

class CodeContextResolverImpl(private val project: Project) : CodeContextResolver {

  private val experimentFetcher: AppInsightsExperimentFetcher
    get() = AppInsightsExperimentFetcher.instance

  override suspend fun getSource(
    stack: StacktraceGroup,
    overrideSourceLimit: Boolean,
  ): CodeContextData {
    val flagValue =
      Experiment.entries[
          StudioFlags.CODE_CONTEXT_EXPERIMENT_OVERRIDE.get().takeIf { i ->
            i in 0 until Experiment.entries.size
          } ?: 0]
    val experiment =
      if (flagValue != Experiment.UNKNOWN) {
        flagValue
      } else {
        experimentFetcher.getCurrentExperiment(ExperimentGroup.CODE_CONTEXT)
      }
    val fileLimit =
      when (experiment) {
        Experiment.TOP_SOURCE -> 1
        Experiment.TOP_THREE_SOURCES -> 3
        Experiment.ALL_SOURCES -> Integer.MAX_VALUE
        Experiment.CONTROL ->
          if (overrideSourceLimit) {
            1
          } else {
            return CodeContextData.CONTROL
          }
        Experiment.UNKNOWN -> return CodeContextData.UNASSIGNED
      }
    val sources = getSource(stack, fileLimit)
    return CodeContextData(sources, experiment, getMetadata(sources))
  }

  private fun getMetadata(contexts: List<CodeContext>): CodeContextTrackingInfo =
    contexts.fold(CodeContextTrackingInfo.EMPTY) { acc, context ->
      CodeContextTrackingInfo(
        acc.fileCount + 1,
        acc.lineCount + context.content.lines().size,
        acc.charCount + context.content.count(),
      )
    }

  private suspend fun getSource(stack: StacktraceGroup, fileLimit: Int): List<CodeContext> {
    val index = ProjectFileIndex.getInstance(project)
    val exceptionInfoCache = ExceptionInfoCache(project, ProjectScope.getContentScope(project))
    return stack.exceptions
      .flatMap { it.stacktrace.frames }
      .mapNotNull { frame ->
        val parsedException = parseExceptionLine(frame.rawSymbol) ?: return@mapNotNull null
        val className =
          frame.rawSymbol.substring(
            parsedException.classFqnRange.startOffset,
            parsedException.classFqnRange.endOffset,
          )
        readAction {
          val resolve = exceptionInfoCache.resolveClassOrFile(className, frame.file)
          if (resolve.isInLibrary || resolve.classes.isEmpty()) return@readAction null
          val file =
            resolve.classes.keys.firstOrNull {
              index.isInSource(it) || index.isInGeneratedSources(it)
            } ?: return@readAction null
          if (GeminiPluginApi.getInstance().isFileExcluded(project, file)) return@readAction null
          val language =
            file.extension?.let { Language.fromExtension(it) } ?: return@readAction null
          CodeContext(className, file.path, file.readText(), language)
        }
      }
      .distinctBy { it.filePath }
      .take(fileLimit)
  }
}

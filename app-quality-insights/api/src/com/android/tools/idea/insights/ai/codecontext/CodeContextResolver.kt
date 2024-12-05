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

import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.ai.codecontext.ContextSharingState.Companion.getContextSharingState
import com.android.utils.associateWithNotNull
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.execution.filters.ExceptionWorker.parseExceptionLine
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import java.nio.file.Paths
import kotlin.io.path.name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

enum class ContextSharingState {
  DISABLED,
  ALLOWED;

  companion object {
    fun getContextSharingState(project: Project) =
      if (
        GeminiPluginApi.getInstance().isAvailable() &&
          GeminiPluginApi.getInstance().isContextAllowed(project)
      )
        ALLOWED
      else DISABLED
  }
}

data class CodeContextData(
  val codeContext: List<CodeContext>,
  val codeContextTrackingInfo: CodeContextTrackingInfo = CodeContextTrackingInfo.EMPTY,
  val contextSharingState: ContextSharingState = ContextSharingState.DISABLED,
) {
  companion object {
    /** The default experiment state for users who disable context sharing settings. */
    val DISABLED = CodeContextData(emptyList(), contextSharingState = ContextSharingState.DISABLED)

    fun empty(project: Project) =
      CodeContextData(emptyList(), contextSharingState = getContextSharingState(project))
  }

  fun isEmpty() = codeContext.isEmpty()
}

/** A simple value class for containing info related to a piece of code context. */
data class CodeContext(
  // The fully qualified path of the source file.
  val filePath: String,
  val content: String,
)

/** Pulls source code from the editor based on the provided stack trace. */
interface CodeContextResolver {
  /**
   * Gets the source files for the given [stack].
   *
   * @param conn [Connection] selected connection.
   * @param stack [StacktraceGroup] for which the files are needed.
   */
  suspend fun getSource(conn: Connection, stack: StacktraceGroup): CodeContextData

  /**
   * Similar to the above function but works off of the response given by Gemini, which is a list of
   * names.
   */
  suspend fun getSource(fileNames: List<String>): CodeContextData

  /**
   * Gets a list of [VirtualFile]s that matches the [filePath].
   *
   * Note the [filePath] could be fully qualified path or could just be a file name.
   */
  suspend fun getSourceVirtualFiles(filePath: String): List<VirtualFile>
}

class CodeContextResolverImpl(private val project: Project) : CodeContextResolver {

  override suspend fun getSource(conn: Connection, stack: StacktraceGroup): CodeContextData {
    if (!conn.isMatchingProject()) {
      return CodeContextData.empty(project)
    }
    val sources = getSource(stack)
    return CodeContextData(sources, getMetadata(sources), getContextSharingState(project))
  }

  override suspend fun getSource(fileNames: List<String>): CodeContextData {
    val context =
      fileNames
        .associateWithNotNull { fileName -> getSourceVirtualFiles(fileName).firstOrNull() }
        .map { (file, virtFile) -> CodeContext(file, virtFile.readText()) }
    return CodeContextData(context, getMetadata(context), getContextSharingState(project))
  }

  override suspend fun getSourceVirtualFiles(filePath: String): List<VirtualFile> =
    withContext(Dispatchers.IO) {
      val scope = GlobalSearchScope.projectScope(project)
      val name = Paths.get(filePath).name
      val candidates = readAction { FilenameIndex.getVirtualFilesByName(name, scope) }
      candidates
        .filter {
          it.path.endsWith(filePath) && !GeminiPluginApi.getInstance().isFileExcluded(project, it)
        }
        .also { files ->
          Logger.getInstance(CodeContextResolverImpl::class.java)
            .debug("Found virtual files ${files.map { it.path }}")
        }
    }

  private fun getMetadata(contexts: List<CodeContext>): CodeContextTrackingInfo =
    contexts.fold(CodeContextTrackingInfo.EMPTY) { acc, context ->
      CodeContextTrackingInfo(
        acc.fileCount + 1,
        acc.lineCount + context.content.lines().size,
        acc.charCount + context.content.count(),
      )
    }

  private suspend fun getSource(stack: StacktraceGroup): List<CodeContext> {
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
          CodeContext(file.path, file.readText())
        }
      }
      .distinctBy { it.filePath }
  }
}

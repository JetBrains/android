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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.google.gson.Gson
import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture


class DataBindingIssueChecker : GradleIssueChecker {
  private val SEPARATOR = "=".repeat(50)

  /**
   * JSON parser, shared across parsing calls to take advantage of the caching it does.
   */
  private val gson = Gson()

  /**
   * Example structure of the exception chain returned from Gradle.
   *
   * BuildException "Could not execute build..."
   *   cause - LocationAwareException "Execution failed for task ..."
   *     cause - ContextualPlaceholderException "Execution failed for task ..."
   *       cause - WorkExecutionException "A failure occurred while executing org.jetbrains.kotlin.gradle.internal.KaptExecution"
   *         cause - InvocationTargetException null
   *           cause - null
   *           target - KaptBaseError "Exception while annotation processing"
   *             cause - LoggedErrorException "Found data binding error(s):\n\n[databinding] <JSON>"
   */
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    // getRootCauseAndLocation only takes us down to WorkExecutionException as the InvocationTargetException has a null message.
    if (rootCause.cause !is InvocationTargetException) return null
    val baseError = (rootCause.cause as? InvocationTargetException)?.targetException
    if (baseError?.cause == null) return null

    val possibleLoggedErrorException = baseError.cause
    val errors = possibleLoggedErrorException?.message?.lineSequence()?.filter { line ->
      line.startsWith(ERROR_LOG_PREFIX)
    }?.toList() ?: return null

    val buildIssues = errors.mapIndexedNotNull{ index, errorJson ->
      convertToBuildIssue(index, errorJson.removePrefix(ERROR_LOG_PREFIX), issueData.projectPath)
    }

    if (buildIssues.isEmpty()) return null

    if (buildIssues.size == 1) {
      return buildIssues.first()
    }
    else {
      val title = "Found ${buildIssues.size} data binding error(s)"
      return object : BuildIssue {
        override val title: String = title
        override val description: String = "$title\n$SEPARATOR\n" + buildIssues.joinToString ("\n$SEPARATOR\n") {
          issue -> issue.description
        }
        override val quickFixes: List<BuildIssueQuickFix> = buildIssues.flatMap { issue -> issue.quickFixes }
        override fun getNavigatable(project: Project): Navigatable? = null
      }
    }
  }

  private fun convertToBuildIssue(index: Int, errorJson: String, projectPath: String): BuildIssue? {
    try {
      val msg = gson.fromJson(errorJson, EncodedMessage::class.java)
      val summary = msg.message.substringBefore('\n')
      if (msg.locations.isEmpty()) {
        return object : BuildIssue {
          override val title: String = summary
          override val description: String = msg.message
          override val quickFixes: List<BuildIssueQuickFix> = listOf()
          override fun getNavigatable(project: Project): Navigatable? = null
        }
      }
      else {
        // Note: msg.filePath is relative to the project, but the build output window can't seem to find the
        // file unless we feed it the absolute path directly.
        val sourceFile = File(projectPath, msg.filePath).absoluteFile
        val location = msg.locations.first()
        val filePosition = FilePosition(sourceFile, location.startLine, location.startCol, location.endLine, location.endCol)
        val goToFile = OpenFileWithLocationQuickFix("open.file.$index", filePosition)
        return object : BuildIssue {
          override val title: String = summary
          override val description: String = msg.message + "\n<a href=\"${goToFile.id}\">Open File</a>"
          override val quickFixes: List<BuildIssueQuickFix> = listOf(goToFile)
          override fun getNavigatable(project: Project): Navigatable? {
            val virtualFile = VfsUtil.findFileByIoFile(sourceFile, false) ?: return null
            return OpenFileDescriptor(project, virtualFile, location.startLine, location.startCol)
          }
        }
      }
    }
    catch (ignored: Exception) {
      return null
    }
  }
}

/**
 * This is an adaptation of [OpenFileAtLocationQuickFix] which allows a customisable ID to allow multiple links
 * in a single message.
 */
class OpenFileWithLocationQuickFix(uniqueId: String, val myFilePosition: FilePosition) : BuildIssueQuickFix {
  override val id = uniqueId

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val projectFile = project.projectFile ?: return CompletableFuture.completedFuture<Any>(null)
    val future = CompletableFuture<Any>()
    invokeLater {
      val file = projectFile.parent.fileSystem.findFileByPath(myFilePosition.file.path)
      if (file != null) {
        val openFile = OpenFileDescriptor(project, file, myFilePosition.startLine, myFilePosition.startColumn, false)
        if (openFile.canNavigate()) {
          openFile.navigate(true)
        }
      }
      future.complete(null)
    }
    return future
  }
}
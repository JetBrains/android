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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelModuleQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getParentModulePath
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.function.Consumer
import java.util.regex.Pattern

private const val INVOKE_CUSTOM = "Invoke-customs are only supported starting with Android O"
private const val DEFAULT_INTERFACE_METHOD = "Default interface methods are only supported starting with Android N (--min-api 24)"
private const val STATIC_INTERFACE_METHOD = "Static interface methods are only supported starting with Android N (--min-api 24)"
private val FAILED_TASK_PATTERN = Pattern.compile("Execution failed for task '(.+)'.")
private val EXCEPTION_TRACE_PATTERN = Pattern.compile("Caused by: java.lang.RuntimeException(.*)")

class DexDisabledIssueChecker : GradleIssueChecker {
  /**
   * Looks for errors related to DexArchiveBuilderException caused when desugaring is not enabled. The expected errors have a root cause
   * with a message like these:
   *
   * - Error: Invoke-customs are only supported starting with Android O (--min-api 26)
   * - Error: Default interface methods are only supported starting with Android N (--min-api 24): <method>
   * - Error: Static interface methods are only supported starting with Android N (--min-api 24): <method>
   *
   * And it should be wrapped by a DexArchiveBuilderException containing a message with recommendations on how to fix it.
   */
  override fun check(issueData: GradleIssueData): BuildIssue? {
    // Confirm rootCause is one of the expected causes.
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first ?: return null
    if (rootCause !is RuntimeException) {
      return null
    }
    var rootMessage = rootCause.message ?: return null
    rootMessage = rootMessage.removePrefix("Error: ")
    if ((!rootMessage.startsWith(INVOKE_CUSTOM))
        && (!rootMessage.startsWith(DEFAULT_INTERFACE_METHOD))
        && (!rootMessage.startsWith(STATIC_INTERFACE_METHOD))) {
      return null
    }

    // Confirm that there is a DexArchiveBuilderException
    val builderException = extractDexArchiveBuilderException(issueData.error) ?: return null
    val issueComposer = BuildIssueComposer(rootMessage, issueTitle = "Desugaring disabled")
    val buildMessage = builderException.message
    if (buildMessage != null) {
      issueComposer.addDescription(buildMessage)
    }
    val modulePath = extractModulePathFromError(issueData.error)
    if (modulePath != null) {
      issueComposer.addQuickFix(SetJavaLanguageLevelModuleQuickFix(modulePath, LanguageLevel.JDK_1_8, setJvmTarget = false))
    }
    issueComposer.addQuickFix(SetJavaLanguageLevelAllQuickFix(LanguageLevel.JDK_1_8, setJvmTarget = false))
    return DexDisabledIssue(issueComposer.composeBuildIssue())
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return stacktrace != null && EXCEPTION_TRACE_PATTERN.matcher(stacktrace).find() &&
           (failureCause.startsWith("Error: $INVOKE_CUSTOM") || failureCause.startsWith("Error: $DEFAULT_INTERFACE_METHOD") ||
            failureCause.startsWith("Error: $STATIC_INTERFACE_METHOD"))
  }
}

class DexDisabledIssue(private val buildIssue: BuildIssue) : BuildIssue {
  override val title = buildIssue.title
  override val description = buildIssue.description
  override val quickFixes = buildIssue.quickFixes
  override fun getNavigatable(project: Project) = buildIssue.getNavigatable(project)
}

private fun extractDexArchiveBuilderException(error: Throwable): Throwable? {
  var cause: Throwable? = error
  while (cause != null) {
    if (cause.javaClass.name.endsWith(".DexArchiveBuilderException")) {
      return cause
    }
    if (cause.cause == cause) {
      break
    }
    cause = cause.cause
  }
  return null
}

private fun extractModulePathFromError(error: Throwable): String? {
  var cause: Throwable? = error
  while (cause != null) {
    val message = cause.message
    if (message != null) {
      val matcher = FAILED_TASK_PATTERN.matcher(message)
      if (matcher.matches()) {
        return getParentModulePath(matcher.group(1)!!)
      }
    }
    if (cause.cause == cause) {
      break
    }
    cause = cause.cause
  }
  return null
}

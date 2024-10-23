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
package com.android.tools.idea.gradle.project.build.events.studiobot

import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.idea.gemini.buildLlmPrompt
import com.intellij.openapi.project.Project

/** Represents a Gradle error context.
 *  The following details are stored in the context:
 *  @param gradleTask The Gradle command that was executed.
 *  @param errorMessage The error message.
 *  @param fullErrorDetails The full error details/stack trace to include.
 *  @param source Whether it is a Build / Sync error
 */
data class GradleErrorContext(
  private val gradleTask: String?,
  private val errorMessage: String?,
  private val fullErrorDetails: String?,
  val source: Source?) {
  enum class Source(private val source: String) {
    BUILD("build"),
    SYNC("sync");
    override fun toString(): String = source
  }

  /**
   * Converts context to a [LlmPrompt] object.
   * @param project The project associated with the query.
   * @return The [LlmPrompt] with the context information.
   *
   * NOTE: The method needs to ensure that the files
   * used as context are allowed by .aiexclude before
   * including it in the prompt.
   */
   fun toPrompt(project: Project): LlmPrompt {
    return buildLlmPrompt (project) {
      userMessage {
      text(toQuery(), filesUsed = emptyList())
    }}
  }

  /**
   * Converts this query context to a plain text query string.
   * @return The query string representation of this query context.
   */
   fun toQuery(): String {
    return  buildString {
      append(
        if (source != null) {
          "I'm getting the following error while ${source}ing my project."
        } else {
          "I'm getting the following error in my project."
        }
      )
      errorMessage?.let {
          append(" The error is: ${it.take(MAX_CHAR_LIMIT_ON_ERROR_MESSAGE)}")
        }
      appendLine()
      appendLine("```")
      gradleTask?.let {
        appendLine("""
          ${'$'} ./gradlew $it
        """.trimIndent())
      }
      fullErrorDetails?.let { stackTrace ->
        stackTrace.lines().take(MAX_STACK_TRACE_LINES_IN_CONTEXT).forEach { appendLine(it) }
        // Find the root cause index.
        val rootCauseIndex = stackTrace.lastIndexOf("Caused by:")
        // Include 5 lines of root cause.
        if(rootCauseIndex != -1) {
          appendLine("...")
          stackTrace.substring(rootCauseIndex).lines().take(MAX_ROOT_CAUSE_LINES_IN_CONTEXT).forEach { appendLine(it) }
        }
      }
      appendLine("```")
      append("How do I fix this?")
    }
  }

  companion object {
    // No.of stack trace lines to include in context is chosen to 10 arbitrarily.
    // To be changed to a more appropriate value after analysing metrics.
    private const val MAX_STACK_TRACE_LINES_IN_CONTEXT = 10;
    // No.of root cause lines to include in context is chosen to 5 arbitrarily.
    private const val MAX_ROOT_CAUSE_LINES_IN_CONTEXT = 5;
    // No.of characters to allow in th error message. It is typically of a single line
    // and 500 characters should be sufficient.
    private const val MAX_CHAR_LIMIT_ON_ERROR_MESSAGE = 500;
  }
}
/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android

import com.android.tools.idea.util.fsm.StateMachine.Companion.stateMachine
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.AndroidJavaDocExternalFilter.Companion.State.ABORTED
import org.jetbrains.android.AndroidJavaDocExternalFilter.Companion.State.CONSUMING_CLASS_DATA
import org.jetbrains.android.AndroidJavaDocExternalFilter.Companion.State.CONSUMING_DESCRIPTION
import org.jetbrains.android.AndroidJavaDocExternalFilter.Companion.State.MOVING_TO_CLASS_DATA
import org.jetbrains.android.AndroidJavaDocExternalFilter.Companion.State.MOVING_TO_CODE
import org.jetbrains.android.AndroidJavaDocExternalFilter.Companion.State.MOVING_TO_DESCRIPTION
import org.jetbrains.android.AndroidJavaDocExternalFilter.Companion.State.SUCCESS
import org.jetbrains.annotations.NonNls
import java.io.BufferedReader
import java.io.Reader

internal class AndroidJavaDocExternalFilter(project: Project?) : JavaDocExternalFilter(project) {
  public override fun doBuildFromStream(url: String, input: Reader, data: StringBuilder) {
    try {
      // Looking up a method, field or constructor? If so we can use the builtin support.
      if (ourAnchorSuffix.matcher(url).find()) super.doBuildFromStream(url, input, data)
      else BufferedReader(input).filterTo(data)
    } catch (e: Exception) {
      LOG.error(e.message, e, "URL: $url")
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#org.jetbrains.android.AndroidDocumentationProvider")
    // Pull out the javadoc section.
    // The format has changed over time, so we need to look for different formats.
    // The document begins with a bunch of stuff we don't want to include (e.g.
    // page navigation etc); in all formats this seems to end with the following marker:
    @NonNls private const val START_SECTION = "<!-- ======== START OF CLASS DATA ======== -->"
    // This doesn't appear anywhere in recent documentation,
    // but presumably was needed at one point; left for now
    // for users who have old documentation installed locally.
    @NonNls private const val SKIP_HEADER = "<!-- END HEADER -->"

    private fun String.isClassDescriptionStart() =
      startsWith("<h2>Class Overview") || equals("<br><hr>")

    /**
     * Returns true if we've clearly reached the section after the class description. Newer docs
     * have no marker section or class attribute marking the beginning of the class doc.
     */
    private fun String.isClearlyBeyondClassDescription() = startsWith("<h2 class=\"api-section\"")

    private enum class State {
      MOVING_TO_CLASS_DATA,
      MOVING_TO_CODE,
      CONSUMING_CLASS_DATA,
      MOVING_TO_DESCRIPTION,
      CONSUMING_DESCRIPTION,
      SUCCESS,
      ABORTED;

      fun toTerminalState() =
        when (this) {
          MOVING_TO_CLASS_DATA,
          MOVING_TO_CODE -> ABORTED
          else -> SUCCESS
        }
    }

    private fun createStateMachine(sb: StringBuilder) =
      stateMachine(initialState = MOVING_TO_CLASS_DATA) {
        MOVING_TO_CLASS_DATA.transitionsTo(MOVING_TO_CODE, ABORTED)
        MOVING_TO_CODE.transitionsTo(CONSUMING_CLASS_DATA, ABORTED)
        CONSUMING_CLASS_DATA {
          onEnter { sb.append(HTML).append("\n").append("<code>\n") }
          onExit { sb.append("</code>\n") }
          transitionsTo(MOVING_TO_DESCRIPTION, CONSUMING_DESCRIPTION, SUCCESS)
        }
        MOVING_TO_DESCRIPTION.transitionsTo(CONSUMING_DESCRIPTION, SUCCESS)
        CONSUMING_DESCRIPTION {
          onEnter { sb.append("<hr><div>\n") }
          onExit { sb.append("</div>\n") }
          transitionsTo(SUCCESS)
        }
        SUCCESS.onEnter { sb.append(HTML_CLOSE) }
        ABORTED.onEnter { sb.clear() }
      }

    private fun BufferedReader.filterTo(sb: StringBuilder) {
      use { buf ->
        var state by createStateMachine(sb)::state
        while (state != ABORTED && state != SUCCESS) {
          val line: String? = buf.readLine()?.trimEnd()
          if (line == null) {
            state = state.toTerminalState()
            continue
          }
          if (line.isBlank()) continue // Blank lines don't change anything
          when (state) {
            SUCCESS,
            ABORTED -> throw RuntimeException("Unreachable") // Compiler not smart enough.
            MOVING_TO_CLASS_DATA -> if (line.contains(START_SECTION)) state = MOVING_TO_CODE
            MOVING_TO_CODE -> if (line.trimStart().startsWith("<code")) state = CONSUMING_CLASS_DATA
            CONSUMING_CLASS_DATA ->
              when {
                line.isClassDescriptionStart() -> state = CONSUMING_DESCRIPTION
                line.contains("<table class=") -> state = MOVING_TO_DESCRIPTION
                line.isClearlyBeyondClassDescription() -> state = SUCCESS
                else -> {
                  sb.append(line).append("\n")
                  if (line.contains(SKIP_HEADER)) state = MOVING_TO_DESCRIPTION
                }
              }
            MOVING_TO_DESCRIPTION ->
              when {
                line.isClearlyBeyondClassDescription() -> state = SUCCESS
                line.isClassDescriptionStart() -> state = CONSUMING_DESCRIPTION
              }
            CONSUMING_DESCRIPTION -> {
              if (line.startsWith("<h2>") || line.startsWith("<h2 ")) state = SUCCESS
              else sb.append(line).append("\n")
            }
          }
        }
      }
    }
  }
}

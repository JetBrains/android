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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.Blames
import com.android.tools.idea.insights.Caption
import com.android.tools.idea.insights.ExceptionStack
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.Stacktrace
import com.android.tools.idea.insights.StacktraceGroup
import com.intellij.execution.filters.ExceptionWorker.parseExceptionLine
import com.intellij.openapi.diagnostic.Logger
import java.util.ArrayDeque

private val LOG = Logger.getInstance("datamodel.Stacktrace")

private const val AT_PREFIX = "at "
private const val STANDALONE_AT = " at "
private const val STANDALONE_PC = " pc "

private fun String.mightBeFrame(): Boolean {
  if (startsWith(AT_PREFIX) || contains(STANDALONE_AT) || contains(STANDALONE_PC)) return true

  return (parseExceptionLine(this) != null)
}

sealed class Line {
  /** Represents a line of trace from the generated dumps. */
  class FrameContent(val value: String) : Line() {
    fun toFrame(): Frame {
      val parsed = parseExceptionLine(value)

      return if (parsed != null) {
        Frame(
          line = parsed.lineNumber.toLong(),
          file = parsed.fileName ?: "",
          rawSymbol = value.trim(),
          symbol =
            value
              .substring(parsed.classFqnRange.startOffset, parsed.methodNameRange.endOffset)
              .trim(),
          blame = Blames.UNKNOWN_BLAMED,
        )
      } else {
        LOG.debug(
          "$value is not in a recognized format and can't be parsed, " +
            "but we still build a frame for it, then at least we can display it in our stacktrace panel later."
        )
        Frame(
          line = 0L,
          file = "",
          rawSymbol = value.trim(),
          symbol = value.trim(),
          blame = Blames.UNKNOWN_BLAMED,
        )
      }
    }
  }

  /** Represents other pieces of info from the generated dumps. */
  class TextContent(val value: String) : Line() {
    fun toCaption(): Caption {
      return Caption(
        title = value.substringBefore(":").trim(),
        subtitle = value.substringAfter(":", missingDelimiterValue = "").trim().trimEnd(':'),
      )
    }
  }
}

/**
 * Returns a list of structured [ExceptionStack] by parsing the given string.
 *
 * Here's our best efforts to identify trace groups and extract file name, line number and symbol
 * from any given trace lines. If it's a native frame, we will still generate a [Frame] but only the
 * symbol field is meaningful.
 *
 * This is more of a temporary solution to have a working insights support for Play Vitals.
 */
private fun String.extract(): List<ExceptionStack> {
  if (isBlank()) return emptyList()

  val exceptionStacks = mutableListOf<ExceptionStack>()
  val lines = split("\n").filterNot { it.isBlank() }
  val stack = ArrayDeque<Line>()

  fun popAndExtract() {
    // 1. collect frames (reversed)
    val frames = mutableListOf<Frame>()
    while (stack.isNotEmpty() && stack.peek() is Line.FrameContent) {
      val popped = stack.pop()
      frames.add((popped as Line.FrameContent).toFrame())
    }

    // 2. collect caption (non-frame text) and exception message
    val rawTextContent = (if (stack.isNotEmpty()) (stack.pop() as? Line.TextContent) else null)
    val caption = rawTextContent?.toCaption() ?: Caption()

    // 3. build "exception stack"
    val exceptionStack =
      ExceptionStack(
        stacktrace =
          Stacktrace(caption = caption, blames = Blames.UNKNOWN_BLAMED, frames = frames.reversed()),
        type = caption.title,
        exceptionMessage = caption.subtitle,
        rawExceptionMessage = rawTextContent?.value ?: "",
      )

    exceptionStacks.add(exceptionStack)
  }

  lines.onEach { line ->
    if (line.mightBeFrame()) {
      stack.push(Line.FrameContent(line))
    } else if (stack.isEmpty()) {
      stack.push(Line.TextContent(line))
    } else {
      popAndExtract()
      stack.push(Line.TextContent(line))
    }
  }

  popAndExtract()
  check(stack.isEmpty())

  return exceptionStacks.toList()
}

internal fun String.extractException(): StacktraceGroup = StacktraceGroup(extract())

internal fun String.extractThreadDump(): StacktraceGroup {
  val threads = split("\n\n")

  val stacks =
    threads
      .flatMap { thread -> thread.extract() }
      .mapIndexed { index, exceptionStack ->
        exceptionStack.copy(
          stacktrace =
            exceptionStack.stacktrace.copy(
              blames =
                if (index == 0) {
                  Blames.BLAMED
                } else {
                  Blames.NOT_BLAMED
                }
            )
        )
      }
  return StacktraceGroup(stacks)
}

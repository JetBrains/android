/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.logcat.folding

/** Regex to match a stack trace line. E.g.: "at com.foo.Class.method(FileName.extension:10)" */
private val EXCEPTION_LINE_PATTERN = Regex("^\\s*at .+\\(.+\\)$")

/** Regex to match the excluded frames line i.e. line of form "... N more" */
private val ELIDED_LINE_PATTERN = Regex("^\\s*... (\\d+) more$")

/** Regex to match an outer stack trace line. E.g.: "Caused by: java.io.IOException" */
private val CAUSED_BY_LINE_PATTERN = Regex("^\\s*Caused by:.+$")

/**
 * Marker to indicate stack trace lines that were originally of the form "... 5 more" but expanded
 * inline. If present, it will be found at the end of the line - this keeps it out of the way, but
 * at the same, the parser can check for it quickly.
 *
 * This is ultimately used by [ExceptionFolding] to determine which lines it can fold.
 */
private const val EXPANDED_STACK_TRACE_MARKER = "\u00A0"

private val SHOULD_BE_FOLDED = Regex("^\\s+at .+$EXPANDED_STACK_TRACE_MARKER$")

/**
 * When printing out exceptions, Java collapses frames that match those of the enclosing exception,
 * and just says "... N more". This class parses a sequence of lines from logcat, and maintains a
 * knowledge of the current stack trace. If it ever sees the pattern "... N more", it then tries to
 * see if that can be fully expanded with the correct frames from the enclosing exception. The
 * logcat view then folds these frames back and displays "...N more", except now users can unfold it
 * to view the full trace.
 *
 * @see
 *   [Description in Throwable.printStackTrace](http://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html.printStackTrace%28%29)
 */
internal object StackTraceExpander {

  fun wasLineExpanded(line: String): Boolean = line.matches(SHOULD_BE_FOLDED)

  @JvmStatic
  fun process(inputLines: Collection<String>): Collection<String> {
    val context = Context()
    return inputLines.flatMap { process(context, it) }
  }
}

/**
 * Given a line of output, detect if it's part of a stack trace and, if so, process it. This allows
 * us to keep track of context about outer exceptions as well as prepend lines with prefix
 * indentation. Lines not part of a stack trace are left unmodified.
 *
 * You should process each line of logcat output through this method and echo the result out to the
 * console.
 *
 * @return one or more processed lines. Note that most of the time, one call results in one line of
 *   processed output, but occasionally, in the case of elided lines (e.g. "... 3 more"), one input
 *   line is expanded into multiple processed lines.
 */
private fun process(context: Context, line: String): List<String> {
  return when {
    isFrameLine(line) -> handleFrameLine(context, line)

    // If this line isn't the start of a stack trace, and we aren't currently in a stack trace,
    // then this has to be a normal line. Let's save time by avoiding all later checks.
    !context.isInTrace -> listOf(line)
    isCauseLine(line) -> handleCausedByLine(context, line)

    // Since we are in a stack trace and this is not a frame or a cause, it must either be the first
    // line after the stack trace the elided
    // marker.
    else -> handleElidedOrEndOfTrace(context, line)
  }
}

private fun handleFrameLine(context: Context, line: String): List<String> {
  context.isInTrace = true
  context.currentStack.add(line)
  return listOf(line)
}

private fun handleCausedByLine(context: Context, line: String): List<String> {
  assert(context.isInTrace) { String.format("Unexpected line while parsing stack trace: %s", line) }

  // if it is a "Caused by:" line, then we're starting a new stack, and our current stack becomes
  // our previous (outer) stack.
  context.swapStacks()

  return listOf(line)
}

private fun handleElidedOrEndOfTrace(context: Context, line: String): List<String> {
  val count = getElidedFrameCount(line)
  return if (count != null) {
    handleElidedLine(context, line, count)
  } else {
    context.reset()
    listOf(line)
  }
}

private fun handleElidedLine(context: Context, line: String, elidedCount: Int): List<String> {
  assert(context.isInTrace) { String.format("Unexpected line while parsing stack trace: %s", line) }
  assert(elidedCount > 0)

  val lines = mutableListOf<String>()
  // if it is the "...N more", we replace that line with the last N frames from the outer stack
  val previousStack = context.previousStack
  val startIndex = previousStack.size - elidedCount
  if (startIndex >= 0) {
    for (i in 0 until elidedCount) {
      val frame = previousStack[startIndex + i]
      lines.add(frame + EXPANDED_STACK_TRACE_MARKER)
      context.currentStack.add(frame)
    }
  } else {
    // something went wrong: we don't actually have the required number of frames in the outer stack
    // in this case, we don't expand the frames
    lines.add(line)
  }
  return lines
}

private fun isFrameLine(line: String) = line.matches(EXCEPTION_LINE_PATTERN)

private fun isCauseLine(line: String): Boolean = line.matches(CAUSED_BY_LINE_PATTERN)

/**
 * Returns the number of stack trace lines that were collapsed, or null if this line doesn't match
 * the elided pattern.
 */
private fun getElidedFrameCount(line: String): Int? =
  ELIDED_LINE_PATTERN.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()

private class Context {
  /**
   * True if we've started parsing lines that match the [EXCEPTION_LINE_PATTERN] and
   * [ELIDED_LINE_PATTERN] patterns and haven't yet reached the end.
   */
  var isInTrace = false

  private var stack1IsCurrent = true
  private val stack1 = mutableListOf<String>()
  private val stack2 = mutableListOf<String>()

  val currentStack
    get() = if (stack1IsCurrent) stack1 else stack2

  val previousStack
    get() = if (stack1IsCurrent) stack2 else stack1

  // currentStack becomes previousStack and previousStack is cleared and becomes currentStack.
  fun swapStacks() {
    previousStack.clear()
    stack1IsCurrent = !stack1IsCurrent
  }

  fun reset() {
    isInTrace = false
    stack1.clear()
    stack2.clear()
  }
}

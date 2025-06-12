/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.base.Splitter
import com.intellij.build.output.BuildOutputInstantReader
import java.util.regex.Pattern

object BuildOutputParserUtils {
  const val MESSAGE_GROUP_INFO_SUFFIX = " info"
  const val MESSAGE_GROUP_STATISTICS_SUFFIX = " statistics"
  const val MESSAGE_GROUP_WARNING_SUFFIX = " warnings"
  const val MESSAGE_GROUP_ERROR_SUFFIX = " errors"
  const val BUILD_FAILED_WITH_EXCEPTION_LINE = "FAILURE: Build failed with an exception."
  const val BUILD_COMPLETED_WITH_FAILURES_LINE = "FAILURE: Build completed with "

  fun String.isBuildFailureOutputLine(): Boolean =
    startsWith(BUILD_FAILED_WITH_EXCEPTION_LINE) ||
    startsWith(BUILD_COMPLETED_WITH_FAILURES_LINE)

  fun String?.isEndOfBuildOutputLine(): Boolean =
    this == null ||
    startsWith("BUILD FAILED") ||
    startsWith("CONFIGURE FAILED") ||
    startsWith("BUILD SUCCESSFUL") ||
    startsWith("CONFIGURE SUCCESSFUL")

  fun String.isCompilationFailureLine(): Boolean =
    this.startsWith("Compilation failed") ||
    this == "Compilation error. See log for more details" ||
    this == "Script compilation error:" ||
    this.contains("compiler failed")

  /** Extracts task name from @param [parentEventId]. */
  fun extractTaskNameFromId(parentEventId: Any): String? {
    if (parentEventId !is String) {
      return null
    }
    //[-447475743:244193606] > [Task :app:compileDebugJavaWithJavac]
    val taskNamePattern = Pattern.compile("> \\[Task (?<gradleFullTaskName>(?::[^:]+)*)]")
    val matcher = taskNamePattern.matcher(parentEventId as String)
    if (matcher.find()) {
      return matcher.group("gradleFullTaskName")
    }
    return null
  }
}


/**
 * A simple [BuildOutputInstantReader] useful for parsing already read message and for build output parsing tests.
 *
 * This reader simply takes an input and splits it around any newlines, omitting empty strings,
 * which mimics the behavior of [BuildOutputInstantReaderImpl]
 */
class LinesBuildOutputInstantReader(
  private val lines: List<String>,
  private val parentEventId: Any
) : BuildOutputInstantReader {
  constructor(input: String, parentEventId: Any) :
    this(Splitter.on("\n").omitEmptyStrings().split(input).toList(), parentEventId)

  var currentIndex: Int = -1
    private set

  override fun getParentEventId() = parentEventId

  override fun readLine(): String? {
    currentIndex++
    return if (currentIndex >= lines.size) {
      null
    }
    else lines[currentIndex]
  }

  override fun pushBack() {
    pushBack(1)
  }

  override fun pushBack(numberOfLines: Int) {
    currentIndex -= numberOfLines
  }
}
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

import com.intellij.build.output.BuildOutputInstantReader
import java.util.regex.Pattern

object BuildOutputParserUtils {
    const val MESSAGE_GROUP_INFO_SUFFIX = " info"
    const val MESSAGE_GROUP_STATISTICS_SUFFIX = " statistics"
    const val MESSAGE_GROUP_WARNING_SUFFIX = " warnings"
    const val MESSAGE_GROUP_ERROR_SUFFIX = " errors"
    const val BUILD_FAILED_WITH_EXCEPTION_LINE = "FAILURE: Build failed with an exception."
    //TODO (b/362959090): This is part of the new Gradle output for multiple failures. Need to adjust all parsers.
    const val BUILD_COMPLETED_WITH_FAILURES_LINE = "FAILURE: Build completed with "

    fun consumeRestOfOutput(reader: BuildOutputInstantReader) {
      while (true) {
        val nextLine = reader.readLine() ?: break
        if (nextLine.startsWith("BUILD FAILED") || nextLine.startsWith("CONFIGURE FAILED")) break
      }
    }

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
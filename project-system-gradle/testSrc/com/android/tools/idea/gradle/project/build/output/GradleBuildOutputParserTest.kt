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
package com.android.tools.idea.gradle.project.build.output

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.util.SystemInfo
import org.junit.Test

class GradleBuildOutputParserTest : BuildOutputParserTest() {

  @Test
  fun parseErrorAndWarning() {
    val filePath = when {
      // Backslash should be double-escaped in json.
      SystemInfo.isWindows -> "C:\\app\\src\\main\\res\\layout\\activity_main.xml"
      else -> "/app/src/main/res/layout/activity_main.xml"
    }
    val filePathJson = filePath.replace("\\", "\\\\")
    parseOutput(
      parentEventId = "testId",
      gradleOutput = """
        AGPBI: {"kind":"error","text":"Error message.","sources":[{"file":"$filePathJson","position":{"startLine":10,"startColumn":31,"startOffset":456,"endColumn":44,"endOffset":469}}],"tool":"AAPT"}
        This is a detail line
        AGPBI: {"kind":"warning","text":"Warning message.","sources":[{}],"tool":"D8"}
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Error message.",
          isFileMessageEvent = true,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "AAPT errors",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId",
          filePosition = "$filePath:11:32-11:45",
          description = "Error message.",
        ),
        ExpectedEvent(
          message = "Warning message.",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "D8 warnings",
          kind = MessageEvent.Kind.WARNING,
          parentId = "testId",
          description = "Warning message."
        )
      )
    )
  }

  @Test
  fun parseWithMultilineWarning() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = """
        AGPBI: {"kind":"warning","text":"Warning message.\nWarning line 1\nWarning line 2\nWarning line 3","sources":[{}],"tool":"D8"}
        This is a detail line
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Warning message.",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "D8 warnings",
          kind = MessageEvent.Kind.WARNING,
          parentId = "testId",
          description = """
            Warning message.
            Warning line 1
            Warning line 2
            Warning line 3
          """.trimIndent()
        )
      )
    )
  }

  @Test
  fun parseWithErrorIgnoreOutput() {
    // 'error:' messages normally parsed by AndroidGradlePluginOutputParser.
    // Check here that first such lines are consumed by GradleBuildOutputParser being part of message
    // but last one is not and triggers AndroidGradlePluginOutputParser.
    parseOutput(
      parentEventId = "testId",
      gradleOutput = """
        AGPBI: {"kind":"error","text":"Error message.","sources":[{}],"original":"error: line 1\nerror: line 2\nerror: line 3","tool":"Dex"}
        error: line 1
        error: line 2
        error: line 3
        Unrelated output
        error: line 1
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Error message.",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Dex errors",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId",
          description = """
            error: line 1
            error: line 2
            error: line 3
          """.trimIndent()
        ),
        ExpectedEvent(
          message = "line 1",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Android Gradle Plugin",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId",
          description = """
            line 1
          """.trimIndent()
        )
      )
    )
  }

  @Test
  fun parseChangeBuildId() {
    parseOutput(
      parentEventId = "testId_1",
      gradleOutput = """
        AGPBI: {"kind":"error","text":"Error message.","sources":[{}],"original":"error: line 1\nerror: line 2\nerror: line 3","tool":"Dex"}
        error: line 1
        Unrelated output
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "Error message.",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Dex errors",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId_1",
          description = """
            error: line 1
            error: line 2
            error: line 3
          """.trimIndent()
        )
      )
    )
    // GradleBuildOutputParser should not consume outputs from different task, so they generate messages
    parseOutput(
      parentEventId = "testId_2",
      gradleOutput = """
        error: line 1
        error: line 2
      """.trimIndent(),
      expectedEvents = listOf(
        ExpectedEvent(
          message = "line 1",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Android Gradle Plugin",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId_2",
          description = """
            line 1
          """.trimIndent()
        ),
        ExpectedEvent(
          message = "line 2",
          isFileMessageEvent = false,
          isBuildIssueEvent = false,
          isDuplicateMessageAware = false,
          group = "Android Gradle Plugin",
          kind = MessageEvent.Kind.ERROR,
          parentId = "testId_2",
          description = """
            line 2
          """.trimIndent()
        )
      )
    )
    // returning to parsing original task - GradleBuildOutputParser should just consume known lines.
    parseOutput(
      parentEventId = "testId_1",
      gradleOutput = """
        error: line 2
        error: line 3
      """.trimIndent(),
      expectedEvents = emptyList<ExpectedEvent>()
    )
  }
}
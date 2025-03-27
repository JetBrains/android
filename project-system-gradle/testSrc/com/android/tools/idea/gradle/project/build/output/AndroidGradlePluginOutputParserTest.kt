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
import org.junit.Test

//TODO this test seem to be about vary old way og AGP producing messages. Double-check and add comment.
class AndroidGradlePluginOutputParserTest  : BuildOutputParserTest() {

  @Test
  fun parseWarningFromOutput() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "WARNING: Configuration 'compile' is obsolete and has been replaced with 'implementation'.",
      expectedEvents = listOf(ExpectedEvent(
        message = "Configuration 'compile' is obsolete and has been replaced with 'implementation'.",
        isFileMessageEvent = false,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Android Gradle Plugin",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        description = """
        Configuration 'compile' is obsolete and has been replaced with 'implementation'.
""".trimIndent()))
    )
  }

  @Test
  fun parseJavacWithSource() {
    //TODO why javac parser does not get it?
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "MyClass.java:38: warning: [serial] serializable class MyClass has no definition of serialVersionUID",
      expectedEvents = emptyList()
    )
  }

  /**
   * Javac warnings without sources are currently treated as AGP warnings as there is no reliable way to distinguish them from each other.
   */
  @Test
  fun parseJavacWithoutSource() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "warning: [serial] serializable class MyClass has no definition of serialVersionUID",
      expectedEvents = listOf(ExpectedEvent(
        message = "[serial] serializable class MyClass has no definition of serialVersionUID",
        isFileMessageEvent = false,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Android Gradle Plugin",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        description = """
        [serial] serializable class MyClass has no definition of serialVersionUID
        """.trimIndent()))
    )
  }

  @Test
  fun parseAGPResourceWarning() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "warning: string 'snowball' has no default translation.",
      expectedEvents = listOf(ExpectedEvent(
        message = "string 'snowball' has no default translation.",
        isFileMessageEvent = false,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Android Gradle Plugin",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        description = """
        string 'snowball' has no default translation.
        """.trimIndent()))
    )
  }

  @Test
  fun parseAGPError() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "ERROR: Something went wrong!",
      expectedEvents = listOf(ExpectedEvent(
        message = "Something went wrong!",
        isFileMessageEvent = false,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Android Gradle Plugin",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        description = """
        Something went wrong!
        """.trimIndent()))
    )
  }

  @Test
  fun parseJavaError() {
    //TODO why javac parser does not get it?
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "MyClass.java:23 error: Something went REALLY wrong!",
      expectedEvents = emptyList()
    )
  }
}
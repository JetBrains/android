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
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CmakeOutputParserTest : BuildOutputParserTest() {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var file: File

  @Before
  fun setUp() {
    file = temporaryFolder.newFile()
  }

  @Test
  fun multilineCmakeWarningInFileWithoutLineNumberOrColumn() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Warning: Warning in cmake code at
$filePath::
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Warning: Warning in cmake code at $filePath::",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake warnings",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "$filePath:0:0-0:0",
        description = """
          CMake Warning: Warning in cmake code at
          $filePath::
        """.trimIndent()))
    )
  }

  @Test
  fun multilineCmakeWarningInFileWithLineNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Warning: Warning in cmake code at
$filePath:13:
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Warning: Warning in cmake code at $filePath:13:",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake warnings",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "$filePath:13:0-13:0",
        description = """
          CMake Warning: Warning in cmake code at
          $filePath:13:
        """.trimIndent()))
    )
  }

  @Test
  fun multilineCmakeWarningInFileWithLineNumberAndColumnNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Warning: Warning in cmake code at
$filePath:13:42
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Warning: Warning in cmake code at $filePath:13:42",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake warnings",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "$filePath:13:42-13:42",
        description = """
          CMake Warning: Warning in cmake code at
          $filePath:13:42
        """.trimIndent()))
    )
  }

  @Test
  fun multilineCmakeErrorInFileWithoutLineNumberOrColumn() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Error: Error in cmake code at
$filePath::
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Error: Error in cmake code at $filePath::",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake errors",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "$filePath:0:0-0:0",
        description = """
          CMake Error: Error in cmake code at
          $filePath::
        """.trimIndent()))
    )
  }

  @Test
  fun multilineCmakeErrorInFileWithLineNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Error: Error in cmake code at
$filePath:13:
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Error: Error in cmake code at $filePath:13:",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake errors",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "$filePath:13:0-13:0",
        description = """
          CMake Error: Error in cmake code at
          $filePath:13:
        """.trimIndent()))
    )
  }

  @Test
  fun multilineCmakeErrorInFileWithLineNumberAndColumnNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Error: Error in cmake code at
$filePath:13:42
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Error: Error in cmake code at $filePath:13:42",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake errors",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "$filePath:13:42-13:42",
        description = """
          CMake Error: Error in cmake code at
          $filePath:13:42
        """.trimIndent()))
    )
  }

  @Test
  fun singleLineCmakeWarningInFileWithoutLineNumberOrColumn() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Warning: Warning in cmake code at $filePath::
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Warning: Warning in cmake code at $filePath::",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake warnings",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "$filePath:0:0-0:0",
        description = """
          CMake Warning: Warning in cmake code at $filePath::
        """.trimIndent()))
    )
  }

  @Test
  fun singleLineCmakeWarningInFileWithLineNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Warning: Warning in cmake code at $filePath:13:
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Warning: Warning in cmake code at $filePath:13:",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake warnings",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "$filePath:13:0-13:0",
        description = """
          CMake Warning: Warning in cmake code at $filePath:13:
        """.trimIndent()))
    )
  }

  @Test
  fun singleLineCmakeWarningInFileWithLineNumberAndColumnNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Warning: Warning in cmake code at $filePath:13:42
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Warning: Warning in cmake code at $filePath:13:42",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake warnings",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "$filePath:13:42-13:42",
        description = """
          CMake Warning: Warning in cmake code at $filePath:13:42
        """.trimIndent()))
    )
  }

  @Test
  fun singleLineCmakeErrorInFileWithoutLineNumberOrColumn() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Error: Error in cmake code at $filePath::
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Error: Error in cmake code at $filePath::",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake errors",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "$filePath:0:0-0:0",
        description = """
          CMake Error: Error in cmake code at $filePath::
        """.trimIndent()))
    )
  }

  @Test
  fun singleLineCmakeErrorInFileWithLineNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Error: Error in cmake code at $filePath:13:
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Error: Error in cmake code at $filePath:13:",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake errors",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "$filePath:13:0-13:0",
        description = """
          CMake Error: Error in cmake code at $filePath:13:
        """.trimIndent()))
    )
  }

  @Test
  fun singleLineCmakeErrorInFileWithLineNumberAndColumnNumber() {
    val filePath = file.absolutePath
    val buildOutput = """
CMake Error: Error in cmake code at $filePath:13:42
""".trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "CMake Error: Error in cmake code at $filePath:13:42",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake errors",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "$filePath:13:42-13:42",
        description = """
          CMake Error: Error in cmake code at $filePath:13:42
        """.trimIndent()))
    )
  }

  @Test
  fun longErrorMessage() {
    val makefile = temporaryFolder.newFile("CMakeLists.txt").absolutePath
    val buildOutput = """
      CMake Error at $makefile:49 (message): Lorem ipsum dolor
      amet, consectetur adipiscing elit.  Etiam ac aliquam lacus.  Nullam suscipit nisl
      vitae sodales varius.  Donec eu enim ante.  Maecenas congue ante a nibh tristique,
      in sagittis velit suscipit.  Ut hendrerit molestie augue quis sodales.  Praesent ac
      consectetur est.  Duis at auctor neque.
    """.trimIndent()
    parseOutput(
      parentEventId = "testId",
      gradleOutput = buildOutput,
      expectedEvents = listOf(ExpectedEvent(
        message = "Lorem ipsum dolor "
                  + "amet, consectetur adipiscing elit.  Etiam ac aliquam lacus.  Nullam suscipit nisl "
                  + "vitae sodales varius.  Donec eu enim ante.  Maecenas congue ante a nibh tristique, "
                  + "in sagittis velit suscipit.  Ut hendrerit molestie augue quis sodales.  Praesent ac "
                  + "consectetur est.  Duis at auctor neque.",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "CMake errors",
        kind= MessageEvent.Kind.ERROR,
        parentId = "testId",
        filePosition = "$makefile:49:0-49:0",
        description = """
          CMake Error at $makefile:49 (message): Lorem ipsum dolor
          amet, consectetur adipiscing elit.  Etiam ac aliquam lacus.  Nullam suscipit nisl
          vitae sodales varius.  Donec eu enim ante.  Maecenas congue ante a nibh tristique,
          in sagittis velit suscipit.  Ut hendrerit molestie augue quis sodales.  Praesent ac
          consectetur est.  Duis at auctor neque.
        """.trimIndent()))
    )
  }
}

class CmakeOutputParserUnitTest {

  @Test
  fun testPosixFilePatternMatcherForErrorFileAndLineNumberError() {
    val lineNumber = 123
    val columnNumber = 456
    val filePath = "/path/to/file.type"
    val error = "CMake Error at $filePath:$lineNumber:$columnNumber"

    val matcher = CmakeOutputParser.errorFileAndLineNumber.matcher(error)
    Assert.assertTrue("[match file path]", matcher.matches())

    val matchedPath = matcher.group(2)
    val fields = CmakeOutputParser.matchErrorFileAndLineNumberErrorParts(matcher, error)
    Assert.assertEquals("[source path]", filePath.trim { it <= ' ' }, matchedPath)
    Assert.assertEquals("[line number]", lineNumber.toLong(), fields.lineNumber.toLong())
    Assert.assertEquals("[column number]", columnNumber.toLong(), fields.columnNumber.toLong())
  }

  @Test
  fun testPosixFilePatternMatcherForFileAndLineNumberError() {
    val lineNumber = 123
    val columnNumber = 456
    val filePath = "/path/to/file.type"
    val error = "$filePath:$lineNumber:$columnNumber"

    val matcher = CmakeOutputParser.fileAndLineNumber.matcher(error)
    Assert.assertTrue("[match file path]", matcher.matches())

    val matchedPath = matcher.group(1)
    val fields = CmakeOutputParser.matchFileAndLineNumberErrorParts(matcher, error)
    Assert.assertEquals("[source path]", filePath.trim { it <= ' ' }, matchedPath)
    Assert.assertEquals("[line number]", lineNumber.toLong(), fields.lineNumber.toLong())
    Assert.assertEquals("[column number]", columnNumber.toLong(), fields.columnNumber.toLong())
  }

  @Test
  fun testWindowsFilePatternMatcherForErrorFileAndLineNumberError() {
    val lineNumber = 123
    val columnNumber = 456
    val filePath = "C:\\Path\\to\\file.type"
    val error = "CMake Error at $filePath:$lineNumber:$columnNumber"

    val matcher = CmakeOutputParser.errorFileAndLineNumber.matcher(error)
    Assert.assertTrue("[match file path]", matcher.matches())

    val matchedPath = matcher.group(2)
    val fields = CmakeOutputParser.matchErrorFileAndLineNumberErrorParts(matcher, error)
    Assert.assertEquals("[source path]", filePath.trim { it <= ' ' }, matchedPath)
    Assert.assertEquals("[line number]", lineNumber.toLong(), fields.lineNumber.toLong())
    Assert.assertEquals("[column number]", columnNumber.toLong(), fields.columnNumber.toLong())
  }

  @Test
  fun testWindowsFilePatternMatcherForFileAndLineNumberError() {
    val lineNumber = 123
    val columnNumber = 456
    val filePath = "C:\\Path\\to\\file.type"
    val error = "$filePath:$lineNumber:$columnNumber"

    val matcher = CmakeOutputParser.fileAndLineNumber.matcher(error)
    Assert.assertTrue("[match file path]", matcher.matches())

    val matchedPath = matcher.group(1)
    val fields = CmakeOutputParser.matchFileAndLineNumberErrorParts(matcher, error)
    Assert.assertEquals("[source path]", filePath.trim { it <= ' ' }, matchedPath)
    Assert.assertEquals("[line number]", lineNumber.toLong(), fields.lineNumber.toLong())
    Assert.assertEquals("[column number]", columnNumber.toLong(), fields.columnNumber.toLong())
  }
}
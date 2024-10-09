/*
 * Copyright (C) 2022 The Android Open Source Project
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.xml.sax.SAXParseException
import java.io.File

class XmlErrorOutputParserTest : BuildOutputParserTest() {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var sourceFile: File

  @Before
  fun setUp() {
    sourceFile = temporaryFolder.newFile()
  }

  @Test
  fun parseFileLineColumnAndMessage() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "org.xml.sax.SAXParseException; systemId: file:${sourceFile.absolutePath}; lineNumber: 4; columnNumber: 5; Element type \"ASd\" must be followed by either attribute specifications, \">\" or \"/>\".",
      expectedEvents = listOf(ExpectedEvent(
        message = "Element type \"ASd\" must be followed by either attribute specifications, \">\" or \"/>\".",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Xml parsing errors",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "${sourceFile.absolutePath}:4:5-4:5",
        description = """
        Element type "ASd" must be followed by either attribute specifications, ">" or "/>".
        """.trimIndent()))
    )
  }

  @Test
  fun parseFileLineColumnAndMessageWithPublicId() {
    parseOutput(
      parentEventId = "testId",
      gradleOutput = "> org.xml.sax.SAXParseExceptionpublicId: test; systemId: ${sourceFile.absolutePath}; lineNumber: 20; columnNumber: 1; XML document structures must start and end within the same entity.",
      expectedEvents = listOf(ExpectedEvent(
        message = "XML document structures must start and end within the same entity.",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Xml parsing errors",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "${sourceFile.absolutePath}:20:1-20:1",
        description = """
        XML document structures must start and end within the same entity.
        """.trimIndent()))
    )
  }

  @Test
  fun parseFileLineAndMessageFromException() {
    val exception = SAXParseException("The content of elements must consist of well-formed character data or markup.", null,
                                      sourceFile.absolutePath, 3, -1)
    parseOutput(
      parentEventId = "testId",
      gradleOutput = exception.toString(),
      expectedEvents = listOf(ExpectedEvent(
        message = "The content of elements must consist of well-formed character data or markup.",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Xml parsing errors",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "${sourceFile.absolutePath}:3:0-3:0",
        description = """
        The content of elements must consist of well-formed character data or markup.
        """.trimIndent()))
    )
  }

  @Test
  fun parseFileAndMessageFromException() {
    val exception = SAXParseException("Premature end of file.", null, sourceFile.absolutePath, -1, -1)

    parseOutput(
      parentEventId = "testId",
      gradleOutput = exception.toString(),
      expectedEvents = listOf(ExpectedEvent(
        message = "Premature end of file.",
        isFileMessageEvent = true,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Xml parsing errors",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        filePosition = "${sourceFile.absolutePath}:0:0-0:0",
        description = """
        Premature end of file.
        """.trimIndent()))
    )
  }

  @Test
  fun parseMessageFromException() {
    val exception = SAXParseException("Some error message.", "bla", null, -1, -1)

    parseOutput(
      parentEventId = "testId",
      gradleOutput = exception.toString(),
      expectedEvents = listOf(ExpectedEvent(
        message = "Some error message.",
        isFileMessageEvent = false,
        isBuildIssueEvent = false,
        isDuplicateMessageAware = false,
        group = "Xml parsing errors",
        kind= MessageEvent.Kind.WARNING,
        parentId = "testId",
        description = """
        Some error message.
        """.trimIndent()))
    )
  }
}
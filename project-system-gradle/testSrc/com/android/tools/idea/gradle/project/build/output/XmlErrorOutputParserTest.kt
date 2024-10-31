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

import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.output.BuildOutputInstantReader
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.xml.sax.SAXParseException
import java.io.File
import java.util.function.Consumer

class XmlErrorOutputParserTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Mock
  lateinit var reader: BuildOutputInstantReader
  @Mock
  lateinit var messageConsumer: Consumer<in BuildEvent?>

  private lateinit var parser: XmlErrorOutputParser
  private lateinit var sourceFile: File

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    parser = XmlErrorOutputParser()
    sourceFile = temporaryFolder.newFile()

    whenever(reader.parentEventId).thenReturn("testId")
  }

  private fun verifyFileMessageEvent(buildEvent: BuildEvent,
                                     expectedPath: String,
                                     group: String,
                                     expectedKind: MessageEvent.Kind,
                                     expectedLineNumber: Int,
                                     expectedColumnNumber: Int,
                                     expectedMessage: String) {
    assertThat(buildEvent).isInstanceOf(FileMessageEvent::class.java)
    val messageEvent = buildEvent as FileMessageEvent

    assertThat(messageEvent.kind).isEqualTo(expectedKind)
    assertThat(expectedPath).isEqualTo(messageEvent.filePosition.file.absolutePath)
    assertThat(messageEvent.filePosition.startLine).isEqualTo(expectedLineNumber - 1)
    assertThat(messageEvent.filePosition.startColumn).isEqualTo(expectedColumnNumber - 1)
    assertThat(messageEvent.group).isEqualTo(group)
    assertThat(messageEvent.description).isEqualTo(expectedMessage)
  }

  @Test
  fun parseFileLineColumnAndMessage() {
    val output = "org.xml.sax.SAXParseException; systemId: file:${sourceFile.absolutePath}; lineNumber: 4; columnNumber: 5; Element type \"ASd\" must be followed by either attribute specifications, \">\" or \"/>\"."

    val captor = ArgumentCaptor.forClass(BuildEvent::class.java)
    assertThat(parser.parse(output, reader, messageConsumer)).isTrue()

    verify(messageConsumer).accept(captor.capture())
    assertThat(captor.allValues).hasSize(1)
    verifyFileMessageEvent(captor.value, sourceFile.absolutePath, "Xml parsing errors", MessageEvent.Kind.WARNING, 4, 5,
                           "Element type \"ASd\" must be followed by either attribute specifications, \">\" or \"/>\".")
  }

  @Test
  fun parseFileLineColumnAndMessageWithPublicId() {
    val output = "> org.xml.sax.SAXParseExceptionpublicId: test; systemId: ${sourceFile.absolutePath}; lineNumber: 20; columnNumber: 1; XML document structures must start and end within the same entity."

    val captor = ArgumentCaptor.forClass(BuildEvent::class.java)
    assertThat(parser.parse(output, reader, messageConsumer)).isTrue()

    verify(messageConsumer).accept(captor.capture())
    assertThat(captor.allValues).hasSize(1)
    verifyFileMessageEvent(captor.value, sourceFile.absolutePath, "Xml parsing errors", MessageEvent.Kind.WARNING, 20, 1,
                           "XML document structures must start and end within the same entity.")

  }

  @Test
  fun parseFileLineAndMessageFromException() {
    val exception = SAXParseException("The content of elements must consist of well-formed character data or markup.", null,
                                      sourceFile.absolutePath, 3, -1)

    val captor = ArgumentCaptor.forClass(BuildEvent::class.java)
    assertThat(parser.parse(exception.toString(), reader, messageConsumer)).isTrue()

    verify(messageConsumer).accept(captor.capture())
    assertThat(captor.allValues).hasSize(1)
    verifyFileMessageEvent(captor.value, sourceFile.absolutePath, "Xml parsing errors", MessageEvent.Kind.WARNING, 3, 0,
                           "The content of elements must consist of well-formed character data or markup.")
  }

  @Test
  fun parseFileAndMessageFromException() {
    val exception = SAXParseException("Premature end of file.", null, sourceFile.absolutePath, -1, -1)

    val captor = ArgumentCaptor.forClass(BuildEvent::class.java)
    assertThat(parser.parse(exception.toString(), reader, messageConsumer)).isTrue()

    verify(messageConsumer).accept(captor.capture())
    assertThat(captor.allValues).hasSize(1)
    verifyFileMessageEvent(captor.value, sourceFile.absolutePath, "Xml parsing errors", MessageEvent.Kind.WARNING, 0, 0,
                           "Premature end of file.")
  }

  @Test
  fun parseMessageFromException() {
    val exception = SAXParseException("Some error message.", "bla", null, -1, -1)

    val captor = ArgumentCaptor.forClass(BuildEvent::class.java)
    assertThat(parser.parse(exception.toString(), reader, messageConsumer)).isTrue()

    verify(messageConsumer).accept(captor.capture())
    assertThat(captor.allValues).hasSize(1)
    assertThat(captor.value).isInstanceOf(MessageEvent::class.java)
    assertThat(captor.value).isNotInstanceOf(FileMessageEvent::class.java)

    val messageEvent = captor.value as MessageEvent

    assertThat(messageEvent.kind).isEqualTo(MessageEvent.Kind.WARNING)
    assertThat(messageEvent.group).isEqualTo("Xml parsing errors")
    assertThat(messageEvent.description).isEqualTo("Some error message.")
  }
}
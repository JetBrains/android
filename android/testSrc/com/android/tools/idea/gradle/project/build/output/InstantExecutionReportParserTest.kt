/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.output.BuildOutputInstantReader
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import java.util.function.Consumer

class InstantExecutionReportParserTest {
  @Mock
  lateinit var reader: BuildOutputInstantReader
  @Mock
  lateinit var messageConsumer: Consumer<in BuildEvent?>

  private lateinit var parser: InstantExecutionReportParser

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    parser = InstantExecutionReportParser()
    `when`(reader.parentEventId).thenReturn("testId")
  }

  @Test
  fun `No errors return false`() {
    assertThat(parser.parse("This is not an Instant execution error line", reader, messageConsumer))
  }

  @Test
  fun `No line return false`() {
    assertThat(parser.parse(null, reader, messageConsumer)).isFalse()
  }

  @Test
  fun `No reader return false`() {
    val urlReport = "file:///path/to/instant-execution-report.html"
    val errorLine = "See the complete report at $urlReport"
    assertThat(parser.parse(errorLine, null, messageConsumer)).isFalse()
  }

  @Test
  fun `No messageConsumer return false`() {
    val urlReport = "file:///path/to/instant-execution-report.html"
    val errorLine = "See the complete report at $urlReport"
    assertThat(parser.parse(errorLine, reader, null)).isFalse()
  }

  @Test
  fun `No instant execution report html return false`() {
    val urlReport = "file:///path/to/other-report.html"
    val errorLine = "See the complete report at $urlReport"
    assertThat(parser.parse(errorLine, reader, messageConsumer)).isFalse()
  }

  @Test
  fun `Extra characters return false`() {
    val urlReport = "file:///path/to/instant-execution-report.html"
    val errorLine = "See the complete report at $urlReport. This is an additional message."
    assertThat(parser.parse(errorLine, reader, messageConsumer)).isFalse()
  }

  @Test
  fun `Report link is created`() {
    val urlReport = "file:///path/to/instant-execution-report.html"
    val errorLine = "See the complete report at $urlReport"
    verifyLink(urlReport, errorLine)
  }

  @Test
  fun `Report link is created with white spaces`() {
    val urlReport = "file:///path/to/instant-execution-report.html"
    val errorLine = "  See the complete report at $urlReport \n"
    verifyLink(urlReport, errorLine)
  }

  private fun verifyLink(urlReport: String, errorLine: String) {
    val captor = ArgumentCaptor.forClass(BuildEvent::class.java)
    assertThat(parser.parse(errorLine, reader, messageConsumer)).isTrue()
    verify(messageConsumer).accept(captor.capture())
    val events = captor.allValues
    assertThat(events).hasSize(1)
    assertThat(events[0]).isInstanceOf(BuildIssueEvent::class.java)
    val quickFixes = (events[0] as BuildIssueEvent).issue.quickFixes
    assertThat(quickFixes).hasSize(1)
    assertThat(quickFixes[0]).isInstanceOf(OpenLinkQuickFix::class.java)
    assertThat((quickFixes[0] as OpenLinkQuickFix).link).isEqualTo(urlReport)
  }
}
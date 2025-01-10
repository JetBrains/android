/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.benchmark.BenchmarkOutput.Companion.LINK_GROUP
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertNotNull

@RunWith(JUnit4::class)
class BenchmarkOutputTest {

  @Test
  fun simpleSingleLineBenchmarkPrint() {
    val line = "test benchmark line"
    val benchmark = BenchmarkOutput(line)
    val expectedOutput = "$line\n"
    validateConsoleOutput(benchmark, expectedOutput)
  }

  @Test
  fun simpleMultilineLineBenchmarkPrint() {
    val line1 = "test benchmark line 1"
    val line2 = "test benchmark line 2"
    val benchmark = BenchmarkOutput("$line1\n$line2")
    val expectedOutput = "$line1\n$line2\n"
    validateConsoleOutput(benchmark, expectedOutput)
  }

  @Test
  fun hyperlinkSingleLineBenchmarkPrint() {
    val line1 = "This [link me](file://is/a/hyperlink/test)"
    val benchmark = BenchmarkOutput(line1)
    val expectedOutput = "This link me\n"
    val expectedHyperlink = mutableListOf("link me")
    validateConsoleOutput(benchmark, expectedOutput, expectedHyperlink)
  }

  @Test
  fun hyperlinkMultiLineBenchmarkPrint() {
    val line1 = "This [link me](file://is/a/hyperlink/test)"
    val line2 = "This [second link](file://a/second/hyperlink)"
    val benchmark = BenchmarkOutput("$line1\n$line2")
    val expectedOutput = "This link me\nThis second link\n"
    val expectedHyperlink = listOf("link me", "second link")
    validateConsoleOutput(benchmark, expectedOutput, expectedHyperlink)
  }

  @Test
  fun hyperlinkInlineFormatBenchmarkPrint() {
    val line1 = "This [link me](file://is/a/hyperlink/test)\tmin: [trace](file://path/to/trace)\tmax: [trace2](file://path/to/trace)"
    val benchmark = BenchmarkOutput(line1)
    val expectedOutput = "This link me\tmin: trace\tmax: trace2\n"
    val expectedHyperlink = listOf("link me", "trace", "trace2")
    validateConsoleOutput(benchmark, expectedOutput, expectedHyperlink)
  }

  @Test
  fun hyperlinkWithUriInlineFormatBenchmarkPrint() {
    val line1 = "This [link me](uri://is/a/hyperlink/test)\tmin: [trace](file://path/to/trace)\tmax: [trace2](file://path/to/trace)"
    val benchmark = BenchmarkOutput(line1)
    val expectedOutput = "This link me\tmin: trace\tmax: trace2\n"
    val expectedHyperlink = listOf("link me", "trace", "trace2")
    validateConsoleOutput(benchmark, expectedOutput, expectedHyperlink)
  }

  @Test
  fun supportedHyperLinkFormats() {
      val path = "/path/to/resource"
      val parameters = "foo=bar"
      val contents = """
        This V3 [link](uri://$path?$parameters) is valid.
        This V2 [link](file://$path) is valid.
      """.trimIndent()
    val benchmark = BenchmarkOutput(contents)
    val v3Link = benchmark.lines.find {
      it.rawText.contains("V3")
    }
    assertNotNull(v3Link)
    assertThat(v3Link.matches?.groups?.get(LINK_GROUP)?.value == "uri://$path?$parameters")
    val v2Link = benchmark.lines.find {
      it.rawText.contains("V2")
    }
    assertNotNull(v2Link)
    assertThat(v2Link.matches?.groups?.get(LINK_GROUP)?.value == "file://$path")
  }

  @Test
  fun foldReturnsNewInstance() {
    val benchmarkA = BenchmarkOutput("benchmarkA")
    val benchmarkB = BenchmarkOutput("benchmarkB")
    val result = benchmarkA.fold(benchmarkB)
    assertThat(result).isNotEqualTo(benchmarkA)
    assertThat(result).isNotEqualTo(benchmarkB)
    assertThat(result).isNotEqualTo(BenchmarkOutput.Empty)
    assertThat(result.lines.joinToString{ it.rawText }).isEqualTo("benchmarkA, benchmarkB")
  }

  private fun validateConsoleOutput(benchmark: BenchmarkOutput, expectedOuput: String = "", expectedLinkLabels: List<String> = emptyList()) {
    val testView = mock(ConsoleView::class.java)
    val outputString = StringBuffer()
    val printedHyperlinks = mutableListOf<String>()
    whenever(testView.print(anyString(), any())).then {
      outputString.append(it.arguments[0])
    }
    whenever(testView.printHyperlink(anyString(), any())).then {
      printedHyperlinks.add(it.arguments[0].toString())
      outputString.append(it.arguments[0])
    }

    benchmark.print(testView, ConsoleViewContentType.LOG_INFO_OUTPUT)

    assertThat(outputString.toString()).isEqualTo(expectedOuput)
    assertThat(printedHyperlinks).containsExactlyElementsIn(expectedLinkLabels)
  }
}
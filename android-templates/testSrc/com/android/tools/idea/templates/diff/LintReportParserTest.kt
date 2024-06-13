/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.templates.diff

import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getTestDataRoot
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.io.path.Path
import kotlin.test.assertContentEquals

class LintReportParserTest {
  /**
   * Runs LintReportParser on an example Lint report (generated from Basic Views Activity), in case
   * the report format changes in the future
   */
  @Test
  fun parseExampleReport() {
    val exampleReport =
      getTestDataRoot().resolve("lintReportParser").resolve("lint-results-debug.txt")

    val byteStream = ByteArrayOutputStream()
    val lintReportParser = LintReportParser(PrintStream(byteStream))

    lintReportParser.parseLintReport(
      Path("C:\\Windows\\fakeProjectDir\\testNewBasicViewsActivity_VALIDATING_"),
      exampleReport
    )

    val expected =
      """1 FragmentTagUsage from androidx.fragment issues:
    [...]\Template test module\src\main\res\layout\content_main.xml:8: Error: Replace the <fragment> tag with FragmentContainerView. [FragmentTagUsage from androidx.fragment]
1 RedundantLabel issues:
    [...]\Template test module\src\main\AndroidManifest.xml:18: Error: Redundant label can be removed [RedundantLabel]
2 GradleDependency issues:
    [...]\gradle\libs.versions.toml:8: Error: A newer version of androidx.navigation:navigation-fragment than 2.6.0 is available: 2.7.0 [GradleDependency]
    [...]\gradle\libs.versions.toml:9: Error: A newer version of androidx.navigation:navigation-ui than 2.6.0 is available: 2.7.0 [GradleDependency]
1 ObsoleteSdkInt issues:
    [...]\Template test module\src\main\res\values-v23: Error: This folder configuration (v23) is unnecessary; minSdkVersion is 23. Merge all the resources in this folder into values. [ObsoleteSdkInt]
1 ContentDescription issues:
    [...]\Template test module\src\main\res\layout\activity_main.xml:24: Error: Missing contentDescription attribute on image [ContentDescription]

6 errors, 0 warnings
""".split("\r\n", "\n")

    // Splitting takes care of both Windows line endings and also makes the assertion error easier to read
    assertContentEquals(expected, byteStream.toString().split("\r\n", "\n"))
  }

  @Test
  fun parseNoIssuesReport() {
    val exampleReport =
      getTestDataRoot().resolve("lintReportParser").resolve("lint-results-debug-empty.txt")

    val byteStream = ByteArrayOutputStream()
    val lintReportParser = LintReportParser(PrintStream(byteStream))

    lintReportParser.parseLintReport(
      Path("C:\\Windows\\fakeProjectDir\\testNewBasicViewsActivity_VALIDATING_"),
      exampleReport
    )

    val expected =
      """No issues found.
""".split("\r\n", "\n")

    // Splitting takes care of both Windows line endings and also makes the assertion error easier to read
    assertContentEquals(expected, byteStream.toString().split("\r\n", "\n"))
  }
}

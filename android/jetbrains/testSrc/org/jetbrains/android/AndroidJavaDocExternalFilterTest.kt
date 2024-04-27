/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android

import com.android.test.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.FileReader
import kotlin.io.path.listDirectoryEntries

const val POINT_URL = "http://developer.android.com/reference/android/graphics/Point.html"

@RunWith(JUnit4::class)
class AndroidJavaDocExternalFilterTest {
  @get:Rule val projectRule = ProjectRule()
  private val project by lazy { projectRule.project }
  private val filter by lazy { AndroidJavaDocExternalFilter(project) }

  @Test
  fun buildFromStream() {
    // Do not run tests on Windows (see b/37120270)
    if (SystemInfo.isWindows) return
    val inputFiles = getInputFiles()
    assertThat(inputFiles).isNotEmpty()

    for (inputFile in inputFiles) {
      val inputReader = fileReader("input/$inputFile")
      val builtDoc = buildString { filter.doBuildFromStream(POINT_URL, inputReader, this) }

      val outputFile = inputFile.toOutputFile()
      val expected = StringUtil.convertLineSeparators(fileReader("output/$outputFile").readText())
      assertWithMessage("$inputFile did not match $outputFile").that(builtDoc).isEqualTo(expected)
    }
  }

  private fun getInputFiles(): List<String> {
    val inputDir =
      TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/javadoc/classes/input")
    return inputDir.listDirectoryEntries().map { p -> p.fileName.toString() }.filterNot { it == "README" }
  }

  private fun String.toOutputFile(): String {
    val name = substringBeforeLast('.')
    val extension = substringAfterLast('.')
    return "${name}Summary.$extension"
  }

  private fun fileReader(path: String): FileReader {
    val dir =
      TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/javadoc/classes").toString()
    return FileReader("$dir/$path")
  }
}

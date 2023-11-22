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

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.FileReader

const val POINT_URL = "http://developer.android.com/reference/android/graphics/Point.html"

@RunWith(JUnit4::class)
class AndroidJavaDocExternalFilterTest {
  @get:Rule val projectRule = ProjectRule()
  private val project by lazy { projectRule.project }
  private val filter by lazy { AndroidJavaDocExternalFilter(project) }

  @Test
  fun oldFormat() {
    // Do not run tests on Windows (see b/37120270)
    if (SystemInfo.isWindows) return

    // Copied from SDK docs v23 rev 1
    val inputReader = fileReader("oldPoint.html")

    val builtDoc = buildString { filter.doBuildFromStream(POINT_URL, inputReader, this) }

    val expected = StringUtil.convertLineSeparators(fileReader("oldPointSummary.html").readText())
    assertThat(builtDoc).isEqualTo(expected)
  }

  @Test
  fun newFormat() {
    // Do not run tests on Windows (see b/37120270)
    if (SystemInfo.isWindows) return

    // Downloaded July 2016 with curl -o <output>
    // https://developer.android.com/reference/android/graphics/Point.html
    val inputReader = fileReader("newPoint.html")

    val builtDoc = buildString { filter.doBuildFromStream(POINT_URL, inputReader, this) }

    val expected = StringUtil.convertLineSeparators(fileReader("newPointSummary.html").readText())
    assertThat(builtDoc).isEqualTo(expected)
  }

  private fun fileReader(path: String): FileReader {
    val dir =
      TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData/javadoc/classes").toString()
    return FileReader("$dir/$path")
  }
}

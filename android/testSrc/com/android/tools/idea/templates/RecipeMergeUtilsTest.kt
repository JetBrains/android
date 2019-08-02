/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.google.common.truth.Truth.assertThat

import com.intellij.util.SystemProperties
import junit.framework.TestCase

private val LINE_SEPARATOR = SystemProperties.getLineSeparator()

class RecipeMergeUtilsTest : TestCase() {
  fun testMergeGradleSettingsFileEmptyDst() {
    val src = "include ':a'\r\ninclude ':b'\n"
    val dst = ""
    val expected = "include ':a'" + LINE_SEPARATOR +
                   "include ':b'" + LINE_SEPARATOR
    assertThat(mergeGradleSettingsFile(src, dst)).isEqualTo(expected)
  }

  fun testMergeGradleSettingsFileEmptySrc() {
    val src = ""
    val dst = "include ':a'" + LINE_SEPARATOR +
              "// Some comment" + LINE_SEPARATOR +
              "   " + LINE_SEPARATOR +
              "   " + LINE_SEPARATOR
    assertThat(mergeGradleSettingsFile(src, dst)).isEqualTo(dst)
  }

  fun testMergeGradleSettingsFileAlreadyInclude() {
    val src = "include ':b'" + LINE_SEPARATOR +
              "include ':c'" + LINE_SEPARATOR
    val dst = "include ':a'" + LINE_SEPARATOR +
              "// Some comment" + LINE_SEPARATOR
    val expected = dst + src
    assertThat(mergeGradleSettingsFile(src, dst)).isEqualTo(expected)
  }

  fun testMergeGradleSettingsFileNoNewLineComments() {
    val src = "include ':b'" + LINE_SEPARATOR +
              "include ':c'" + LINE_SEPARATOR
    val dst = "include ':a'" + LINE_SEPARATOR +
              "/* Some comment" + LINE_SEPARATOR +
              "  include ':notIncluded // This should not be used" + LINE_SEPARATOR +
              "*/"
    val expected = dst + LINE_SEPARATOR + src
    assertThat(mergeGradleSettingsFile(src, dst)).isEqualTo(expected)
  }

  fun testMergeGradleSettingsFileNoIncludeInSrc() {
    val src = "Not valid input"
    val dst = "include ':a'$LINE_SEPARATOR"
    try {
      mergeGradleSettingsFile(src, dst)
      fail("No exception was caused for non include line.")
    }
    catch (runTimeException: RuntimeException) {
      val expectedMessage = "When merging settings.gradle files, only include directives can be merged."
      assertThat(runTimeException).hasMessageThat().isEqualTo(expectedMessage)
    }
  }
}

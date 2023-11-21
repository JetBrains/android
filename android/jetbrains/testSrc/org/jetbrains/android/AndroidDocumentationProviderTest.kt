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

import com.google.common.truth.Truth
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.AndroidDocumentationProvider.MyDocExternalFilter
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader

class AndroidDocumentationProviderTest : AndroidTestCase() {
  @Throws(IOException::class)
  private fun readTestFile(path: String): String {
    val virtualFile = myFixture.copyFileToProject("javadoc/classes/$path", path)
    return VfsUtilCore.loadText(virtualFile)
  }

  @Throws(Exception::class)
  fun testExternalFilterOldFormat() {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return
    }

    // Copied from SDK docs v23 rev 1
    val input = readTestFile("oldPoint.html")
    val output = readTestFile("oldPointSummary.html")
    val url = "http://developer.android.com/reference/android/graphics/Point.html"
    checkFilter(url, input, output)
  }

  @Throws(Exception::class)
  fun testExternalFilterNewFormat() {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return
    }

    // Downloaded July 2016 with curl -o <output> https://developer.android.com/reference/android/graphics/Point.html
    val input = readTestFile("newPoint.html")
    val output = readTestFile("newPointSummary.html")
    val url = "http://developer.android.com/reference/android/graphics/Point.html"
    checkFilter(url, input, output)
  }

  @Throws(Exception::class)
  fun checkFilter(url: String?, input: String?, expected: String?) {
    val filter = MyDocExternalFilter(project)
    val builder = StringBuilder(1000)
    val reader = BufferedReader(StringReader(input))
    filter.doBuildFromStream(url!!, reader, builder)
    Truth.assertThat(builder.toString()).isEqualTo(StringUtil.convertLineSeparators(expected!!))
  }
}
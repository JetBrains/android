/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import kotlin.test.assertSame

class JsonPrettyPrinterTest {
  @Test
  fun prettyPrintAAB_r8json() {
    val observedText = JsonPrettyPrinter.prettyPrint(
      String(
        ApkTestUtils.getApkBytes(
          "/macrobenchmark-target-release.aab",
          "BUNDLE-METADATA/com.android.tools/r8.json"
        ), StandardCharsets.UTF_8)
    )
    assertEquals(observedText, ApkTestUtils.getResourceText("/expected-r8-json.txt"))
  }

  @Test
  fun prettyPrint_malformedJson() {
    val notJson = "[[[[[["
    val observedText = JsonPrettyPrinter.prettyPrint(notJson)
    assertSame(observedText, notJson) // when malformed, returns original
  }
}
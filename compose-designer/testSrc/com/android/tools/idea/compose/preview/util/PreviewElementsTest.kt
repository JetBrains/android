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
package com.android.tools.idea.compose.preview.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class PreviewElementsTest {
  @Test
  fun testPreviewConfigurationCleaner() {
    assertEquals(
      PreviewConfiguration.cleanAndGet(-120, null, 1, 1, 2f),
      PreviewConfiguration.cleanAndGet(-120, null, -2, -10, 2f))

    assertEquals(
      PreviewConfiguration.cleanAndGet(9000, null, MAX_WIDTH, MAX_HEIGHT, null),
      PreviewConfiguration.cleanAndGet(9000, null, 500000, 500000, 1f))

    assertEquals(
      PreviewConfiguration.cleanAndGet(12, null, 120, MAX_HEIGHT, null),
      PreviewConfiguration.cleanAndGet(12, null, 120, 500000, 1f))
  }

  @Test
  fun testValidXmlForPreview() {
    val previewsToCheck = listOf(
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null)),
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", "group1", true, true, null), null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null)),
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", "group1", true, true, "#000"),
                                   null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null)),
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", "group1", true, false, "#000"),
                                   null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null)))

    val factory = DocumentBuilderFactory.newDefaultInstance()
    val documentBuilder = factory.newDocumentBuilder()

    previewsToCheck
      .map { it.toPreviewXml().buildString() }
      .forEach {
        try {
          documentBuilder.parse(InputSource(StringReader(it)))
        }
        catch (t: Throwable) {
          fail(
            """
Failed to parse Preview XML

XML
----------------
$it

Exception
----------------
$t
""")
        }

      }
  }

  @Test
  fun testAffinity() {
    val composable0 = SinglePreviewElementInstance("composableMethodName",
      PreviewDisplaySettings("A name", null, false, false, null), null, null,
      PreviewConfiguration.cleanAndGet(null, null, null, null, null))

    // The same as composable0, just a different instance
    val composable0b = SinglePreviewElementInstance("composableMethodName",
                                                   PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null))

    // Same as composable0 but with different display settings
    val composable1 = SinglePreviewElementInstance("composableMethodName",
                                                  PreviewDisplaySettings("Different name", null, false, false, null), null, null,
                                                  PreviewConfiguration.cleanAndGet(null, null, null, null, null))

    // Same as composable0 but with different display settings
    val composable2 = SinglePreviewElementInstance("composableMethodName",
                                                   PreviewDisplaySettings("Different name", null, false, false, null), null, null,
                                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null))

    val result = listOf(composable2, composable1, composable0b)
      .shuffled()
      .sortedBy { modelAffinity(composable0, it) }
      .toTypedArray()

    // The more similar, the lower result of modelAffinity.
    assertArrayEquals(arrayOf(composable0b, composable1, composable2), result)
  }
}
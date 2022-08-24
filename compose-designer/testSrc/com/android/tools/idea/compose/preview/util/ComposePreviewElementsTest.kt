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

import com.android.tools.idea.compose.preview.ComposePreviewElementModelAdapter
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import org.xml.sax.InputSource

class ComposePreviewElementsTest {

  @Test
  fun testPreviewConfigurationCleaner() {
    assertEquals(
      PreviewConfiguration.cleanAndGet(-120, null, 1, 1, "", 2f, null, "", 2),
      PreviewConfiguration.cleanAndGet(-120, null, -2, -10, null, 2f, 0, null, 2)
    )

    assertEquals(
      PreviewConfiguration.cleanAndGet(
        9000,
        null,
        MAX_WIDTH,
        MAX_HEIGHT,
        null,
        null,
        null,
        "id:device"
      ),
      PreviewConfiguration.cleanAndGet(9000, null, 500000, 500000, null, 1f, 0, "id:device")
    )

    assertEquals(
      PreviewConfiguration.cleanAndGet(12, null, 120, MAX_HEIGHT, null, null, 123, null, -1),
      PreviewConfiguration.cleanAndGet(12, null, 120, 500000, null, 1f, 123, null, null)
    )
  }

  @Test
  fun testValidXmlForPreview() {
    val previewsToCheck =
      listOf(
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings("A name", null, false, false, null),
          null,
          null,
          PreviewConfiguration.cleanAndGet()
        ),
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings("A name", "group1", true, true, null),
          null,
          null,
          PreviewConfiguration.cleanAndGet()
        ),
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings("A name", "group1", true, true, "#000"),
          null,
          null,
          PreviewConfiguration.cleanAndGet()
        ),
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings("A name", "group1", true, false, "#000"),
          null,
          null,
          PreviewConfiguration.cleanAndGet()
        )
      )

    val factory = DocumentBuilderFactory.newDefaultInstance()
    val documentBuilder = factory.newDocumentBuilder()

    previewsToCheck.map { it.toPreviewXml().buildString() }.forEach {
      try {
        documentBuilder.parse(InputSource(StringReader(it)))
      } catch (t: Throwable) {
        fail(
          """
Failed to parse Preview XML

XML
----------------
$it

Exception
----------------
$t
"""
        )
      }
    }
  }

  @Test
  fun testAffinity() {
    val composable0 =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    // The same as composable0, just a different instance
    val composable0b =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    // Same as composable0 but with different display settings
    val composable1 =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings("Different name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    // Same as composable0 but with different display settings
    val composable2 =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings("Different name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    val adapter =
      object : ComposePreviewElementModelAdapter() {
        override fun toXml(previewElement: ComposePreviewElementInstance) = ""
        override fun createDataContext(previewElement: ComposePreviewElementInstance) =
          DataContext {}
      }

    val result =
      listOf(composable2, composable1, composable0b)
        .shuffled()
        .sortedBy { adapter.calcAffinity(it, composable0) }
        .toTypedArray()

    // The more similar, the lower result of modelAffinity.
    assertArrayEquals(arrayOf(composable0b, composable1, composable2), result)
  }

  @Test
  fun testEquals() {
    class TestComposePreviewElementInstance(
      override val instanceId: String,
      override val composableMethodFqn: String,
      override val displaySettings: PreviewDisplaySettings,
      override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
      override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
      override val configuration: PreviewConfiguration
    ) : ComposePreviewElementInstance()

    val composable0 =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    // The same as composable0, just a different instance
    val composable0b =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    // The same as composable0, but with a different name
    val composable1 =
      SingleComposePreviewElementInstance(
        "composableMethodName2",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    // The same as composable0, but with a different type
    val composable2 =
      TestComposePreviewElementInstance(
        "composableMethodName",
        "composableMethodName",
        PreviewDisplaySettings("A name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    // The same as composable2, but with a different display settings
    val composable3 =
      TestComposePreviewElementInstance(
        "composableMethodName",
        "composableMethodName",
        PreviewDisplaySettings("B name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )

    assertEquals(composable0, composable0b)
    assertNotEquals(composable0, composable1)
    assertNotEquals(composable0, composable2)
    assertNotEquals(composable2, composable3)
  }
}

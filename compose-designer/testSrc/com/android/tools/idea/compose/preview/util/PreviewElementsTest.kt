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

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.android.compose.ComposeLibraryNamespace
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(Parameterized::class)
class PreviewElementsTest(private val namespace: ComposeLibraryNamespace) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val namespaces = listOf(ComposeLibraryNamespace.ANDROIDX_UI, ComposeLibraryNamespace.ANDROIDX_COMPOSE)
  }

  @Test
  fun testPreviewConfigurationCleaner() {
    assertEquals(
      PreviewConfiguration.cleanAndGet(-120, null, 1, 1, 2f, null, ""),
      PreviewConfiguration.cleanAndGet(-120, null, -2, -10, 2f, 0, null))

    assertEquals(
      PreviewConfiguration.cleanAndGet(9000, null, MAX_WIDTH, MAX_HEIGHT, null, null, "id:device"),
      PreviewConfiguration.cleanAndGet(9000, null, 500000, 500000, 1f, 0, "id:device"))

    assertEquals(
      PreviewConfiguration.cleanAndGet(12, null, 120, MAX_HEIGHT, null, 123, null),
      PreviewConfiguration.cleanAndGet(12, null, 120, 500000, 1f, 123, null))
  }

  @Test
  fun testValidXmlForPreview() {
    val previewsToCheck = listOf(
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                   namespace),
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", "group1", true, true, null), null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                   namespace),
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", "group1", true, true, "#000"),
                                   null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                   namespace),
      SinglePreviewElementInstance("composableMethodName",
                                   PreviewDisplaySettings("A name", "group1", true, false, "#000"),
                                   null, null,
                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                   namespace))

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
                                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                   namespace)

    // The same as composable0, just a different instance
    val composable0b = SinglePreviewElementInstance("composableMethodName",
                                                    PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                                    PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                    namespace)

    // Same as composable0 but with different display settings
    val composable1 = SinglePreviewElementInstance("composableMethodName",
                                                   PreviewDisplaySettings("Different name", null, false, false, null), null, null,
                                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                   namespace)

    // Same as composable0 but with different display settings
    val composable2 = SinglePreviewElementInstance("composableMethodName",
                                                   PreviewDisplaySettings("Different name", null, false, false, null), null, null,
                                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                   namespace)

    val result = listOf(composable2, composable1, composable0b)
      .shuffled()
      .sortedBy { modelAffinity(composable0, it) }
      .toTypedArray()

    // The more similar, the lower result of modelAffinity.
    assertArrayEquals(arrayOf(composable0b, composable1, composable2), result)
  }

  @Test
  fun testEquals() {
    class TestPreviewElementInstance(override val instanceId: String,
                                     override val composableMethodFqn: String,
                                     override val displaySettings: PreviewDisplaySettings,
                                     override val previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?,
                                     override val previewBodyPsi: SmartPsiElementPointer<PsiElement>?,
                                     override val configuration: PreviewConfiguration,
                                     override val composeLibraryNamespace: ComposeLibraryNamespace) : PreviewElementInstance()

    val composable0 = SinglePreviewElementInstance("composableMethodName",
                                                   PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                   namespace)

    // The same as composable0, just a different instance
    val composable0b = SinglePreviewElementInstance("composableMethodName",
                                                    PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                                    PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                    namespace)

    // The same as composable0, but with a different name
    val composable1 = SinglePreviewElementInstance("composableMethodName2",
                                                   PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                                   PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                   namespace)

    // The same as composable0, but with a different type
    val composable2 = TestPreviewElementInstance("composableMethodName", "composableMethodName",
                                                 PreviewDisplaySettings("A name", null, false, false, null), null, null,
                                                 PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                 namespace)

    // The same as composable2, but with a different display settings
    val composable3 = TestPreviewElementInstance("composableMethodName", "composableMethodName",
                                                 PreviewDisplaySettings("B name", null, false, false, null), null, null,
                                                 PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null),
                                                 namespace)

    assertEquals(composable0, composable0b)
    assertNotEquals(composable0, composable1)
    assertNotEquals(composable0, composable2)
    assertNotEquals(composable2, composable3)
  }
}
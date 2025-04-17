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
package com.android.tools.preview

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import org.xml.sax.InputSource

class ComposePreviewElementTest {

  @Test
  fun testValidXmlForPreview() {
    val previewsToCheck =
      listOf(
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings(
            name = "A name",
            baseName = "A base name",
            parameterName = null,
            group = null,
            showDecoration = false,
            showBackground = false,
            backgroundColor = null,
          ),
          null,
          null,
          PreviewConfiguration.cleanAndGet(),
        ),
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings(
            name = "A name",
            baseName = "A base name",
            parameterName = null,
            group = "group1",
            showDecoration = true,
            showBackground = true,
            backgroundColor = null,
          ),
          null,
          null,
          PreviewConfiguration.cleanAndGet(),
        ),
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings(
            name = "A name",
            baseName = "A base name",
            parameterName = null,
            group = "group1",
            showDecoration = true,
            showBackground = true,
            backgroundColor = "#000",
          ),
          null,
          null,
          PreviewConfiguration.cleanAndGet(),
        ),
        SingleComposePreviewElementInstance(
          "composableMethodName",
          PreviewDisplaySettings(
            name = "A name",
            baseName = "A base name",
            parameterName = null,
            group = "group1",
            showDecoration = true,
            showBackground = false,
            backgroundColor = "#000",
          ),
          null,
          null,
          PreviewConfiguration.cleanAndGet(),
        ),
      )

    val factory = DocumentBuilderFactory.newDefaultInstance()
    val documentBuilder = factory.newDocumentBuilder()

    previewsToCheck
      .map { it.toPreviewXml().buildString() }
      .forEach {
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
  fun testEquals() {
    class TestComposePreviewElementInstance(
      override val instanceId: String,
      override val methodFqn: String,
      override val displaySettings: PreviewDisplaySettings,
      override val previewElementDefinition: Unit?,
      override val previewBody: Unit?,
      override val configuration: PreviewConfiguration,
    ) : ComposePreviewElementInstance<Unit>() {
      override var hasAnimations = false

      override fun createDerivedInstance(
        displaySettings: PreviewDisplaySettings,
        config: PreviewConfiguration,
      ) =
        TestComposePreviewElementInstance(
          instanceId = instanceId,
          methodFqn = methodFqn,
          displaySettings = displaySettings,
          previewElementDefinition = previewElementDefinition,
          previewBody = previewBody,
          configuration = config,
        )
    }

    val composable0 =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings(
          name = "A name",
          baseName = "A base name",
          parameterName = null,
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
        ),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // The same as composable0, just a different instance
    val composable0b =
      SingleComposePreviewElementInstance(
        "composableMethodName",
        PreviewDisplaySettings(
          name = "A name",
          baseName = "A base name",
          parameterName = null,
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
        ),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // The same as composable0, but with a different name
    val composable1 =
      SingleComposePreviewElementInstance(
        "composableMethodName2",
        PreviewDisplaySettings(
          name = "A name",
          baseName = "A base name",
          parameterName = null,
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
        ),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // The same as composable0, but with a different type
    val composable2 =
      TestComposePreviewElementInstance(
        "composableMethodName",
        "composableMethodName",
        PreviewDisplaySettings(
          name = "A name",
          baseName = "A base name",
          parameterName = null,
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
        ),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    // The same as composable2, but with a different display settings
    val composable3 =
      TestComposePreviewElementInstance(
        "composableMethodName",
        "composableMethodName",
        PreviewDisplaySettings(
          name = "B name",
          baseName = "B base name",
          parameterName = null,
          group = null,
          showDecoration = false,
          showBackground = false,
          backgroundColor = null,
        ),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )

    assertEquals(composable0, composable0b)
    assertNotEquals(composable0, composable1)
    assertNotEquals(composable0, composable2)
    assertNotEquals(composable2, composable3)
  }
}

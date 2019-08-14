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
package com.android.tools.idea.compose.preview

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UastVisitor

class MethodPreviewElementFinderTest : ComposeLightCodeInsightFixtureTestCase() {
  fun testFindPreviewAnnotations() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.tools.preview.Preview
      import com.android.tools.preview.Configuration
      import androidx.compose.Compose

      @Compose
      fun Preview1() {
        Preview() {
        }
      }

      @Compose
      fun Preview2() {
        Preview(name = "preview2", configuration = Configuration(apiLevel = 12)) {
        }
      }

      @Compose
      fun Preview3() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2)) {
        }
      }

      @Compose
      fun NoPreviewCompose() {

      }
    """.trimIndent())

    val elements = MethodPreviewElementFinder.findPreviewMethods(composeTest.toUElement() as UFile)
    assertEquals(3, elements.size)
    elements.single { it.name == "preview2" }.let {
      assertEquals("preview2", it.name)
      assertEquals(12, it.configuration.apiLevel)
      assertNull(it.configuration.theme)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
    }

    elements.single { it.name == "preview3" }.let {
      assertEquals("preview3", it.name)
      assertEquals(1, it.configuration.width)
      assertEquals(2, it.configuration.height)
    }

    elements.single { it.name.isEmpty()}.let {
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
    }
  }
}
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
package org.jetbrains.android.completion

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.internal.DumpLookupElementWeights
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AndroidRestrictToCompletionWeigherTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().withKotlin()
  @get:Rule val flagRule = FlagRule(StudioFlags.RESTRICT_TO_COMPLETION_WEIGHER, true)

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    // Stub restrict to annotation
    fixture.addFileToProject(
      "src/androidx/annotation/RestrictTo.kt",
      // language=kotlin
      """
      package androidx.annotation

      @Target({ANNOTATION_TYPE,TYPE,METHOD,CONSTRUCTOR,FIELD,PACKAGE})
      public annotation class RestrictTo(
        vararg val value: Scope
      ) {
        enum class Scope {
          LIBRARY,
          LIBRARY_GROUP,
          LIBRARY_GROUP_PREFIX,
        }
      }
      """,
    )

    fixture.addFileToProject(
      "src/androidx/library/internal/ClassLevel.kt",
      // language=kotlin
      """
        package androidx.library.internal

        import androidx.annotation.RestrictTo

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        class ClassLevel {
          inner class Inner
        }
        """,
    )
    fixture.addFileToProject(
      "src/androidx/library/public/ClassLevel.kt",
      // language=kotlin
      """
        package androidx.library.public

        import androidx.annotation.RestrictTo

        class ClassLevel {
          inner class Inner
        }
        """,
    )
    fixture.addFileToProject(
      "src/androidx/library/public/FileLevel.kt",
      // language=kotlin
      """
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        package androidx.library.public

        class FileLevel
        fun fileLevelFunction() {}
        """,
    )
    fixture.addFileToProject(
      "src/androidx/library/internal/FileLevel.kt",
      // language=kotlin
      """
        @RestrictTo(RestrictTo.Scope.LIBRARY)
        package androidx.library.internal

        import androidx.annotation.RestrictTo

        class FileLevel
        fun fileLevelFunction() {}
        """,
    )
  }

  @Test
  fun testLookupElementOrder_classNameRestrictedAtClassLevel() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
        package com.example

        fun main() {
            ClassLe$caret
        }
      """
        .trimIndent(),
    )

    fixture.completeBasic()
    // Ensure that the weigher is active
    Truth.assertThat(
        DumpLookupElementWeights.getLookupElementWeights(fixture.lookup as LookupImpl, true)
          .filter { it.contains("restrictTo=") }
      )
      .hasSize(2)
    Truth.assertThat(fixture.renderedLookupElements)
      .containsExactly(
        "ClassLevel (androidx.library.public)",
        "ClassLevel (androidx.library.internal)",
      )
      .inOrder()
  }

  @Test
  fun testLookupElementOrder_classNameRestrictedAtFileLevel() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
        package com.example

        fun main() {
            FileL$caret
        }
      """
        .trimIndent(),
    )

    fixture.completeBasic()
    Truth.assertThat(fixture.renderedLookupElements)
      .containsExactly(
        "FileLevel (androidx.library.public)",
        "FileLevel (androidx.library.internal)",
      )
      .inOrder()
  }

  @Test
  fun testLookupElementOrder_methodNameRestrictedAtFileLevel() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
        package com.example

        fun main() {
            fileLevelF$caret
        }
      """
        .trimIndent(),
    )

    fixture.completeBasic()
    Truth.assertThat(fixture.renderedLookupElements)
      .containsExactly(
        "fileLevelFunction() (androidx.library.public) Unit",
        "fileLevelFunction() (androidx.library.internal) Unit",
      )
      .inOrder()
  }

  @Test
  fun testLookupElementOrder_innerClassRestrictedAtClassLevel() {
    fixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
        package com.example

        fun main() {
            Inne$caret
        }
      """
        .trimIndent(),
    )

    fixture.completeBasic()
    Truth.assertThat(fixture.renderedLookupElements)
      .containsExactly(
        "Inner (androidx.library.public.ClassLevel)",
        "Inner (androidx.library.internal.ClassLevel)",
      )
      .inOrder()
  }

  private val CodeInsightTestFixture.renderedLookupElements: Collection<String>
    get() {
      return runReadAction {
          lookupElements
            .orEmpty()
            .map { lookupElement ->
              LookupElementPresentation().apply { lookupElement.renderElement(this) }
            }
            .map { presentation ->
              buildString {
                append(presentation.itemText)
                append(presentation.tailText)
                if (!presentation.typeText.isNullOrEmpty()) {
                  append(" ")
                  append(presentation.typeText)
                }
              }
            }
        }
        .filter { it.contains("androidx.") }
    }
}

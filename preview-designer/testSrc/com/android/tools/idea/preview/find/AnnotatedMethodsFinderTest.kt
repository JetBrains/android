/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.find

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.openapi.application.runReadAction
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.uast.UMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun identity(methods: List<UMethod>) = methods.asFlow()

private fun nameLetters(methods: List<UMethod>) = methods.flatMap { it.name.toList() }.asFlow()

class AnnotatedMethodsFinderTest {

  @get:Rule val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Test
  fun `test findAnnotations`() {
    val sourceFile =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFile.kt",
        // language=kotlin
        """
        @MyAnnotationA
        fun FooA1() { }

        fun FooC() { }

        @MyAnnotationA
        fun FooA2() { }

        @MyAnnotationB
        fun FooB() { }
        """
          .trimIndent(),
      )

    assertEquals(0, CacheKeysManager.getInstance(project).map().size)
    assertEquals(
      2,
      runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA").size },
    )
    assertEquals(1, CacheKeysManager.getInstance(project).map().size)
    assertEquals(
      2,
      runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA").size },
    )
    // Check that call with the same args combination does not create a new key and reuses the
    // cache:
    assertEquals(1, CacheKeysManager.getInstance(project).map().size)
    assertEquals(
      1,
      runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationB").size },
    )
    assertEquals(2, CacheKeysManager.getInstance(project).map().size)
    assertEquals(
      0,
      runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationC").size },
    )
    assertEquals(3, CacheKeysManager.getInstance(project).map().size)
  }

  @Test
  fun `test findAnnotatedMethodsValues`() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotationA.kt",
      // language=kotlin
      """
        package com.android.annotations

        annotation class MyAnnotationA
        """
        .trimIndent(),
    )

    val sourceFile =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFile.kt",
        // language=kotlin
        """
        package com.android.test

        import com.android.annotations.MyAnnotationA

        @MyAnnotationA
        fun abcde() { }

        fun FooC() { }

        @MyAnnotationA
        fun fghia() { }
        """
          .trimIndent(),
      )

    val nLetters = 10 // There are 10 letters in "abcde and "fghia" altogether
    assertEquals(0, CacheKeysManager.getInstance(project).map().size)
    assertEquals(
      nLetters,
      findAnnotatedMethodsValues(
          project,
          sourceFile.virtualFile,
          "com.android.annotations.MyAnnotationA",
          "MyAnnotationA",
          toValues = ::nameLetters,
        )
        .size,
    )
    assertTrue(
      "Unexpectedly no new cache keys",
      CacheKeysManager.getInstance(project).map().size > 0,
    )
    val cacheKeys = CacheKeysManager.getInstance(project).map().size
    assertEquals(
      nLetters,
      findAnnotatedMethodsValues(
          project,
          sourceFile.virtualFile,
          "com.android.annotations.MyAnnotationA",
          "MyAnnotationA",
          toValues = ::nameLetters,
        )
        .size,
    )
    // Check that call with the same args combination does not create new keys and reuses the cache:
    assertEquals(cacheKeys, CacheKeysManager.getInstance(project).map().size)
    assertEquals(
      2,
      findAnnotatedMethodsValues(
          project,
          sourceFile.virtualFile,
          "com.android.annotations.MyAnnotationA",
          "MyAnnotationA",
          toValues = ::identity,
        )
        .size,
    )
    assertTrue(
      "Unexpectedly no new cache keys",
      cacheKeys < CacheKeysManager.getInstance(project).map().size,
    )
    assertEquals(
      0,
      findAnnotatedMethodsValues(
          project,
          sourceFile.virtualFile,
          "com.android.annotations.MyAnnotationB",
          "MyAnnotationB",
          toValues = ::identity,
        )
        .size,
    )
  }

  @Test
  fun `findAnnotation requires read lock`() {
    val sourceFile =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFile.kt",
        // language=kotlin
        """
        @MyAnnotationA
        fun FooA1() { }
        """
          .trimIndent(),
      )
    val withoutReadLock = runCatching {
      findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA")
    }
    val withReadLock = runCatching {
      runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA") }
    }
    assertTrue(withoutReadLock.isFailure)
    assertTrue(withReadLock.isSuccess)
  }
}

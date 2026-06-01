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
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
    assertEquals(2, runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA").size })
    assertEquals(1, CacheKeysManager.getInstance(project).map().size)
    assertEquals(2, runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA").size })
    // Check that call with the same args combination does not create a new key and reuses the
    // cache:
    assertEquals(1, CacheKeysManager.getInstance(project).map().size)
    assertEquals(1, runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationB").size })
    assertEquals(2, CacheKeysManager.getInstance(project).map().size)
    assertEquals(0, runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationC").size })
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

    assertEquals(0, CacheKeysManager.getInstance(project).map().size)
    assertEquals(2, findAnnotatedMethods(project, sourceFile.virtualFile, "com.android.annotations.MyAnnotationA", "MyAnnotationA").size)
    assertTrue("Unexpectedly no new cache keys", CacheKeysManager.getInstance(project).map().size > 0)
    val cacheKeys = CacheKeysManager.getInstance(project).map().size
    assertEquals(2, findAnnotatedMethods(project, sourceFile.virtualFile, "com.android.annotations.MyAnnotationA", "MyAnnotationA").size)
    // Check that call with the same args combination does not create new keys and reuses the cache:
    assertEquals(cacheKeys, CacheKeysManager.getInstance(project).map().size)

    // A different parameter combination should add a new cache key
    assertEquals(0, findAnnotatedMethods(project, sourceFile.virtualFile, "com.android.annotations.MyAnnotationB", "MyAnnotationB").size)
    assertTrue("Unexpectedly no new cache keys", cacheKeys < CacheKeysManager.getInstance(project).map().size)
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
    val withoutReadLock = runCatching { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA") }
    val withReadLock = runCatching { runReadAction { findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA") } }
    assertTrue(withoutReadLock.isFailure)
    assertTrue(withReadLock.isSuccess)
  }

  @Test
  fun `test getContainingUMethodAnnotatedWith works with aliased annotations`() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotationA.kt",
      // language=kotlin
      """
      package com.android.annotations

      annotation class MyAnnotationA
      """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyComposable.kt",
      // language=kotlin
      """
      package com.android.annotations

      annotation class MyComposable
      """
        .trimIndent(),
    )

    val sourceFile =
      fixture.addFileToProjectAndInvalidate(
        "com/android/test/SourceFile.kt",
        // language=kotlin
        """
        package com.android.test

        import com.android.annotations.MyAnnotationA as MyAnnotationAlias
        import com.android.annotations.MyComposable as MyComposableAlias

        @MyAnnotationAlias
        @MyComposableAlias
        fun funWithAlias() { }
        """
          .trimIndent(),
      )

    // Check that we can find the annotated method even when both the indexed annotation and the required annotation are aliased.
    // When both are aliased, the shortAnnotationName to search for is "MyAnnotationAlias", and the annotationFqn is
    // "com.android.annotations.MyComposable".
    val methods = findAnnotatedMethods(project, sourceFile.virtualFile, "com.android.annotations.MyComposable", "MyAnnotationAlias")

    assertEquals(1, methods.size)
    assertEquals("funWithAlias", readAction { methods.first().name })
  }

  @Test
  fun `test getContainingUMethodAnnotatedWith handles shadowed imports`() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotationA.kt",
      // language=kotlin
      """
      package com.android.annotations

      annotation class MyAnnotationA
      """
        .trimIndent(),
    )
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyComposable.kt",
      // language=kotlin
      """
      package com.android.annotations

      annotation class MyComposable
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
        import com.android.annotations.MyComposable

        class Outer {
            // Shadow the imported MyComposable with a nested class of the same name
            annotation class MyComposable

            @MyAnnotationA
            @MyComposable
            fun funWithShadowedImport() { }
        }
        """
          .trimIndent(),
      )

    // Since the imported MyComposable is shadowed by a local annotation,
    // the required annotation "com.android.annotations.MyComposable" is NOT actually present on the function.
    // So the finder must return empty list.
    val methods = findAnnotatedMethods(project, sourceFile.virtualFile, "com.android.annotations.MyComposable", "MyAnnotationA")

    assertEquals(0, methods.size)
  }
}

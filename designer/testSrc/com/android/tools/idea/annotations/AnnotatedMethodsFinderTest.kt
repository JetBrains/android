/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.annotations

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun identity(methods: List<UMethod>) = methods.asSequence()

private fun nameLetters(methods: List<UMethod>) = methods.asSequence().flatMap { it.name.asSequence() }

class AnnotatedMethodsFinderTest {

  @get:Rule
  val projectRule: AndroidProjectRule = AndroidProjectRule.inMemory()

  private val project get() = projectRule.project
  private val fixture get() = projectRule.fixture

  @Before
  fun setUp() {
    CacheKeysManager.map().clear()
  }

  @Test
  fun `test hasAnnotations`() {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotation.kt",
      // language=kotlin
      """
        package com.android.annotations

        annotation class MyAnnotation
        """.trimIndent())
    val sourceFile = fixture.addFileToProjectAndInvalidate(
      "com/android/test/SourceFile.kt",
      // language=kotlin
      """
        package com.android.test

        import com.android.annotations.MyAnnotation

        @MyAnnotation
        fun Foo1() { }

        fun Foo2() { }
        """.trimIndent())

    assertEquals(0, CacheKeysManager.map().size)
    assertTrue(hasAnnotations(project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotation"), "MyAnnotation"))
    assertEquals(1, CacheKeysManager.map().size)
    assertTrue(hasAnnotations(project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotation"), "MyAnnotation"))
    // Check that call with the same args combination does not create a new key and reuses the cache:
    assertEquals(1, CacheKeysManager.map().size)
    assertFalse(hasAnnotations(project, sourceFile.virtualFile, setOf("com.android.annotations.IDoNotExist"), "IDoNotExist"))
    assertEquals(2, CacheKeysManager.map().size)
  }

  @Test
  fun `test hasAnnotations dumb mode`() {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotation.kt",
      // language=kotlin
      """
        package com.android.annotations

        annotation class MyAnnotation
        """.trimIndent())
    val sourceFile = fixture.addFileToProjectAndInvalidate(
      "com/android/test/SourceFile.kt",
      // language=kotlin
      """
        package com.android.test

        import com.android.annotations.MyAnnotation

        @MyAnnotation
        fun Foo1() { }
        """.trimIndent())

    val testDumbService = TestDumbService(fixture.project)
    fixture.project.replaceService(DumbService::class.java, testDumbService, project)

    assertFalse(hasAnnotations(project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotation"), "MyAnnotation"))

    testDumbService.dumbMode = false

    assertTrue(hasAnnotations(project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotation"), "MyAnnotation"))
  }

  @Test
  fun `test findAnnotations`() {
    val sourceFile = fixture.addFileToProjectAndInvalidate(
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
        """.trimIndent())

    assertEquals(0, CacheKeysManager.map().size)
    assertEquals(2, findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA").size)
    assertEquals(1, CacheKeysManager.map().size)
    assertEquals(2, findAnnotations(project, sourceFile.virtualFile, "MyAnnotationA").size)
    // Check that call with the same args combination does not create a new key and reuses the cache:
    assertEquals(1, CacheKeysManager.map().size)
    assertEquals(1, findAnnotations(project, sourceFile.virtualFile, "MyAnnotationB").size)
    assertEquals(2, CacheKeysManager.map().size)
    assertEquals(0, findAnnotations(project, sourceFile.virtualFile, "MyAnnotationC").size)
    assertEquals(3, CacheKeysManager.map().size)
  }

  @Test
  fun `test findAnnotatedMethodsValues`() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotationA.kt",
      // language=kotlin
      """
        package com.android.annotations

        annotation class MyAnnotationA
        """.trimIndent())

    val sourceFile = fixture.addFileToProjectAndInvalidate(
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
        """.trimIndent())

    val nLetters = 10 // There are 10 letters in "abcde and "fghia" altogether
    assertEquals(0, CacheKeysManager.map().size)
    assertEquals(
      nLetters,
      findAnnotatedMethodsValues(
        project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotationA"), "MyAnnotationA", toValues = ::nameLetters).size)
    assertTrue("Unexpectedly no new cache keys", CacheKeysManager.map().size > 0)
    val cacheKeys = CacheKeysManager.map().size
    assertEquals(
      nLetters,
      findAnnotatedMethodsValues(
        project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotationA"), "MyAnnotationA", toValues = ::nameLetters).size)
    // Check that call with the same args combination does not create new keys and reuses the cache:
    assertEquals(cacheKeys, CacheKeysManager.map().size)
    assertEquals(
      2,
      findAnnotatedMethodsValues(
        project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotationA"), "MyAnnotationA", toValues = ::identity).size)
    assertTrue("Unexpectedly no new cache keys", cacheKeys < CacheKeysManager.map().size)
    assertEquals(
      0,
      findAnnotatedMethodsValues(
        project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotationB"),"MyAnnotationB", toValues = ::identity).size)
  }

  @Test
  fun `test hasAppliedAnnotations with filter`() {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotationA.kt",
      // language=kotlin
      """
        package com.android.annotations

        object Surfaces {
          const val SURFACE1 = "foo"
          const val SURFACE2 = "bar"
        }

        annotation class MyAnnotationA(param1: String)
        """.trimIndent())

    val sourceFile = fixture.addFileToProjectAndInvalidate(
      "com/android/test/SourceFile.kt",
      // language=kotlin
      """
        package com.android.test

        import com.android.annotations.MyAnnotationA
        import com.android.annotations.Surfaces

        @MyAnnotationA(Surfaces.SURFACE1)
        fun abcde() { }
        """.trimIndent())

    val res = hasAnnotations(project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotationA"), "MyAnnotationA") {
      ReadAction.compute<Boolean, Throwable> {
        it.psiOrParent.toUElementOfType<UAnnotation>()?.findAttributeValue("param1")?.evaluate() == "foo"
      }
    }

    assertTrue(res)
  }

  @Test
  fun `test findAnnotatedMethodsValues with filter`() = runBlocking {
    fixture.addFileToProjectAndInvalidate(
      "com/android/annotations/MyAnnotationA.kt",
      // language=kotlin
      """
        package com.android.annotations

        object Surfaces {
          const val SURFACE1 = "foo"
          const val SURFACE2 = "bar"
        }

        annotation class MyAnnotationA(param1: String)
        """.trimIndent())

    val sourceFile = fixture.addFileToProjectAndInvalidate(
      "com/android/test/SourceFile.kt",
      // language=kotlin
      """
        package com.android.test

        import com.android.annotations.MyAnnotationA
        import com.android.annotations.Surfaces

        @MyAnnotationA(Surfaces.SURFACE2)
        fun abcde() { }

        fun FooC() { }

        @MyAnnotationA(Surfaces.SURFACE1)
        fun fghia() { }
        """.trimIndent())

    val fooFilter: (UAnnotation) -> Boolean = {
      ReadAction.compute<Boolean, Throwable> {
        it.findAttributeValue("param1")?.evaluate() == "foo"
      }
    }

    assertEquals(0, CacheKeysManager.map().size)
    assertEquals(
      1,
      findAnnotatedMethodsValues(
        project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotationA"), "MyAnnotationA", fooFilter, ::identity).size)
    assertTrue("Unexpectedly no new cache keys", CacheKeysManager.map().size > 0)
    val cacheKeys = CacheKeysManager.map().size
    assertEquals(
      1,
      findAnnotatedMethodsValues(
        project, sourceFile.virtualFile, setOf("com.android.annotations.MyAnnotationA"),"MyAnnotationA", fooFilter, ::identity).size)
    // Check that call with the same args combination does not create new keys and reuses the cache:
    assertEquals(cacheKeys, CacheKeysManager.map().size)
  }
}

/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.compileIr
import com.android.tools.idea.testing.AndroidProjectRule
import junit.framework.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.fail

@RunWith(JUnit4::class)
class BasicCompileTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)

    // Create mocks for the kotlin.jvm to avoid having to bring in the whole dependency
    projectRule.fixture.configureByText("JvmName.kt", "package kotlin.jvm\n" +
                                                      "@Target(AnnotationTarget.FILE)\n" +
                                                      "public annotation class JvmName(val name: String)\n")

    projectRule.fixture.configureByText("JvmMultifileClass.kt", "package kotlin.jvm\n" +
                                                                "@Target(AnnotationTarget.FILE)\n" +
                                                                "public annotation class JvmMultifileClass()")
  }

  @Test
  fun simpleChange() {
    val original = """
      fun foo() = ""
      fun bar() = 1 
    """

    val cache = initialCache(mapOf("A.kt" to original))
    var next = projectRule.fixture.configureByText("A.kt", """
      fun foo() = "I am foo"
      fun bar() = 1 
    """)

    var output = compile(next, cache)
    val returnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)

    // Replace the return value of foo.
    next = projectRule.fixture.configureByText("A.kt", """
      fun foo() = "I am not foo"
      fun bar() = 1 
    """)

    // Re-compile A.kt like how live edit work.
    output = compile(next, cache)
    var leReturnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am not foo", leReturnedValue)
  }

  @Test
  fun recoverableErrors() {
    try {
      val file = projectRule.fixture.configureByText("RecoverableError.kt", """
        fun recoverableError() { "a".toString() } }
      """)
      compile(file)
      Assert.fail("RecoverableError.kt contains a lexical error and should not be updated by Live Edit")
    } catch (e: LiveEditUpdateException) {
      Assert.assertEquals("Expecting a top level declaration", e.message)
    }
  }

  @Test
  fun inlineTarget() {
    projectRule.fixture.configureByText("InlineTarget.kt", "inline fun it1() = \"I am foo\"")
    val original = "fun callInlineTarget() = \"\""

    val cache = initialCache(mapOf("CallInlineTarget.kt" to original))
    val next = projectRule.fixture.configureByText("CallInlineTarget.kt", "fun callInlineTarget() = it1()")

    val output = compile(next, cache)
    val returnedValue = invokeStatic("callInlineTarget", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)
  }

  @Test
  fun lambdaChange() {
    val original = """
      fun hasLambda() : String {
        var capture = "x"
        var lambda = { capture = "a" }
        lambda()
        return capture
      }
    """

    val cache = initialCache(mapOf("HasLambda.kt" to original))
    val next = projectRule.fixture.configureByText(
      "HasLambda.kt", """
        fun hasLambda() : String {
          var capture = "z"
          var lambda = { capture = "y" }
          lambda()
          return capture
        }
      """)
    val output = compile(next, cache)
    Assert.assertEquals(1, output.supportClassesMap.size)
    val returnedValue = invokeStatic("hasLambda", loadClass(output))
    Assert.assertEquals("y", returnedValue)
  }

  @Test
  fun samChange() {
    val file = projectRule.fixture.configureByText("HasSAM.kt", """
      fun interface A {
        fun go(): Int
      }
      fun hasSam(): Int {
        var test = A { 100 }
        return test.go()
      }
    """)
    val output = compile(file)
    Assert.assertEquals(1, output.supportClassesMap.size)
    // Can't test invocation of the method since the functional interface "A" is not loaded.
  }

  @Test
  fun crossFileReference() {
    projectRule.fixture.configureByText("A.kt", "fun foo() = \"\"")
    val fileCallA = projectRule.fixture.configureByText("CallA.kt", "fun callA() = foo()")
    compile(fileCallA)
  }

  @Test
  fun internalVar() {
    val cache = initialCache(mapOf("HasInternalVar.kt" to """
      internal var x = 0
      fun getNum() = x
    """))

    val next = projectRule.fixture.configureByText("HasInternalVar.kt", """
     internal var x = 0
     fun getNum() = x + 1
    """)

    val output = compile(next, cache)
    Assert.assertTrue(output.classesMap["HasInternalVarKt"]!!.isNotEmpty())
    val returnedValue = invokeStatic("getNum", loadClass(output))
    Assert.assertEquals(1, returnedValue)
  }

  @Test
  @Ignore // CLASS DIFFER ONLY
  fun publicInlineFunction() {
    try {
      val cache = initialCache(mapOf("HasPublicInline.kt" to "public inline fun publicInlineFun() = 1"))
      val next = projectRule.fixture.configureByText("HasPublicInline.kt", "public inline fun publicInlineFun() = 2")
      compile(next, cache)
      Assert.fail("Expecting an exception thrown.")
    }
    catch (e: LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.NON_PRIVATE_INLINE_FUNCTION, e.error)
    }
  }

  @Test
  @Ignore // CLASS DIFFER ONLY
  fun renamedFile() {
    val cache = initialCache(mapOf("RenamedFile.kt" to """
      @file:kotlin.jvm.JvmName("CustomJvmName")
      @file:kotlin.jvm.JvmMultifileClass
      fun T() {}
    """))

    val next = projectRule.fixture.configureByText("RenamedFile.kt", """
      @file:kotlin.jvm.JvmName("CustomJvmName")
      @file:kotlin.jvm.JvmMultifileClass
      fun T() { val x = 0 }
    """)

    val output = compile(next, cache)
    Assert.assertNotNull(output.irClasses.singleOrNull { it.name == "CustomJvmName" }) // CustomJvmName.class doesn't change
    Assert.assertTrue(output.classesMap["CustomJvmName__RenamedFileKt"]!!.isNotEmpty())
  }

  @Test
  @Ignore // CLASS DIFFER ONLY
  fun modifyConstructor() {
    val cache = initialCache(mapOf("ModifyConstructor.kt" to """
      class MyClass() {
        init {
          val x = 0
        }
      }
    """.trimIndent()))

    val next = projectRule.fixture.configureByText("ModifyConstructor.kt", """
      class MyClass() {
        init {
          val x = 999
        }
      }
    """.trimIndent())

    try {
      compile(next, cache)
      fail("Expected exception due to modified constructor")
    } catch (e: LiveEditUpdateException) {
      assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, e.error)
      assertContains(e.details, "MyClass()")
    }
  }

  @Test
  @Ignore // CLASS DIFFER ONLY
  fun modifyStaticInit() {
    val cache = initialCache(mapOf("ModifyStaticInit.kt" to """
      val x = 1
    """.trimIndent()))

    val next = projectRule.fixture.configureByText("ModifyStaticInit.kt", """
      val x = 2
    """.trimIndent())

    try {
      compile(next, cache)
      fail("Expected exception due to modified static initializer")
    } catch (e: LiveEditUpdateException) {
      assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, e.error)
      assertContains(e.details, "static initializer")
    }
  }

  private fun initialCache(files: Map<String, String>): MutableIrClassCache {
    val cache = MutableIrClassCache()
    files.map { projectRule.compileIr(it.value, it.key) }.forEach { cache.update(it) }
    return cache
  }
}

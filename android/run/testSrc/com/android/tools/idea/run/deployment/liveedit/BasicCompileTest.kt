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

import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileIr
import com.android.tools.idea.run.deployment.liveedit.analysis.disableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.enableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.initialCache
import com.android.tools.idea.run.deployment.liveedit.analysis.modifyKtFile
import com.android.tools.idea.testing.AndroidProjectRule
import org.jetbrains.kotlin.psi.KtFile
import org.junit.After
import org.junit.Assert
import org.junit.Before
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
    disableLiveEdit()

    // Create mocks for the kotlin.jvm to avoid having to bring in the whole dependency
    projectRule.fixture.configureByText("JvmName.kt", "package kotlin.jvm\n" +
                                                      "@Target(AnnotationTarget.FILE)\n" +
                                                      "public annotation class JvmName(val name: String)\n")

    projectRule.fixture.configureByText("JvmMultifileClass.kt", "package kotlin.jvm\n" +
                                                                "@Target(AnnotationTarget.FILE)\n" +
                                                                "public annotation class JvmMultifileClass()")
  }

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun simpleChange() {
    val file = projectRule.createKtFile("A.kt", """
      fun foo() = ""
      fun bar() = 1 
    """)

    val cache = projectRule.initialCache(listOf(file))
    projectRule.modifyKtFile(file, """
      fun foo() = "I am foo"
      fun bar() = 1 
    """)

    var output = compile(file, cache)
    val returnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)

    // Replace the return value of foo.
    projectRule.modifyKtFile(file, """
      fun foo() = "I am not foo"
      fun bar() = 1 
    """)

    // Re-compile A.kt like how live edit work.
    output = compile(file, cache)
    var leReturnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am not foo", leReturnedValue)
  }

  @Test
  fun recoverableErrors() {
    try {
      val file = projectRule.createKtFile("RecoverableError.kt", """
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
    val inlined = projectRule.createKtFile("InlineTarget.kt", "inline fun it1() = \"I am foo\"")
    val file = projectRule.createKtFile("CallInlineTarget.kt", "fun callInlineTarget() = \"\"")

    val cache = projectRule.initialCache(listOf(inlined, file))
    projectRule.modifyKtFile(file, "fun callInlineTarget() = it1()")

    val output = compile(file, cache)
    val returnedValue = invokeStatic("callInlineTarget", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)
  }

  @Test
  fun lambdaChange() {
    val file = projectRule.createKtFile("HasLambda.kt", """
      fun hasLambda() : String {
        var capture = "x"
        var lambda = { capture = "a" }
        lambda()
        return capture
      }
    """)

    val cache = projectRule.initialCache(listOf(file))
    projectRule.modifyKtFile(file, """
        fun hasLambda() : String {
          var capture = "z"
          var lambda = { capture = "y" }
          lambda()
          return capture
        }
      """)
    val output = compile(file, cache)
    Assert.assertEquals(1, output.supportClassesMap.size)
    val returnedValue = invokeStatic("hasLambda", loadClass(output))
    Assert.assertEquals("y", returnedValue)
  }

  @Test
  fun samChange() {
    val file = projectRule.createKtFile("HasSAM.kt", """
      fun interface A {
        fun go(): Int
      }
      fun hasSam(): Int {
        var test = A { 100 }
        return test.go()
      }
    """)
    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String) = apk[className]
    })
    val output = compile(listOf(LiveEditCompilerInput(file, file)), compiler)
    Assert.assertEquals(1, output.supportClassesMap.size)
    // Can't test invocation of the method since the functional interface "A" is not loaded.
  }

  @Test
  fun noNewClasses() {
    val file = projectRule.createKtFile("Test.kt", """
      class A {}
      class B {}
    """)
    val cache = projectRule.initialCache(listOf(file))
    projectRule.modifyKtFile(file, """
      class A {}
      class B {}
      class C {}
    """.trimIndent())
    val exception = Assert.assertThrows(LiveEditUpdateException::class.java) {
      compile(file, cache)
    }
    assertEquals(exception.error, LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE)
    assertEquals(exception.details, "added new class C in Test.kt")
  }

  @Test
  fun crossFileReference() {
    projectRule.createKtFile("A.kt", "fun foo() = \"\"")
    val fileCallA = projectRule.createKtFile("CallA.kt", "fun callA() = foo()")
    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(fileCallA)
    val compiler = LiveEditCompiler(projectRule.project, cache, object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String) = apk[className]
    })
    compile(listOf(LiveEditCompilerInput(fileCallA, fileCallA)), compiler)
  }

  @Test
  fun internalVar() {
    val file = projectRule.createKtFile("HasInternalVar.kt", """
      internal var x = 0
      fun getNum() = x
    """)
    val cache = projectRule.initialCache(listOf(file))

    projectRule.modifyKtFile(file, """
     internal var x = 0
     fun getNum() = x + 1
    """)

    val output = compile(file, cache)
    Assert.assertTrue(output.classesMap["HasInternalVarKt"]!!.isNotEmpty())
    val returnedValue = invokeStatic("getNum", loadClass(output))
    Assert.assertEquals(1, returnedValue)
  }

  @Test
  fun publicInlineFunction() {
    try {
      val file = projectRule.createKtFile("HasPublicInline.kt", "public inline fun publicInlineFun() = 1")
      val cache = projectRule.initialCache(listOf(file))
      projectRule.modifyKtFile(file, "public inline fun publicInlineFun() = 2")
      compile(file, cache)
      Assert.fail("Expecting an exception thrown.")
    }
    catch (e: LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.NON_PRIVATE_INLINE_FUNCTION, e.error)
    }
  }

  @Test
  fun renamedFile() {
    val file = projectRule.createKtFile("RenamedFile.kt", """
      @file:kotlin.jvm.JvmName("CustomJvmName")
      @file:kotlin.jvm.JvmMultifileClass
      fun T() {}
    """)

    val cache = projectRule.initialCache(listOf(file))

    projectRule.modifyKtFile(file, """
      @file:kotlin.jvm.JvmName("CustomJvmName")
      @file:kotlin.jvm.JvmMultifileClass
      fun T() { val x = 0 }
    """)

    val output = compile(file, cache)
    Assert.assertNotNull(output.irClasses.singleOrNull { it.name == "CustomJvmName" }) // CustomJvmName.class doesn't change
    Assert.assertTrue(output.classesMap["CustomJvmName__RenamedFileKt"]!!.isNotEmpty())
  }

  @Test
  fun modifyConstructor() {
    val file = projectRule.createKtFile("ModifyConstructor.kt", """
      class MyClass() {
        init {
          val x = 0
        }
      }
    """)
    val cache = projectRule.initialCache(listOf(file))

   projectRule.modifyKtFile(file, """
      class MyClass() {
        init {
          val x = 999
        }
      }
    """)

    try {
      compile(file, cache)
      fail("Expected exception due to modified constructor")
    } catch (e: LiveEditUpdateException) {
      assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, e.error)
      assertContains(e.details, "MyClass")
    }
  }

  @Test
  fun modifyStaticInit() {
    val file = projectRule.createKtFile("ModifyStaticInit.kt", "val x = 1")
    val cache = projectRule.initialCache(listOf(file))
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


}

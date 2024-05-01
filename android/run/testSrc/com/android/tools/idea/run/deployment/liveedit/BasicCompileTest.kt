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

import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileByteArray
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileIr
import com.android.tools.idea.run.deployment.liveedit.analysis.disableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.enableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.initialCache
import com.android.tools.idea.run.deployment.liveedit.analysis.modifyKtFile
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtFile
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.fail

@RunWith(JUnit4::class)
class BasicCompileTest {
  private var projectRule = AndroidProjectRule.inMemory().withKotlin()

  // We don't need ADB in these tests. However, disableLiveEdit() or endableLiveEdit() does trigger calls to the AdbDebugBridge
  // so not having that available causes a NullPointerException when we call it.
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

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
    // Step 1: Error Free
    val file = projectRule.createKtFile("RecoverableError.kt", """
        fun recoverableError() { "a".toString() }
      """)

    val cache = projectRule.initialCache(listOf(file))

    // Step 2: Introduce recoverable syntax errors
    projectRule.modifyKtFile(file, """
        fun recoverableError() { "a".toString() } }
      """)

    try {
      compile(file, cache)
      Assert.fail("RecoverableError.kt contains a lexical error and should not be updated by Live Edit")
    } catch (e: LiveEditUpdateException) {
      Assert.assertEquals("Expecting a top level declaration", e.message)
    }

    // Step 3: Fix syntax error
    projectRule.modifyKtFile(file, """
        fun recoverableError() { "a".toString() }
      """)

    // Should not have compiler errors.
    compile(file, cache)
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
    val state = getPsiValidationState(file)
    val output = compile(listOf(LiveEditCompilerInput(file, state)), compiler)
    Assert.assertEquals(1, output.supportClassesMap.size)
    // Can't test invocation of the method since the functional interface "A" is not loaded.
  }

  @Test
  fun genericSamChange() {
    val file = projectRule.createKtFile("ModifyFieldValue.kt", """
      fun interface Observer<T> {
        fun onChanged(value: T)
      }

      class Watchable<T> {
        fun <T> callObserver(value: T, o: Observer<T>) {
          o.onChanged(value)
        }
      }

      fun main() {
        val x = Watchable<String>()
        x.callObserver("hello") { println(it) }
      }
    """)
    val cache = projectRule.initialCache(listOf(file))

    projectRule.modifyKtFile(file, """
      fun interface Observer<T> {
        fun onChanged(value: T)
      }

      class Watchable<T> {
        fun <T> callObserver(value: T, o: Observer<T>) {
          o.onChanged(value)
        }
      }

      fun main() {
        val x = Watchable<String>()
        x.callObserver("hello") { println("value: " + it) }
      }
    """)

    val output = compile(file, cache)
    Assert.assertEquals(1, output.supportClassesMap.size)
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
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_USER_CLASS_ADDED, exception.error)
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
    val state = getPsiValidationState(fileCallA)
    compile(listOf(LiveEditCompilerInput(fileCallA, state)), compiler)
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
      assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_CONSTRUCTOR, e.error)
      assertContains(e.details, "MyClass")
    }
  }

  @Test
  fun modifyFieldValue() {
    val file = projectRule.createKtFile("ModifyFieldValue.kt", """
      class MyClass() {
        val a = 100
        val b = 200
      }
    """)
    val cache = projectRule.initialCache(listOf(file))

    projectRule.modifyKtFile(file, """
      class MyClass() {
        val a = 999
        val b = 200
      }
    """)

    try {
      compile(file, cache)
      fail("Expected exception due to modified field")
    }
    catch (e: LiveEditUpdateException) {
      assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_CONSTRUCTOR, e.error)
      assertContains(e.details, "MyClass")
      println(e.details)
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
      assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_CLINIT, e.error)
      assertContains(e.details, "static initializer")
    }
  }

  @Test
  fun `Modify when Mapping`() {
    val enumDef = projectRule.createKtFile("Food.kt", """
      enum class Food { Pizza, Donuts }
    """)

    val file = projectRule.createKtFile("ModifyWhenMapping.kt", """
      fun getUnits(food: Food) : String {
        return when (food) {
          Food.Pizza -> "slices"
          Food.Donuts -> "dozens"
        }
      }
    """)
    val cache = projectRule.initialCache(listOf(enumDef, file))

    projectRule.modifyKtFile(file, """
      fun getUnits(food: Food) : String {
        return when (food) {
          Food.Donuts -> "x"
          Food.Pizza -> "y"
        }
      }
    """)

    try {
      compile(file, cache)
      fail("Expected exception due to modified constructor")
    } catch (e: LiveEditUpdateException) {
      assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_WHEN_ENUM_PATH, e.error)
      assertContains(e.details, "Changing `when` on enum code path")
    }
  }

  @Test
  fun `Adding new WithMapping`() {
    val enumDef = projectRule.createKtFile("Food.kt", """
      enum class Food { Pizza, Donuts }
    """)

    val file = projectRule.createKtFile("ModifyWhenMapping.kt", """
      fun getUnits(food: Food) : String {
        var suffix = when (food) {
          Food.Pizza -> "!!"
          Food.Donuts -> "!!!!!"
        }
        return suffix
      }

       fun getMessage(): String {
          return getUnits(Food.Pizza)
       }
    """)
    val cache = projectRule.initialCache(listOf(enumDef, file))

    projectRule.modifyKtFile(file, """
      fun getUnits(food: Food) : String {
        var suffix = when (food) {
          Food.Pizza -> "!!"
          Food.Donuts -> "!!!!!"
        }

        return when (food) {
          Food.Pizza -> "slices"
          Food.Donuts -> "dozens"
        } + suffix
      }

       fun getMessage(): String {
          return getUnits(Food.Pizza)
       }
    """)

    val output = compile(file, irClassCache = cache)
    var apk = projectRule.directApiCompileByteArray(listOf(enumDef, file))

    Assert.assertTrue(output.classesMap["ModifyWhenMappingKt"]!!.isNotEmpty())
    val returnedValue = invokeStatic("getMessage", loadClass(output, extraClasses = apk))
    Assert.assertEquals("slices!!", returnedValue)
  }


  @Test
  fun diagnosticErrorForInvisibleReference() {
    try {
      val file = projectRule.createKtFile("A.kt", """
      open class Parent {
        protected open fun invisibleFunction() {}
      }
      class Child: Parent() {
        override fun invisibleFunction() {}
      }
      fun foo() {
        val child = Child()
        child.invisibleFunction()
      }
    """)
      compile(file)
      Assert.fail("A.kt contains a call to an invisible function invisibleFunction()")
    }
    catch (e: LiveEditUpdateException) {
      if (KotlinPluginModeProvider.isK2Mode()) {
        Assert.assertEquals("[INVISIBLE_REFERENCE] Cannot access 'fun invisibleFunction(): Unit': it is protected in '/Child'.", e.message)
      } else {
        Assert.assertTrue(e.message?.contains("Analyze Error. INVISIBLE_MEMBER") == true)
      }
    }
  }
}

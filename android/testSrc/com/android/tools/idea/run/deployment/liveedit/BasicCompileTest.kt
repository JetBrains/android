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

import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.literals.MethodReference
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import kotlin.test.fail

@RunWith(JUnit4::class)
class BasicCompileTest {
  private lateinit var myProject: Project
  private lateinit var ktComposable: PsiFile
  private lateinit var ktFileA: PsiFile
  private lateinit var ktFileCallA: PsiFile
  private lateinit var ktFileComposeSimple: PsiFile
  private lateinit var ktFileComposeNested: PsiFile


  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
    ktFileA = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"}")
    ktFileCallA = projectRule.fixture.configureByText("CallA.kt", "fun callA() : String { return foo() }")

    ktComposable = projectRule.fixture.configureByText("Composable.kt", "package androidx.compose.runtime \n" +
                                                                        "@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)\n" +
                                                                        "annotation class Composable\n")

    ktFileComposeSimple = projectRule.fixture.configureByText("ComposeSimple.kt",
                                                             "@androidx.compose.runtime.Composable fun composableFun() : String { " +
                                                             "return \"hi\" " +
                                                             "}")

    ktFileComposeNested = projectRule.fixture.configureByText("ComposeNested.kt",
                                                              "@androidx.compose.runtime.Composable fun composableNested () : " +
                                                              "@androidx.compose.runtime.Composable (Int)->Unit { " +
                                                              "return { } " +
                                                              "}")
  }

  @Test
  fun simpleChange() {
    var output = compile(ktFileA, findFunction(ktFileA, "foo"))
    var returnedValue = invokeStatic("foo", loadClass("AKt", output.classData))
    Assert.assertEquals("I am foo", returnedValue)
  }

  @Test
  fun simpleComposeChange() {
    var output = compile(ktFileComposeSimple, findFunction(ktFileComposeSimple, "composableFun"))
    // We can't really invoke any composable without the runtime libraries. At least we can check
    // to make sure the output isn't empty.
    Assert.assertTrue(output.classData.isNotEmpty())
  }

  @Test
  fun simpleComposeNested() {
    var output = compile(ktFileComposeNested, findFunction(ktFileComposeNested, "composableNested"))
    Assert.assertEquals("composableNested", output.methodName)
    Assert.assertEquals("(Landroidx/compose/runtime/Composer;I)Lkotlin/jvm/functions/Function3;", output.methodDesc)
  }

  @Test
  fun crossFileReference() {
    // b/201728545
    compileFail(ktFileCallA, findFunction(ktFileCallA, "callA"))
  }

  private fun compile(file: PsiFile, function: KtNamedFunction) : AndroidLiveEditCodeGenerator.GeneratedCode {
    val output = mutableListOf<AndroidLiveEditCodeGenerator.GeneratedCode>()
    AndroidLiveEditCodeGenerator().compile(myProject, listOf(MethodReference(file, function)), output)
    return output[0]
  }

  private fun compileFail(file: PsiFile, function: KtNamedFunction) {
    val output = mutableListOf<AndroidLiveEditCodeGenerator.GeneratedCode>()
    try {
      AndroidLiveEditCodeGenerator().compile(myProject, listOf(MethodReference(file, function)), output)
      fail("Compilation should fail")
    } catch (e: LiveEditUpdateException) {
      // Do nothing; test passes.
    }
  }
  /**
   * Look for the first named function with a given name.
   */
  private fun findFunction(file: PsiFile, name: String): KtNamedFunction {
    return runReadAction {
      file.collectDescendantsOfType<KtNamedFunction>().first { it.name?.contains(name) ?: false }
    }
  }

  /**
   * Loads a class with given bytecode in an isolated classloader.
   */
  private fun loadClass(name: String, bytecode: ByteArray) : Class<*> {
    // We use a temp classloader so we can have the same class name across different classes without conflict.
    val tempLoader = object : ClassLoader() {
      override fun findClass(name: String): Class<*>? {
        return defineClass(name, bytecode, 0, bytecode.size)
      }
    }
    return tempLoader.loadClass(name)
  }

  /**
   * Invoke a given function of a given class and return the return value.
   */
  private fun invokeStatic(name: String, clazz: Class<*>) : Any {
    return clazz.getMethod(name).invoke(null)
  }
}
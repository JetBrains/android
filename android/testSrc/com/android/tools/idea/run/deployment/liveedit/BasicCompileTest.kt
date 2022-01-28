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
import kotlin.test.fail

@RunWith(JUnit4::class)
class BasicCompileTest {
  private lateinit var myProject: Project
  private var files = HashMap<String, PsiFile>()

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
    files["A.kt"] = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"}")
    files["CallA.kt"] = projectRule.fixture.configureByText("CallA.kt", "fun callA() : String { return foo() }")

    files["Composable.kt"] = projectRule.fixture.configureByText("Composable.kt", "package androidx.compose.runtime \n" +
                                                                        "@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)\n" +
                                                                        "annotation class Composable\n")

    files["ComposeSimple.kt"] = projectRule.fixture.configureByText("ComposeSimple.kt",
                                                             "@androidx.compose.runtime.Composable fun composableFun() : String { " +
                                                             "return \"hi\" " +
                                                             "}")

    files["ComposeNested.kt"] = projectRule.fixture.configureByText("ComposeNested.kt",
                                                              "@androidx.compose.runtime.Composable fun composableNested () : " +
                                                              "@androidx.compose.runtime.Composable (Int)->Unit { " +
                                                              "return { } " +
                                                              "}")

    files["HasLambda.kt"] = projectRule.fixture.configureByText("HasLambda.kt",
                                                                "fun hasLambda() : String { \n" +
                                                                "var capture = \"x\" \n" +
                                                                "var lambda = {capture = \"y\"} \n" +
                                                                "lambda() \n" +
                                                                "return capture \n" +
                                                                "}")
  }

  @Test
  fun simpleChange() {
    var output = compile(files["A.kt"], "foo").singleOutput()
    var returnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)
  }

  @Test
  fun lambdaChange() {
    var output = compile(files["HasLambda.kt"], "hasLambda").singleOutput()
    Assert.assertEquals(1, output.supportClasses.size)
    var returnedValue = invokeStatic("hasLambda", loadClass(output))
    Assert.assertEquals("y", returnedValue)
  }

  @Test
  fun simpleComposeChange() {
    var output = compile(files["ComposeSimple.kt"], "composableFun").singleOutput()
    // We can't really invoke any composable without the runtime libraries. At least we can check
    // to make sure the output isn't empty.
    Assert.assertTrue(output.classData.isNotEmpty())
  }

  @Test
  fun simpleComposeNested() {
    var output = compile(files["ComposeNested.kt"], "composableNested").singleOutput()
    Assert.assertEquals("composableNested", output.methodName)
    Assert.assertEquals("(Landroidx/compose/runtime/Composer;I)Lkotlin/jvm/functions/Function3;", output.methodDesc)
  }

  @Test
  fun crossFileReference() {
    // b/201728545
    compileFail(files["CallA.kt"], findFunction(files["CallA.kt"], "callA"))
  }

  private fun compile(file: PsiFile?, functionName: String): List<AndroidLiveEditCodeGenerator.GeneratedCode> {
    return compile(file!!, findFunction(file, functionName))
  }

  private fun compile(file: PsiFile, function: KtNamedFunction) : List<AndroidLiveEditCodeGenerator.GeneratedCode> {
    val output = mutableListOf<AndroidLiveEditCodeGenerator.GeneratedCode>()
    AndroidLiveEditCodeGenerator().compile(myProject, listOf(MethodReference(file, function)), output)
    return output
  }

  private fun compileFail(file: PsiFile?, function: KtNamedFunction) {
    val output = mutableListOf<AndroidLiveEditCodeGenerator.GeneratedCode>()
    try {
      AndroidLiveEditCodeGenerator().compile(myProject, listOf(MethodReference(file!!, function)), output)
      fail("Compilation should fail")
    } catch (e: LiveEditUpdateException) {
      // Do nothing; test passes.
    }
  }

  /**
   * Look for the first named function with a given name.
   */
  private fun findFunction(file: PsiFile?, name: String): KtNamedFunction {
    return runReadAction {
      file!!.collectDescendantsOfType<KtNamedFunction>().first { it.name?.contains(name) ?: false }
    }
  }

  /**
   * Loads the target class of the generator's output in a throwaway classloader.
   *
   * Support classes will also be loaded in the SAME classloader.
   */
  private fun loadClass(output: AndroidLiveEditCodeGenerator.GeneratedCode) : Class<*> {
    // We use a temp classloader so we can have the same class name across different classes without conflict.
    val tempLoader = object : ClassLoader() {
      override fun findClass(name: String): Class<*>? {
        return if (name == output.className) {
          // load it from the target
          defineClass(name, output.classData, 0, output.classData.size)
        } else {
          // try to see if it is one of the support classes
          defineClass(name, output.supportClasses[name], 0, output.supportClasses[name]!!.size)
        }
      }
    }
    return tempLoader.loadClass(output.className)
  }

  /**
   * Invoke a given function of a given class and return the return value.
   */
  private fun invokeStatic(name: String, clazz: Class<*>) : Any {
    return clazz.getMethod(name).invoke(null)
  }
}

private fun List<AndroidLiveEditCodeGenerator.GeneratedCode>.singleOutput() : AndroidLiveEditCodeGenerator.GeneratedCode{
  Assert.assertEquals(1, this.size)
  return this[0]
}
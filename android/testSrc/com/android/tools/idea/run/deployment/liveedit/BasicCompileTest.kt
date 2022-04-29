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

import com.android.tools.idea.editors.literals.FunctionState
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BasicCompileTest {
  private lateinit var myProject: Project
  private var files = HashMap<String, PsiFile>()

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
    files["A.kt"] = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"} fun bar() = 1")
    files["CallA.kt"] = projectRule.fixture.configureByText("CallA.kt", "fun callA() : String { return foo() }")

    files["InlineTarget.kt"] = projectRule.fixture.configureByText("InlineTarget.kt", "inline fun it1() : String { return \"I am foo\"}")
    files["CallInlineTarget.kt"] = projectRule.fixture.configureByText("CallInlineTarget.kt", "fun callInlineTarget() : String { return it1() }")

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

    files["HasInternalVar.kt"] = projectRule.fixture.configureByText("HasInternalVar.kt",
                                                                     "internal var x = 1\n fun getNum() = x")
  }

  @Test
  fun simpleChange() {
    // Compile A.kt targetting foo()
    var state = readFunctionState(files["A.kt"])
    var output = compile(files["A.kt"], "foo", state).singleOutput()
    var returnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)
    Assert.assertEquals(0, output.offSet.start)
    Assert.assertEquals(39, output.offSet.end)

    // Compile A.kt again targeting bar()
    output = compile(files["A.kt"], "bar", state).singleOutput()
    Assert.assertEquals(40, output.offSet.start)
    Assert.assertEquals(53, output.offSet.end)

    // Replace the return value of foo.
    var foo = findFunction(files["A.kt"], "foo")
    WriteCommandAction.runWriteCommandAction(myProject) {
      var expresion = ((foo.bodyBlockExpression!!.firstStatement as KtReturnExpression).returnedExpression as KtStringTemplateExpression)
      Assert.assertEquals("\"I am foo\"", expresion.text)
      expresion.updateText("I am not foo")
      Assert.assertEquals(39 + "not ".length, foo.textRange.endOffset)
    }

    // Re-compile A.kt like how live edit work.
    var leOutput = compile(files["A.kt"], "foo", state).singleOutput()
    var leReturnedValue = invokeStatic("foo", loadClass(leOutput))
    Assert.assertEquals("I am not foo", leReturnedValue)
    Assert.assertEquals(0, leOutput.offSet.start)
    // The offset remains unchanged as we use this to invalidate the previous state.
    Assert.assertEquals(39, leOutput.offSet.end)

    // Re-compiling A.kt targetting bar(). Note that the offsets of bar() does not change despite foo() is now longer.
    output = compile(files["A.kt"], "bar", state).singleOutput()
    Assert.assertEquals(40, output.offSet.start)
    Assert.assertEquals(53, output.offSet.end)
  }

  @Test
  fun inlineTarget() {
    var state = readFunctionState(files["CallInlineTarget.kt"])
    try {
      compile(files["CallInlineTarget.kt"], "callInlineTarget", state, useInliner = false).singleOutput()
      Assert.fail("Expecting LiveEditUpdateException")
    } catch (e: LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.UNABLE_TO_INLINE, e.error)
    }
    var output = compile(files["CallInlineTarget.kt"], "callInlineTarget", state, useInliner = true).singleOutput()
    var returnedValue = invokeStatic("callInlineTarget", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)
  }

  @Test
  fun lambdaChange() {
    var output = compile(files["HasLambda.kt"], "hasLambda").singleOutput()
    Assert.assertEquals(1, output.supportClasses.size)
    var returnedValue = invokeStatic("hasLambda", loadClass(output))
    Assert.assertEquals("y", returnedValue)
    Assert.assertEquals(0, output.offSet.start)
    Assert.assertEquals(104, output.offSet.end)
  }

  @Test
  fun simpleComposeChange() {
    var output = compile(files["ComposeSimple.kt"], "composableFun").singleOutput()
    // We can't really invoke any composable without the runtime libraries. At least we can check
    // to make sure the output isn't empty.
    Assert.assertTrue(output.classData.isNotEmpty())
    Assert.assertEquals("@androidx.compose.runtime.Composable ".length, output.offSet.start)
    Assert.assertEquals(81, output.offSet.end)
  }

  @Test
  fun simpleComposeNested() {
    var output = compile(files["ComposeNested.kt"], "composableNested").singleOutput()
    Assert.assertEquals("composableNested", output.methodName)
    Assert.assertEquals("(Landroidx/compose/runtime/Composer;I)Lkotlin/jvm/functions/Function3;", output.methodDesc)
    Assert.assertEquals("@androidx.compose.runtime.Composable ".length, output.offSet.start)
    Assert.assertEquals(126, output.offSet.end)
  }

  @Test
  fun crossFileReference() {
    compile(files["CallA.kt"], "callA")
  }

  @Test
  fun internalVar() {
    var output = compile(files["HasInternalVar.kt"], "getNum").singleOutput()
    Assert.assertTrue(output.classData.isNotEmpty())
    var returnedValue = invokeStatic("getNum", loadClass(output))
    Assert.assertEquals(1, returnedValue)
  }

  private fun compile(file: PsiFile?, functionName: String, state: FunctionState? = null, useInliner: Boolean = false) :
        List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> {
    return compile(file!!, findFunction(file, functionName), state, useInliner)
  }

  private fun compile(file: PsiFile, function: KtNamedFunction, state: FunctionState? = null, useInliner: Boolean = false) :
        List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> {
    LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis = useInliner
    val output = mutableListOf<AndroidLiveEditCodeGenerator.CodeGeneratorOutput>()
    AndroidLiveEditCodeGenerator(myProject).compile(
      listOf(AndroidLiveEditCodeGenerator.CodeGeneratorInput(file, function, state?: readFunctionState(file))), output)
    return output
  }

  private fun readFunctionState(file: PsiFile?) = runReadAction { FunctionState(file as KtFile) }

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
  private fun loadClass(output: AndroidLiveEditCodeGenerator.CodeGeneratorOutput) : Class<*> {
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

private fun List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput>.singleOutput() : AndroidLiveEditCodeGenerator.CodeGeneratorOutput{
  Assert.assertEquals(1, this.size)
  return this[0]
}
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

import com.android.testutils.TestUtils
import com.android.tools.compose.ComposePluginIrGenerationExtension
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import junit.framework.Assert
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.net.URL
import java.net.URLClassLoader

@RunWith(JUnit4::class)
class BasicCompileTest {
  private lateinit var myProject: Project
  private var files = HashMap<String, PsiFile>()

  /**
   * Path to the compose-runtime jar. Note that unlike all other dependencies, we
   * don't need to load that into the test's runtime classpath. Instead, we just
   * need to make sure it is in the classpath input of the compiler invocation
   * Live Edit uses. Aside from things like references to the @Composable
   * annotation, we actually don't need anything from that runtime during the compiler.
   * The main reason to include that is because the compose compiler plugin expects
   * the runtime to be path of the classpath or else it'll throw an error.
   */
  private val composeRuntimePath = TestUtils.getWorkspaceRoot().resolve(
    "tools/adt/idea/compose-ide-plugin/testData/lib/compose-runtime-1.3.0-SNAPSHOT.jar").toString()

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project

    projectRule.module.loadComposeRuntimeInClassPath()

    // Register the compose compiler plugin much like what Intellij would normally do.
    if (IrGenerationExtension.getInstances(myProject).find { it is ComposePluginIrGenerationExtension } == null) {
      IrGenerationExtension.registerExtension(myProject, ComposePluginIrGenerationExtension())
    }

    files["A.kt"] = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"} fun bar() = 1")
    files["CallA.kt"] = projectRule.fixture.configureByText("CallA.kt", "fun callA() : String { return foo() }")

    files["InlineTarget.kt"] = projectRule.fixture.configureByText("InlineTarget.kt", "inline fun it1() : String { return \"I am foo\"}")
    files["CallInlineTarget.kt"] = projectRule.fixture.configureByText("CallInlineTarget.kt", "fun callInlineTarget() : String { return it1() }")

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

    files["HasSAM.kt"] = projectRule.fixture.configureByText("HasSAM.kt",
                                                                "fun interface A {\n" +
                                                                "fun go(): Int\n" +
                                                                "}\n" +
                                                                "fun hasSAM() : Int { \n" +
                                                                "var test = A { 100 } \n" +
                                                                "return test.go() \n" +
                                                                "}")

    files["HasInternalVar.kt"] = projectRule.fixture.configureByText("HasInternalVar.kt",
                                                                     "internal var x = 1\n fun getNum() = x")

    files["HasPublicInline.kt"] = projectRule.fixture.configureByText("HasPublicInline.kt",
                                                                     "public inline fun publicInlineFun() = 1")
  }

  @Test
  fun simpleChange() {
    // Compile A.kt targetting foo()
    var output = compile(files["A.kt"], "foo").singleOutput()
    var returnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)

    // Compile A.kt again targeting bar()
    output = compile(files["A.kt"], "bar").singleOutput()

    // Replace the return value of foo.
    var foo = findFunction(files["A.kt"], "foo")
    WriteCommandAction.runWriteCommandAction(myProject) {
      var expresion = ((foo.bodyBlockExpression!!.firstStatement as KtReturnExpression).returnedExpression as KtStringTemplateExpression)
      Assert.assertEquals("\"I am foo\"", expresion.text)
      expresion.updateText("I am not foo")
      Assert.assertEquals(39 + "not ".length, foo.textRange.endOffset)
    }

    // Re-compile A.kt like how live edit work.
    var leOutput = compile(files["A.kt"], "foo").singleOutput()
    var leReturnedValue = invokeStatic("foo", loadClass(leOutput))
    Assert.assertEquals("I am not foo", leReturnedValue)

    // Re-compiling A.kt targetting bar(). Note that the offsets of bar() does not change despite foo() is now longer.
    output = compile(files["A.kt"], "bar").singleOutput()
  }

  @Test
  fun inlineTarget() {
    try {
      compile(files["CallInlineTarget.kt"], "callInlineTarget", useInliner = false).singleOutput()
      Assert.fail("Expecting LiveEditUpdateException")
    } catch (e: LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.UNABLE_TO_INLINE, e.error)
    }
    var output = compile(files["CallInlineTarget.kt"], "callInlineTarget", useInliner = true).singleOutput()
    var returnedValue = invokeStatic("callInlineTarget", loadClass(output))
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
  fun samChange() {
    val output = compile(files["HasSAM.kt"], "hasSAM").singleOutput()
    Assert.assertEquals(1, output.supportClasses.size)
    // Can't test invocation of the method since the functional interface "A" is not loaded.
  }

  @Test
  fun simpleComposeChange() {
    var output = compile(files["ComposeSimple.kt"], "composableFun").singleOutput()
    // We can't really invoke any composable without a "host". Normally that host will be the
    // Android activity. There are other hosts that we can possibly run as a Compose unit test.
    // We could potentially look into doing that. However, for the purpose of verifying the
    // compose compiler was invoked correctly, we can just check the output's methods.
    Assert.assertTrue(output.classData.isNotEmpty())
    var c = loadClass(output)
    var foundFunction = false;
    for (m in c.methods) {
      if (m.toString().contains("ComposeSimpleKt.composableFun(androidx.compose.runtime.Composer,int)")) {
        foundFunction = true;
      }
    }
    Assert.assertTrue(foundFunction)
  }

  @Test
  fun simpleComposeNested() {
    var output = compile(files["ComposeNested.kt"], "composableNested").singleOutput()
    Assert.assertEquals("composableNested", output.methodName)
    Assert.assertEquals("(Landroidx/compose/runtime/Composer;I)Lkotlin/jvm/functions/Function3;", output.methodDesc)
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

  @Test
  fun publicInlineFunction() {
    try {
      compile(files["HasPublicInline.kt"], "publicInlineFun")
      Assert.fail("Expecting an exception thrown.")
    } catch (e : LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.NON_PRIVATE_INLINE_FUNCTION, e.error)
    }
  }

  private fun compile(file: PsiFile?, functionName: String, useInliner: Boolean = false) :
        List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> {
    return compile(file!!, findFunction(file, functionName), useInliner)
  }

  private fun compile(file: PsiFile, function: KtNamedFunction, useInliner: Boolean = false) :
        List<AndroidLiveEditCodeGenerator.CodeGeneratorOutput> {
    LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis = useInliner
    LiveEditAdvancedConfiguration.getInstance().usePartialRecompose = false
    val output = mutableListOf<AndroidLiveEditCodeGenerator.CodeGeneratorOutput>()

    // The real Live Edit / Fast Preview has a retry system should the compilation got cancelled.
    // We are going to use a simplified version of that here and continue to try until
    // compilation succeeds.
    var finished = false
    while (!finished) {
      finished = AndroidLiveEditCodeGenerator(myProject).compile(
        listOf(AndroidLiveEditCodeGenerator.CodeGeneratorInput(file, function)), output)
    }
    return output
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
  private fun loadClass(output: AndroidLiveEditCodeGenerator.CodeGeneratorOutput) : Class<*> {
    // We use a temp classloader so we can have the same class name across different classes without conflict.
    val tempLoader = object : URLClassLoader(arrayOf(URL("jar:file:$composeRuntimePath!/"))) {
      override fun findClass(name: String): Class<*>? {
        return if (name == output.className) {
          // load it from the target
          defineClass(name, output.classData, 0, output.classData.size)
        } else if (output.supportClasses.containsKey(name)) {
          // try to see if it is one of the support classes
          defineClass(name, output.supportClasses[name], 0, output.supportClasses[name]!!.size)
        } else {
          return super.findClass(name)
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

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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
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
    setUpComposeInProjectFixture(projectRule)
    myProject = projectRule.project

    files["A.kt"] = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"} fun bar() = 1")
    files["CallA.kt"] = projectRule.fixture.configureByText("CallA.kt", "fun callA() : String { return foo() }")

    files["InlineTarget.kt"] = projectRule.fixture.configureByText("InlineTarget.kt", "inline fun it1() : String { return \"I am foo\"}")
    files["CallInlineTarget.kt"] = projectRule.fixture.configureByText("CallInlineTarget.kt", "fun callInlineTarget() : String { return it1() }")


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

    files["RecoverableError.kt"] = projectRule.fixture.configureByText("RecoverableError.kt",
                                                                      "fun recoverableError() {\"a\".toString()}}")

    // Create mocks for the kotlin.jvm to avoid having to bring in the whole dependency
    projectRule.fixture.configureByText("JvmName.kt", "package kotlin.jvm\n" +
                                                      "@Target(AnnotationTarget.FILE)\n" +
                                                      "public annotation class JvmName(val name: String)\n")

    projectRule.fixture.configureByText("JvmMultifileClass.kt", "package kotlin.jvm\n" +
                                                                "@Target(AnnotationTarget.FILE)\n" +
                                                                "public annotation class JvmMultifileClass()")

    files["RenamedFile.kt"] = projectRule.fixture.configureByText("RenamedFile.kt",
                                                                  "@file:kotlin.jvm.JvmName(\"CustomJvmName\")\n" +
                                                                  "@file:kotlin.jvm.JvmMultifileClass\n" +
                                                                  "fun T() {}")
  }

  @Test
  fun simpleChange() {
    // Compile A.kt targetting foo()
    var output = compile(files["A.kt"], "foo")
    var returnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)

    // Compile A.kt again targeting bar()
    output = compile(files["A.kt"], "bar")

    // Replace the return value of foo.
    var foo = findFunction(files["A.kt"], "foo")
    WriteCommandAction.runWriteCommandAction(myProject) {
      var expresion = ((foo.bodyBlockExpression!!.firstStatement as KtReturnExpression).returnedExpression as KtStringTemplateExpression)
      Assert.assertEquals("\"I am foo\"", expresion.text)
      expresion.updateText("I am not foo")
      Assert.assertEquals(39 + "not ".length, foo.textRange.endOffset)
    }

    // Re-compile A.kt like how live edit work.
    var leOutput = compile(files["A.kt"], "foo")
    var leReturnedValue = invokeStatic("foo", loadClass(leOutput))
    Assert.assertEquals("I am not foo", leReturnedValue)

    // Re-compiling A.kt targetting bar(). Note that the offsets of bar() does not change despite foo() is now longer.
    output = compile(files["A.kt"], "bar")
  }

  @Test
  fun recoverableErrors() {
    try {
      compile(files["RecoverableError.kt"], "recoverableError")
      Assert.fail("RecoverableError.kt contains a lexical error and should not be updated by Live Edit")
    } catch (e: LiveEditUpdateException) {
      Assert.assertEquals("Expecting a top level declaration", e.message)
    }
  }

  @Test
  fun inlineTarget() {
    try {
      compile(files["CallInlineTarget.kt"], "callInlineTarget", useInliner = false)
      Assert.fail("Expecting LiveEditUpdateException")
    } catch (e: LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.UNABLE_TO_INLINE, e.error)
    }
    var output = compile(files["CallInlineTarget.kt"], "callInlineTarget", useInliner = true)
    var returnedValue = invokeStatic("callInlineTarget", loadClass(output))
    Assert.assertEquals("I am foo", returnedValue)
  }

  @Test
  fun lambdaChange() {
    var output = compile(files["HasLambda.kt"], "hasLambda")
    Assert.assertEquals(1, output.supportClasses.size)
    var returnedValue = invokeStatic("hasLambda", loadClass(output))
    Assert.assertEquals("y", returnedValue)
  }

  @Test
  fun samChange() {
    val output = compile(files["HasSAM.kt"], "hasSAM")
    Assert.assertEquals(1, output.supportClasses.size)
    // Can't test invocation of the method since the functional interface "A" is not loaded.
  }

  @Test
  fun crossFileReference() {
    compile(files["CallA.kt"], "callA")
  }

  @Test
  fun internalVar() {
    var output = compile(files["HasInternalVar.kt"], "getNum")
    Assert.assertTrue(output.classesMap["HasInternalVarKt"]!!.isNotEmpty())
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

  @Test
  fun renamedFile() {
    var output = compile(files["RenamedFile.kt"], "T")
    Assert.assertTrue(output.classesMap["CustomJvmName"]!!.isNotEmpty())
    Assert.assertTrue(output.classesMap["CustomJvmName__RenamedFileKt"]!!.isNotEmpty())
  }
}

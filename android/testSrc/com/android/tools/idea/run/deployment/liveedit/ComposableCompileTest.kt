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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposableCompileTest {
  private var files = HashMap<String, PsiFile>()

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)

    files["ComposeSimple.kt"] = projectRule.fixture.configureByText("ComposeSimple.kt",
                                                                    "@androidx.compose.runtime.Composable fun composableFun() : String { " +
                                                                    "return \"hi\" " +
                                                                    "}")

    files["ComposeNested.kt"] = projectRule.fixture.configureByText("ComposeNested.kt",
                                                                    "@androidx.compose.runtime.Composable fun composableNested () : " +
                                                                    "@androidx.compose.runtime.Composable (Int)->Unit { " +
                                                                    "return { } " +
                                                                    "}")

    files["HasComposableSingletons.kt"] = projectRule.fixture.configureByText("HasComposableSingletons.kt",
                                                                              "import androidx.compose.runtime.Composable\n" +
                                                                              "@Composable fun hasLambdaA(content: @Composable () -> Unit) { }\n" +
                                                                              "@Composable fun hasLambdaB() { hasLambdaA {} }")
  }

  @Test
  fun simpleComposeChange() {
    var output = compile(files["ComposeSimple.kt"], "composableFun").singleOutput()
    // We can't really invoke any composable without a "host". Normally that host will be the
    // Android activity. There are other hosts that we can possibly run as a Compose unit test.
    // We could potentially look into doing that. However, for the purpose of verifying the
    // compose compiler was invoked correctly, we can just check the output's methods.
    Assert.assertTrue(output.classData.isNotEmpty())

    Assert.assertEquals(1639534479, output.groupId)

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
    Assert.assertEquals(-1050554150, output.groupId)
  }

  @Test
  fun testModuleName() {
    var output = compile(files["HasComposableSingletons.kt"], "hasLambdaA").singleOutput()
    var singleton = output.supportClasses.get("ComposableSingletons\$HasComposableSingletonsKt");
    Assert.assertNotNull(singleton)
    var cl = loadClass(output, "ComposableSingletons\$HasComposableSingletonsKt")
    var getLambda = cl.methods.find { it.name.contains("getLambda") }
    // Make sure we have getLambda$<MODULE_NAME>
    Assert.assertTrue(getLambda!!.name.contains(projectRule.module.name))
  }
}
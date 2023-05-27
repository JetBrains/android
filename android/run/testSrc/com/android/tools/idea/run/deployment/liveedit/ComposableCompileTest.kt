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
                                                                    "}\n" +
                                                                    "@androidx.compose.runtime.Composable fun composableFun2() : String { " +
                                                                    "return \"hi2\" " +
                                                                    "}\n")

    files["ComposeNested.kt"] = projectRule.fixture.configureByText("ComposeNested.kt",
                                                                    "@androidx.compose.runtime.Composable fun composableNested () : " +
                                                                    "@androidx.compose.runtime.Composable (Int)->Unit { " +
                                                                    "return { } " +
                                                                    "}")

    files["HasComposableSingletons.kt"] = projectRule.fixture.configureByText("HasComposableSingletons.kt",
                                                                              "import androidx.compose.runtime.Composable\n" +
                                                                              "@Composable fun hasLambdaA(content: @Composable () -> Unit) { }\n" +
                                                                              "@Composable fun hasLambdaB() { hasLambdaA {} }")

    files["Mixed.kt"] = projectRule.fixture.configureByText("Mixed.kt",
                                                            "import androidx.compose.runtime.Composable\n" +
                                                            "@Composable fun isComposable() {}\n" +
                                                            "fun notComposable() {}\n")
  }

  @Test
  fun simpleComposeChange() {
    val output = compile(files["ComposeSimple.kt"], "composableFun")
    // We can't really invoke any composable without a "host". Normally that host will be the
    // Android activity. There are other hosts that we can possibly run as a Compose unit test.
    // We could potentially look into doing that. However, for the purpose of verifying the
    // compose compiler was invoked correctly, we can just check the output's methods.
    Assert.assertTrue(output.classesMap["ComposeSimpleKt"]!!.isNotEmpty())

    Assert.assertEquals(1639534479, output.groupIds.first())

    val c = loadClass(output)
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
    val output = compile(files["ComposeNested.kt"], "composableNested")
    Assert.assertEquals(-1050554150, output.groupIds.first())
  }

  @Test
  fun multipleEditsInOneUpdate() {
    // Testing an edit that has two files and three function modified.
    val file1 = files["ComposeSimple.kt"]
    val file2 = files["ComposeNested.kt"]
    val output = compile(listOf(
      LiveEditCompilerInput(file1!!, findFunction(file1, "composableFun")),
      LiveEditCompilerInput(file1!!, findFunction(file1, "composableFun2")),
      LiveEditCompilerInput(file1!!, findFunction(file1, "composableFun2")), // Multiple edits of the same function
      LiveEditCompilerInput(file2!!, findFunction(file2, "composableNested")),
      ))

    Assert.assertEquals(2, output.classesMap.size)
    Assert.assertTrue(output.classesMap.get("ComposeSimpleKt")!!.isNotEmpty())
    Assert.assertTrue(output.classesMap.get("ComposeNestedKt")!!.isNotEmpty())

    Assert.assertEquals(3, output.groupIds.size)
    Assert.assertTrue("groupids = " + output.groupIds.toString(), output.groupIds.contains(1639534479))
    Assert.assertTrue("groupids = " + output.groupIds.toString(), output.groupIds.contains(-1050554150))
    Assert.assertTrue("groupids = " + output.groupIds.toString(), output.groupIds.contains(-1350204187))
    Assert.assertFalse(output.resetState) // Compose only edits should not request for a full state reset.
  }

  @Test
  fun simpleMixed() {
    var output = compile(files["Mixed.kt"], "isComposable")
    Assert.assertEquals(-785806172, output.groupIds.first())
    Assert.assertFalse(output.resetState)

    output = compile(files["Mixed.kt"], "notComposable")
    // Editing a normal Kotlin function should not result any group IDs. Instead, it should manually trigger a full state reset every edit.
    Assert.assertTrue(output.groupIds.isEmpty())
    Assert.assertTrue(output.resetState)
  }

  @Test
  fun testModuleName() {
    val output = compile(files["HasComposableSingletons.kt"], "hasLambdaA")
    val singleton = output.supportClassesMap.get("ComposableSingletons\$HasComposableSingletonsKt");
    Assert.assertNotNull(singleton)
    val cl = loadClass(output, "ComposableSingletons\$HasComposableSingletonsKt")
    val getLambda = cl.methods.find { it.name.contains("getLambda") }
    // Make sure we have getLambda$<MODULE_NAME>
    Assert.assertTrue(getLambda!!.name.contains(projectRule.module.name))
  }
}
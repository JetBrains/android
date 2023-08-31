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

import com.android.tools.idea.run.deployment.liveedit.analysis.compileIr
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposableCompileTest {
  private var files = HashMap<String, PsiFile>()

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)



    files["HasComposableSingletons.kt"] = projectRule.fixture.configureByText("HasComposableSingletons.kt",
                                                                              "import androidx.compose.runtime.Composable\n" +
                                                                              "@Composable fun hasLambdaA(content: @Composable () -> Unit) { }\n" +
                                                                              "@Composable fun hasLambdaB() { hasLambdaA {} }")

  }

  @Test
  fun simpleComposeChange() {
    val cache = initialCache(mapOf("ComposeSimple.kt" to """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() : String {
        var str = "hi"
        return str
      }
      @Composable fun composableFun2() : String {
        return "hi2"
      }"""))

    val file = projectRule.fixture.configureByText("ComposeSimple.kt","""
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() : String {
        var str = "hello"
        return str
      }
      @Composable fun composableFun2() : String {
        return "hi2"
      }""")
    val output = compile(file, cache)
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
    val cache = initialCache(mapOf("ComposeNested.kt" to """
      import androidx.compose.runtime.Composable
      @Composable
      fun composableNested(): @Composable (Int) -> Unit {
        return { }
      }"""))
    val file = projectRule.fixture.configureByText("ComposeNested.kt", """
      import androidx.compose.runtime.Composable
      @Composable
      fun composableNested(): @Composable (Int) -> Unit {
        val x = 0
        return { val y = 0 }
      }""")
    val output = compile(file, cache)
    Assert.assertEquals(-1050554150, output.groupIds.first())
  }

  @Test
  fun multipleEditsInOneUpdate() {
    val cache = initialCache(mapOf(
      "ComposeSimple.kt" to """
        import androidx.compose.runtime.Composable
        @Composable fun composableFun() : String {
          var str = "hi"
          return str
        }
        @Composable fun composableFun2() : String {
          return "hi2"
        }""",
      "ComposeNested.kt" to """
        import androidx.compose.runtime.Composable
        @Composable
        fun composableNested(): @Composable (Int) -> Unit {
          return { }
         }"""))

    // Testing an edit that has two files and three function modified.
    val file1 = projectRule.fixture.configureByText("ComposeSimple.kt", """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() : String {
        var str = "hello"
        return str
      }
      @Composable fun composableFun2() : String {
        return "hello"
      }""")
    val file2 = projectRule.fixture.configureByText("ComposeNested.kt", """
      import androidx.compose.runtime.Composable
      @Composable
      fun composableNested(): @Composable (Int) -> Unit {
        val x = 0
        return { val y = 0 }
      }""")
      val output = compile(listOf(
      LiveEditCompilerInput(file1, file1 as KtFile),
      LiveEditCompilerInput(file1, file1),
      LiveEditCompilerInput(file2, file2 as KtFile)), cache)

    Assert.assertEquals(4, output.classes.size)
    Assert.assertEquals(2, output.classesMap.size)
    Assert.assertEquals(2, output.supportClassesMap.size)
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
    val original = projectRule.compileIr("""
     import androidx.compose.runtime.Composable
     @Composable fun isComposable() {}
     fun notComposable() {} 
    """, "Mixed.kt")
    val cache = MutableIrClassCache()
    cache.update(original)

    val editComposable = projectRule.fixture.configureByText("Mixed.kt", """
     import androidx.compose.runtime.Composable
     @Composable fun isComposable() { val x = 0 }
     fun notComposable() {}
    """)

    var output = compile(editComposable, "isComposable", cache)
    Assert.assertEquals(-785806172, output.groupIds.first())
    Assert.assertFalse(output.resetState)

    val editNonComposable = projectRule.fixture.configureByText("Mixed.kt", """
     import androidx.compose.runtime.Composable
     @Composable fun isComposable() {}
     fun notComposable() { val x = 0 }
    """)

    output = compile(editNonComposable, "notComposable", cache)
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

  @Test
  fun sendAllThenOnlyChanges() {
    val cache = initialCache(mapOf(
      "ComposeSimple.kt" to """
        import androidx.compose.runtime.Composable
        @Composable fun composableFun() {
          val a = { }
        }
        @Composable fun composableFun2() {
          val a = { }
        }
        @Composable fun composableFun3() {
          val a = { }
        }
        @Composable fun composableFun4() {
          val a = { }
        }"""))
    val compiler = LiveEditCompiler(projectRule.project, cache)
    val file1 = projectRule.fixture.configureByText("ComposeSimple.kt", """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() {
        val a = { "hello "}
      }
      @Composable fun composableFun2() {
        val a = { }
      }
      @Composable fun composableFun3() {
        val a = { }
      }
      @Composable fun composableFun4() {
        val a = { }
      }""")

    val file2 = projectRule.fixture.configureByText("ComposeSimple.kt", """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() {
        val a = { "hello "}
      }
      @Composable fun composableFun2() {
        val a = { "hello" }
      }
      @Composable fun composableFun3() {
        val a = { }
      }
      @Composable fun composableFun4() {
        val a = { }
      }""")

    // First LE should send all classes, regardless of what has changed.
    val output = compile(listOf(LiveEditCompilerInput(file1, file1 as KtFile)), compiler)
    assertEquals(9, output.classes.size)
    assertEquals(1, output.classesMap.size)
    assertEquals(8, output.supportClassesMap.size)
    assertTrue(output.classesMap["ComposeSimpleKt"]!!.isNotEmpty())

    // Subsequent LE operations should resume sending only changed classes.
    val output2 = compile(listOf(LiveEditCompilerInput(file2, file2 as KtFile)), compiler)
    assertEquals(2, output2.classes.size)
    assertEquals(0, output2.classesMap.size)
    assertEquals(2, output2.supportClassesMap.size)
  }

  private fun initialCache(files: Map<String, String>): MutableIrClassCache {
    val cache = MutableIrClassCache()
    files.map { projectRule.compileIr(it.value, it.key) }.forEach { cache.update(it) }
    return cache
  }
}
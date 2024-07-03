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

import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.tools.deploy.proto.Deploy.LiveEditRequest.InvalidateMode
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.diff
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileIr
import com.android.tools.idea.run.deployment.liveedit.analysis.disableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.enableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.initialCache
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.modifyKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.onlyComposeDebugConstantChanges
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposableCompileTest {
  private var files = HashMap<String, PsiFile>()

  private var projectRule = AndroidProjectRule.inMemory().withKotlin()
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

  @Before
  fun setUp() {
    LiveEditAdvancedConfiguration.getInstance().useDebugMode = true
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()
  }

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun simpleComposeChange() {
    val file = projectRule.createKtFile("ComposeSimple.kt", """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() {
        var word = "Hello"
      }
      @Composable fun composableFun2() {
        var word = "World"
      }""")
    val cache = projectRule.initialCache(listOf(file))
    projectRule.modifyKtFile(file, """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() {
        var word = "Hi!!"
      }
      @Composable fun composableFun2() {
        var word = "World"
      }""")
    val output = compile(file, cache)
    // We can't really invoke any composable without a "host". Normally that host will be the
    // Android activity. There are other hosts that we can possibly run as a Compose unit test.
    // We could potentially look into doing that. However, for the purpose of verifying the
    // compose compiler was invoked correctly, we can just check the output's methods.
    Assert.assertTrue(output.classesMap["ComposeSimpleKt"]!!.isNotEmpty())

    Assert.assertEquals(-1332540612, output.groupIds.first())

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
    val file = projectRule.createKtFile("ComposeNested.kt" , """
      import androidx.compose.runtime.Composable
      @Composable
      fun caller() {
        composableNested()(0)
      }
      fun composableNested(): @Composable (Int) -> Unit {
        return { } // group 22704048
      }""")
    val cache = projectRule.initialCache(listOf(file))
    projectRule.modifyKtFile(file, """
      import androidx.compose.runtime.Composable
      @Composable
      fun caller() {
        composableNested()(1)
      }
      fun composableNested(): @Composable (Int) -> Unit {
        return { val y = 0 }
      }""")
    val output = compile(file, cache)
    Assert.assertTrue(-1369675262 in output.groupIds)
    Assert.assertTrue(22704048 in output.groupIds)
  }

  @Test
  @Ignore("b/327357129")
  fun multipleEditsInOneUpdate() {
    val simpleFile = projectRule.createKtFile("ComposeSimple.kt", """
        import androidx.compose.runtime.Composable
        @Composable fun composableFun() : String {
          var str = "hi"
          return str
        }
        @Composable fun composableFun2() : String {
          return "hi2"
        }""")
    val simpleState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(simpleFile) }
    val nestedFile = projectRule.createKtFile("ComposeNested.kt", """
        import androidx.compose.runtime.Composable
        @Composable
        fun composableNested(): @Composable (Int) -> Unit {
          return { }
         }""")
    val nestedState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(nestedFile) }
    val cache = projectRule.initialCache(listOf(simpleFile, nestedFile))

    // Testing an edit that has two files and three function modified.
    projectRule.modifyKtFile(simpleFile, """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() : String {
        var str = "hello"
        return str
      }
      @Composable fun composableFun2() : String {
        return "hello"
      }""")
    projectRule.modifyKtFile(nestedFile, """
      import androidx.compose.runtime.Composable
      @Composable
      fun composableNested(): @Composable (Int) -> Unit {
        val x = 0
        return { }
      }""")
      val output = compile(listOf(
      LiveEditCompilerInput(simpleFile, simpleState),
      LiveEditCompilerInput(nestedFile, nestedState)), cache)

    Assert.assertEquals(3, output.classes.size)
    Assert.assertEquals(2, output.classesMap.size)
    Assert.assertEquals(1, output.supportClassesMap.size)
    Assert.assertTrue(output.classesMap.get("ComposeSimpleKt")!!.isNotEmpty())
    Assert.assertTrue(output.classesMap.get("ComposeNestedKt")!!.isNotEmpty())

    Assert.assertEquals(3, output.groupIds.size)
    Assert.assertTrue("groupids = " + output.groupIds.toString(), output.groupIds.contains(1639534479))
    Assert.assertTrue("groupids = " + output.groupIds.toString(), output.groupIds.contains(-1050554150))
    Assert.assertTrue("groupids = " + output.groupIds.toString(), output.groupIds.contains(-1350204187))
    Assert.assertEquals(InvalidateMode.INVALIDATE_GROUPS, output.invalidateMode) // Compose only edits should not request for a full state reset.
  }

  @Test
  fun simpleMixed() {
    val file = projectRule.createKtFile("Mixed.kt", """
     import androidx.compose.runtime.Composable
     @Composable fun isComposable() {}
     fun notComposable() {}
    """)
    val cache = projectRule.initialCache(listOf(file))
    projectRule.modifyKtFile(file, """
     import androidx.compose.runtime.Composable
     @Composable fun isComposable() { val x = 0 }
     fun notComposable() {}
    """)

    var output = compile(file, cache)
    Assert.assertEquals(-785806172, output.groupIds.first())
    Assert.assertEquals(InvalidateMode.INVALIDATE_GROUPS, output.invalidateMode)

    val editNonComposable = projectRule.fixture.configureByText("Mixed.kt", """
     import androidx.compose.runtime.Composable
     @Composable fun isComposable() {}
     fun notComposable() { val x = 0 }
    """)

    output = compile(editNonComposable, cache)
    // Editing a normal Kotlin function should not result any group IDs. Instead, it should manually trigger a full state reset every edit.
    Assert.assertTrue(output.groupIds.isEmpty())
    Assert.assertEquals(InvalidateMode.SAVE_AND_LOAD, output.invalidateMode)
  }

  @Test
  fun testModuleName() {
    val file = projectRule.createKtFile("HasComposableSingletons.kt", """
      import androidx.compose.runtime.Composable
      @Composable fun hasLambdaA(content: @Composable () -> Unit) { }
      @Composable fun hasLambdaB() { hasLambdaA {} }
    """)
    val fileState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String) = apk[className]
    })
    val output = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    val singleton = output.supportClassesMap["ComposableSingletons\$HasComposableSingletonsKt"];
    Assert.assertNotNull(singleton)
    val cl = loadClass(output, "ComposableSingletons\$HasComposableSingletonsKt")
    val getLambda = cl.methods.find { it.name.contains("getLambda") }
    // Make sure we have getLambda$<MODULE_NAME>
    Assert.assertTrue(getLambda!!.name.contains(projectRule.module.name))
  }

  @Test
  fun sendAllThenOnlyChanges() {
    val file = projectRule.createKtFile("ComposeSimple.kt", """
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
      }""")
    val fileState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String) = apk[className]
    })

    projectRule.modifyKtFile(file, """
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

    // First LE should send all classes, regardless of what has changed.
    val output = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    assertEquals(9, output.classes.size)
    assertEquals(1, output.classesMap.size)
    assertEquals(8, output.supportClassesMap.size)
    assertTrue(output.classesMap["ComposeSimpleKt"]!!.isNotEmpty())

    cache.update(output.irClasses)

    projectRule.modifyKtFile(file, """
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

    // Subsequent LE operations should resume sending only changed classes.
    val output2 = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    assertEquals(1, output2.classes.size)
    assertEquals(0, output2.classesMap.size)
    assertEquals(1, output2.supportClassesMap.size)
  }

  private val modifierCode = """
      class Color(val value: Int) {
          companion object {
              val Red = Color(0)
          }
      }

      class Dp()
      val Int.dp: Dp get() = Dp()

      interface Modifier {
          companion object : Modifier
      }

      fun Modifier.background(c: Color) = this
      fun Modifier.size(size: Dp) = this
      fun Modifier.padding(size: Dp) = this
      """.trimIndent()

  // Regression test for invalid incremental analysis. See b/295257198.
  @Test
  fun incrementalAnalysisFunctionBodyTest() {
    val fileName = "Test.kt"
    val className = "TestKt"
    val file = projectRule.createKtFile(fileName, """
      $modifierCode
      fun foo() {
        Modifier.background(Color.Red).size(100.dp).padding(20.dp)
      }""".trimIndent())
    val fileState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, MutableIrClassCache(), object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String): IrClass? {
        return apk[className]
      }
    })
    projectRule.modifyKtFile(file, """
      $modifierCode
      fun foo() {
        Modifier.background(Color.Red).size(100.dp)
      }""")

    val output = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    Assert.assertTrue(output.classesMap[className]!!.isNotEmpty())
    val klass = loadClass(output, className)
    // Before the fix, invalid code will be generated and this will lead to a class
    // cast exception during execution.
    invokeStatic("foo", klass)
  }

  // Regression test for invalid incremental analysis. See b/295257198.
  @Test
  fun incrementalAnalysisFunctionExpressionBodyTest() {
    val fileName = "Test.kt"
    val className = "TestKt"
    val file = projectRule.createKtFile(fileName, """
      $modifierCode
      fun foo(): Modifier = Modifier.background(Color.Red).size(100.dp).padding(20.dp)""")
    val fileState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, MutableIrClassCache(), object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String): IrClass? {
        return apk[className]
      }
    })

    projectRule.modifyKtFile(file, """
      $modifierCode
      fun foo(): Modifier = Modifier.background(Color.Red).size(100.dp)""")

    val output = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    Assert.assertTrue(output.classesMap[className]!!.isNotEmpty())
    val klass = loadClass(output, className)
    // Before the fix, invalid code will be generated and this will lead to a class
    // cast exception during execution.
    invokeStatic("foo", klass)
  }

  // Regression test for incorrect fix for b/295257198 where we filtered the
  // parent context in the incremental analysis too much.
  @Test
  fun incrementalAnalysisFunctionBodyWithArgumentsTest() {
    val fileName = "Test.kt"
    val className = "TestKt"
    val file = projectRule.createKtFile(fileName, """
      fun bar() = foo(0)
      fun foo(l: Int): Int {
        return 32
      }""")
    val cache = projectRule.initialCache(listOf(file))

    projectRule.modifyKtFile(file,"""
      fun bar() = foo(0)
      fun foo(l: Int): Int {
        return 42
      }""")

    val output = compile(file, cache)
    Assert.assertTrue(output.classesMap[className]!!.isNotEmpty())
    val klass = loadClass(output, className)
    assertEquals(42, invokeStatic("bar", klass))
  }

  @Test
  fun incrementalAnalysisPropertyGetter() {
    val fileName = "Test.kt"
    val className = "TestKt"
    val file = projectRule.createKtFile(fileName, """
      $modifierCode
      class A {
        val y: Modifier
          get() = Modifier.background(Color.Red).size(100.dp).padding(20.dp)
      }
      fun bar(): Int {
        A().y
        return 42
      }""")
    val fileState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, MutableIrClassCache(), object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String): IrClass? {
        return apk[className]
      }
    })

    projectRule.modifyKtFile(file, """
      $modifierCode
      class A {
        val y: Modifier
          get() = Modifier.background(Color.Red).size(100.dp)
      }
      fun bar(): Int {
        A().y
        return 42
      }""")

    val output = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    Assert.assertTrue(output.classesMap[className]!!.isNotEmpty())
    val klass = loadClass(output, className)
    assertEquals(42, invokeStatic("bar", klass))
  }

  @Test
  fun testIgnoreTraceEventStart() {
    val file = projectRule.createKtFile("File.kt", """
      import androidx.compose.runtime.Composable
      @Composable fun composableFun() : String {
        var str = "hi"
        return str
      }
    """.trimIndent()) as KtFile

    val firstClass = projectRule.directApiCompileIr(file)["FileKt"]
    assertNotNull(firstClass)
    val firstMethod = firstClass.methods.first { it.name == "composableFun" }

    // Ensure we actually generated a traceEventStart() call
    assertNotNull(firstMethod.instructions.singleOrNull {
      it.opcode == Opcodes.INVOKESTATIC && it.params[0] == "androidx/compose/runtime/ComposerKt" && it.params[1] == "traceEventStart"
    })

    // Modifying the line numbers causes the traceEventStart() calls to change; unfortunately, it doesn't change the sourceInformation()
    // calls from within our test context. Not sure why.
    val content = """  
    import androidx.compose.runtime.Composable
     
      // Change the line offset of the @Composable to cause the argument to traceEventStart() to change
      @Composable fun composableFun() : String {
        var str = "hi"
        return str
      }
    """.trimIndent()

    projectRule.modifyKtFile(file, content)
    val secondClass = projectRule.directApiCompileIr(file)["FileKt"]
    assertNotNull(secondClass)
    val secondMethod = secondClass.methods.first { it.name == "composableFun" }

    // Ensure we actually generated a traceEventStart() call
    assertNotNull(secondMethod.instructions.singleOrNull {
      it.opcode == Opcodes.INVOKESTATIC && it.params[0] == "androidx/compose/runtime/ComposerKt" && it.params[1] == "traceEventStart"
    })

    assertNotNull(diff(firstClass, secondClass))
    assertTrue(onlyComposeDebugConstantChanges(firstMethod.instructions, secondMethod.instructions))
  }

  // Check for b/326306840
  @Test
  fun doNotIgnoreLdcChanges() {
    val file = projectRule.createKtFile("A.kt", """
      import androidx.compose.runtime.Composable
      @Composable
      fun method() {
        val color = method(0xFFFFFFFF)
      }
      fun method(a: Long) {
      }
    """.trimIndent())
    val fileState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, MutableIrClassCache(), object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String): IrClass? {
        return apk[className]
      }
    })
    projectRule.modifyKtFile(file,  """
      import androidx.compose.runtime.Composable
      @Composable
      fun method() {
        val color = method(0xFF00FFFF)
      }
      fun method(a: Long) {
      }
    """.trimIndent())
    val output = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    assertTrue(output.groupIds.isNotEmpty())
  }

  @Test
  fun mutableState() {
    val fileName = "Test.kt"
    val className = "TestKt"
    val file = projectRule.createKtFile(fileName, """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.getValue
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.remember
        import androidx.compose.runtime.setValue
        @Composable
        fun C() { }
      """)
    val fileState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(file) }
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, MutableIrClassCache(), object: ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String): IrClass? {
        return apk[className]
      }
    })

    projectRule.modifyKtFile(file, """
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.getValue
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.remember
        import androidx.compose.runtime.setValue
        @Composable
        fun C() {
            var c by remember { mutableStateOf(0) }
        }
      """)

    val output = compile(listOf(LiveEditCompilerInput(file, fileState)), compiler)
    Assert.assertTrue(output.classesMap[className]!!.isNotEmpty())
  }
}

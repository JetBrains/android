/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.objectweb.asm.tree.MethodInsnNode

private const val START_RESTART_GROUP = "startRestartGroup(I)Landroidx/compose/runtime/Composer;"
private const val START_MOVABLE_GROUP = "startMovableGroup(ILjava/lang/Object;)V"
private const val START_REPLACEABLE_GROUP = "startReplaceableGroup(I)V"
private const val START_REUSABLE_GROUP = "startReusableGroup(ILjava/lang/Object;)V"
private const val START_REPLACE_GROUP = "startReplaceGroup(I)V"

class ComposeAnalysisTest {
  private var projectRule = AndroidProjectRule.inMemory().withKotlin()
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")

  @get:Rule
  val chain: RuleChain = RuleChain.outerRule(projectRule).around(fakeAdb)

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()
  }

  @Test
  fun `single restartable group`() {
    val output = compileForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun test() {}
      """)
    ensureComposeCalls(output, START_RESTART_GROUP)
    val groupTable = computeGroupTableForTest(output)
    groupTable.assertGroupTable(groupCount = 1, restartLambdaCount = 1, lambdaGroupCount = 0, innerClassCount = 1)
    val test = groupTable.assertGroup(956630616)
    groupTable.assertRestartLambda(test)
    assertEquals("test", test.name)
  }

  @Test
  fun `two restartable groups`() {
    val output = compileForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun first() {}
      @Composable
      fun second() {}
      """)

    ensureComposeCalls(output, START_RESTART_GROUP)
    val groupTable = computeGroupTableForTest(output)
    groupTable.assertGroupTable(groupCount = 2, restartLambdaCount = 2, lambdaGroupCount = 0, innerClassCount = 2)

    val first = groupTable.assertGroup(-1619759206)
    groupTable.assertRestartLambda(first)
    assertEquals("first", first.name)

    val second = groupTable.assertGroup(-130088134)
    groupTable.assertRestartLambda(second)
    assertEquals("second", second.name)
  }

  @Test
  fun `two restartable groups with the same name`() {
    val output = compileForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun group() {}
      @Composable
      fun group(param: Int) {}
      """)
    ensureComposeCalls(output, START_RESTART_GROUP)
    val groupTable = computeGroupTableForTest(output)
    groupTable.assertGroupTable(groupCount = 2, restartLambdaCount = 2, lambdaGroupCount = 0, innerClassCount = 2)

    val first = groupTable.assertGroup(1872838441)
    groupTable.assertRestartLambda(first)
    assertEquals("group", first.name)
    assertEquals("(Landroidx/compose/runtime/Composer;I)V", first.desc)

    val second = groupTable.assertGroup(690094472)
    groupTable.assertRestartLambda(second)
    assertEquals("group", second.name)
    assertEquals("(ILandroidx/compose/runtime/Composer;I)V", second.desc)
  }

  @Test
  fun `composable with content`() {
    val output = compileForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun test() {
        outer {
          outer {
           outer { }
          }
        }
        outer { }
      }

      @Composable
      fun outer(content: @Composable () -> Unit) {
        content()
      }
      """)
    ensureComposeCalls(output, START_RESTART_GROUP)
    val groupTable = computeGroupTableForTest(output)

    groupTable.assertGroupTable(groupCount = 6, restartLambdaCount = 2, lambdaGroupCount = 4, innerClassCount = 2)

    val test = groupTable.assertGroup(956630616)
    groupTable.assertRestartLambda(test)
    assertEquals("test", test.name)

    val outer = if (!KotlinPluginModeProvider.isK2Mode()) {
      groupTable.assertGroup(169591811)
    } else {
      groupTable.assertGroup(-270222928)
    }
    groupTable.assertRestartLambda(outer)
    assertEquals("outer", outer.name)

    // We don't assert lambda names, because the compiler is free to name them anything without impacting the group analysis
    val lambda3 = groupTable.assertLambdaParent(58708456, test)
    val lambda2 = groupTable.assertLambdaParent(-2103565224, lambda3)
    groupTable.assertLambdaParent(302052328, lambda2)
    groupTable.assertLambdaParent(-289044847, test)
  }

  @Test
  fun `nested composable with captures`() {
    val output = compileForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun test() {
        val capture = 100
        outer {
          outer {
            outer {
              val usesCapture = capture
            }
          }
        }
      }

      @Composable
      fun outer(content: @Composable () -> Unit) {
        content()
      }
      """)
    ensureComposeCalls(output, START_RESTART_GROUP)
    val groupTable = computeGroupTableForTest(output)
    groupTable.assertGroupTable(groupCount = 5, restartLambdaCount = 2, lambdaGroupCount = 3, innerClassCount = 5)

    val test = groupTable.assertGroup(956630616)
    groupTable.assertRestartLambda(test)
    assertEquals("test", test.name)

    val outer = if (!KotlinPluginModeProvider.isK2Mode()) {
      groupTable.assertGroup(169591811)
    } else {
      groupTable.assertGroup(-270222928)
    }
    groupTable.assertRestartLambda(outer)
    assertEquals("outer", outer.name)

    // We don't assert lambda names, because the compiler is free to name them anything without impacting the group analysis
    val lambda1 = groupTable.assertLambdaParent(58708456, test)
    val lambda11 = groupTable.assertLambdaParent(-2103565224, lambda1)
    groupTable.assertLambdaParent(302052328, lambda11)
  }

  @Test
  fun `replaceable group`() {
    val output = compileForTest("""
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.remember
      @Composable
      fun compute(param: Int): Int {
        val state = remember(param) { param + 1 }
        return state
      }
      """)

    val groupTable = computeGroupTableForTest(output)

    // No restart lambdas for composable functions that return values
    groupTable.assertGroupTable(groupCount = 1, restartLambdaCount = 0, lambdaGroupCount = 0, innerClassCount = 0)

    if (KotlinPluginModeProvider.isK2Mode()) {
      ensureComposeCalls(output, START_REPLACE_GROUP)
      val compute = groupTable.assertGroup(1273468969)
      assertEquals("compute", compute.name)
    } else {
      ensureComposeCalls(output, START_REPLACEABLE_GROUP)
      val compute = groupTable.assertGroup(1273468969)
      assertEquals("compute", compute.name)
    }
  }

  @Test
  fun `restartable, replaceable, reusable, and movable groups`() {
    val output = compileForTest("""
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.remember
      import androidx.compose.runtime.mutableStateOf
      import androidx.compose.runtime.State
      import androidx.compose.runtime.ReusableContent
      import androidx.compose.runtime.key
      
      class Data(val id: String) {
        val word = mutableStateOf<String>("")
      }
      
      @Composable
      fun MyList(items: List<Data>) {
        if (items.size > 1) {
          items.forEach {
            key (it.id) {
              Text(it.word.value)
            }
            ReusableContent(it.id) {
              Text(it.word.value)
            }
          }
        }
      }
      
      @Composable
      fun Text(text: String) {
        // Pretend this does something
      }
      """)

    if (KotlinPluginModeProvider.isK2Mode()) {
      ensureComposeCalls(output, START_RESTART_GROUP, START_REUSABLE_GROUP, START_MOVABLE_GROUP)
    } else {
      ensureComposeCalls(output, START_RESTART_GROUP, START_REUSABLE_GROUP, START_MOVABLE_GROUP, START_REPLACEABLE_GROUP)
    }

    // This test doesn't care about the contents of the group table; only that we successfully construct it without errors
    computeGroupTableForTest(output)
  }


  /**
   * Asserts that the table contains a @Composable method with the provided key and returns that method.
   */
  private fun GroupTable.assertGroup(key: Int): IrMethod {
    val (method, _) = groups.filterValues { it.key == key }.entries.single()
    return method
  }

  /**
   * Asserts that the table contains a restart lambda associated with the provided @Composable method and returns that lambda class.
   */
  private fun GroupTable.assertRestartLambda(method: IrMethod): IrClass {
    return restartLambdas.filterValues { it == method }.keys.single()
  }

  /**
   * Asserts that the provided method is the parent of the lambda class associated with the provided group key. Returns the lambda's
   * invoke() method.
   */
  private fun GroupTable.assertLambdaParent(key: Int, parentMethod: IrMethod): IrMethod {
    val (method, _) = groups.filterValues { it.key == key }.entries.single()
    assertEquals(parentMethod, lambdaParents[method.clazz]!!)
    return method
  }

  private fun GroupTable.assertGroupTable(groupCount: Int, restartLambdaCount: Int, lambdaGroupCount: Int, innerClassCount: Int) {
    assertEquals(groupCount, groups.size)
    assertEquals(restartLambdaCount, restartLambdas.size)
    assertEquals(lambdaGroupCount, lambdaParents.size)
    assertEquals(innerClassCount, composableInnerClasses.size)
  }

  private class Output(val file: KtFile, val classes: List<IrClass>)

  private fun compileForTest(content: String): Output {
    val fileName = "Test"
    val file = projectRule.createKtFile("$fileName.kt", content)
    val output = projectRule.directApiCompileIr(file)
    val keyMeta = output["${fileName}Kt\$KeyMeta"]
    val classes = output.values.filterNot { it == keyMeta }
    return Output(file, classes)
  }

  private fun ensureComposeCalls(output: Output, vararg methods: String) {
    val notFound = methods.toMutableSet()
    val found = mutableSetOf<String>()
    output.classes.forEach { klass ->
      klass.methods.forEach { method ->
        method.node.instructions
          .filterIsInstance<MethodInsnNode>()
          .filter { it.owner == "androidx/compose/runtime/Composer" }
          .forEach {
            val method = it.name + it.desc
            found.add(method)
            if (method in notFound) {
              notFound.remove(method)
            }
          }
      }
    }
    if (notFound.isNotEmpty()) {
      fail("Composer calls that the test was expected to exercise were not found in the bytecode: $notFound\nFound: $found")
    }
  }

  private fun computeGroupTableForTest(output: Output): GroupTable {
    val groupTable = computeGroupTable(output.classes)
    println(groupTable.toStringWithLineInfo(output.file))
    return groupTable
  }
}

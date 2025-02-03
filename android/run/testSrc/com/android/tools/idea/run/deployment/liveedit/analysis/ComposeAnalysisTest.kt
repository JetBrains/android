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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

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
    val groupTable = computeGroupTableForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun test() {}
      """)
    groupTable.assertGroupTable(methodGroupCount = 1, restartLambdaCount = 1, lambdaGroupCount = 0, innerClassCount = 1)
    val test = groupTable.assertMethodGroup(956630616)
    groupTable.assertRestartLambda(test)
    assertEquals("test", test.name)
  }

  @Test
  fun `two restartable groups`() {
    val groupTable = computeGroupTableForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun first() {}
      @Composable
      fun second() {}
      """)
    groupTable.assertGroupTable(methodGroupCount = 2, restartLambdaCount = 2, lambdaGroupCount = 0, innerClassCount = 2)

    val first = groupTable.assertMethodGroup(-1619759206)
    groupTable.assertRestartLambda(first)
    assertEquals("first", first.name)

    val second = groupTable.assertMethodGroup(-130088134)
    groupTable.assertRestartLambda(second)
    assertEquals("second", second.name)
  }

  @Test
  fun `two restartable groups with the same name`() {
    val groupTable = computeGroupTableForTest("""
      import androidx.compose.runtime.Composable
      @Composable
      fun group() {}
      @Composable
      fun group(param: Int) {}
      """)
    groupTable.assertGroupTable(methodGroupCount = 2, restartLambdaCount = 2, lambdaGroupCount = 0, innerClassCount = 2)

    val first = groupTable.assertMethodGroup(1872838441)
    groupTable.assertRestartLambda(first)
    assertEquals("group", first.name)
    assertEquals("(Landroidx/compose/runtime/Composer;I)V", first.desc)

    val second = groupTable.assertMethodGroup(690094472)
    groupTable.assertRestartLambda(second)
    assertEquals("group", second.name)
    assertEquals("(ILandroidx/compose/runtime/Composer;I)V", second.desc)
  }

  @Test
  fun `composable with content`() {
    val groupTable = computeGroupTableForTest("""
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
    groupTable.assertGroupTable(methodGroupCount = 2, restartLambdaCount = 2, lambdaGroupCount = 4, innerClassCount = 2)

    val test = groupTable.assertMethodGroup(956630616)
    groupTable.assertRestartLambda(test)
    assertEquals("test", test.name)

    val outer = if (!KotlinPluginModeProvider.isK2Mode()) {
      groupTable.assertMethodGroup(169591811)
    } else {
      groupTable.assertMethodGroup(-270222928)
    }
    groupTable.assertRestartLambda(outer)
    assertEquals("outer", outer.name)

    // We don't assert lambda names, because the compiler is free to name them anything without impacting the group analysis
    val lambda3 = groupTable.assertLambdaGroup(58708456, test)
    val lambda2 = groupTable.assertLambdaGroup(-2103565224, lambda3)
    groupTable.assertLambdaGroup(302052328, lambda2)
    groupTable.assertLambdaGroup(-289044847, test)
  }

  @Test
  fun `nested composable with captures`() {
    val groupTable = computeGroupTableForTest("""
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
    groupTable.assertGroupTable(methodGroupCount = 2, restartLambdaCount = 2, lambdaGroupCount = 3, innerClassCount = 5)

    val test = groupTable.assertMethodGroup(956630616)
    groupTable.assertRestartLambda(test)
    assertEquals("test", test.name)

    val outer = if (!KotlinPluginModeProvider.isK2Mode()) {
      groupTable.assertMethodGroup(169591811)
    } else {
      groupTable.assertMethodGroup(-270222928)
    }
    groupTable.assertRestartLambda(outer)
    assertEquals("outer", outer.name)

    // We don't assert lambda names, because the compiler is free to name them anything without impacting the group analysis
    val lambda1 = groupTable.assertLambdaGroup(58708456, test)
    val lambda11 = groupTable.assertLambdaGroup(-2103565224, lambda1)
    groupTable.assertLambdaGroup(302052328, lambda11)
  }

  @Test
  fun `composable with return value`() {
    val groupTable = computeGroupTableForTest("""
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.remember
      @Composable
      fun compute(param: Int): Int {
        val state = remember(param) { param + 1 }
        return state
      }
      """)
    // No restart lambdas for composable functions that return values
    groupTable.assertGroupTable(methodGroupCount = 1, restartLambdaCount = 0, lambdaGroupCount = 0, innerClassCount = 0)
    val compute = groupTable.assertMethodGroup(1273468969)
    assertEquals("compute", compute.name)
  }

  @Test
  fun `using composable with return value`() {
    val groupTable = computeGroupTableForTest("""
      import androidx.compose.runtime.Composable
      import androidx.compose.runtime.remember
      @Composable
      fun compute(param: Int): Int {
        val state = remember(param) { param + 1 }
        return state
      }
      """)
    groupTable.assertGroupTable(methodGroupCount = 1, restartLambdaCount = 0, lambdaGroupCount = 0, innerClassCount = 0)
  }

  private fun GroupTable.assertMethodGroup(key: Int): IrMethod {
    val (method, _) = methodGroups.filterValues { it.key == key }.entries.single()
    return method
  }

  private fun GroupTable.assertRestartLambda(method: IrMethod): IrClass {
    return restartLambdas.filterValues { it == method }.keys.single()
  }

  private fun GroupTable.assertLambdaGroup(key: Int, parentClass: IrClass): IrClass {
    val (clazz, _) = lambdaGroups.filterValues { it.key == key }.entries.single()
    assertEquals(parentClass, lambdaParents[clazz]!!.clazz)
    return clazz
  }

  private fun GroupTable.assertLambdaGroup(key: Int, parentMethod: IrMethod): IrClass {
    val (clazz, _) = lambdaGroups.filterValues { it.key == key }.entries.single()
    assertEquals(parentMethod, lambdaParents[clazz]!!)
    return clazz
  }

  private fun GroupTable.assertGroupTable(methodGroupCount: Int, restartLambdaCount: Int, lambdaGroupCount: Int, innerClassCount: Int) {
    assertEquals(methodGroupCount, methodGroups.size)
    assertEquals(restartLambdaCount, restartLambdas.size)
    assertEquals(lambdaGroupCount, lambdaGroups.size)
    assertEquals(lambdaGroupCount, lambdaParents.size)
    assertEquals(innerClassCount, composableInnerClasses.size)
  }

  private fun computeGroupTableForTest(content: String): GroupTable {
    val fileName = "Test"
    val file = projectRule.createKtFile("$fileName.kt", content)
    val output = projectRule.directApiCompileIr(file)
    val keyMeta = output["${fileName}Kt\$KeyMeta"]

    val classes = output.values.filterNot { it == keyMeta }
    val groups = parseComposeGroups(keyMeta!!)
    val groupTable = computeGroupTable(classes, groups)
    println(groupTable.toStringWithLineInfo(file))
    return groupTable
  }
}
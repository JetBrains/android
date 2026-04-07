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
package com.android.tools.compose.debug

import com.android.testutils.runInDebuggerThread
import com.android.tools.compose.debug.utils.mockDebugProcess
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.openapi.project.Project
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class ComposePositionManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = projectRule.project

  @Test
  fun testComposeSingletonClasses() = runTest {
    val source =
      """
      package a;
      import androidx.compose.runtime.Composable

      class A {
        fun f() {
          g {
           f()
          }
        }

        fun g(@Composable () -> Unit) {}
      }
      """
        .trimIndent()
    val file = projectRule.fixture.addFileToProject("src/a/test.kt", source)

    val debugProcess =
      mockDebugProcess(project, projectRule.testRootDisposable) {
        classType("a.A") {
          method("f", lines = listOf(4, 5, 6, 7, 8))
          method("g", lines = listOf(10))
        }

        classType("a.ComposableSingletons\$TestKt")

        classType("a.ComposableSingletons\$TestKt\$lambda-1") { method("invoke", lines = listOf(5, 6, 7)) }
      }

    runInDebuggerThread(debugProcess) {
      val composePositionManager = ComposePositionManagerFactory().createPositionManager(debugProcess) as ComposePositionManager
      val position = SourcePosition.createFromLine(file, 5)
      composePositionManager.createPrepareRequests(mock(), position)

      val refs = composePositionManager.getRefs(position) - debugProcess.kotlinPositionManagerRefs(position)

      assertThat(debugProcess.prepareRequestPatterns).contains("a.ComposableSingletons\$TestKt$*")
      assertThat(refs).containsExactly("a.ComposableSingletons\$TestKt\$lambda-1")
    }
  }

  @Test
  fun testComposeSingletonClassesJvmName() = runTest {
    val source =
      """
      @file:JvmName("FileClass")
      package a;
      import androidx.compose.runtime.Composable

      fun f() {
        g {
          f()
        }
      }

      fun g(@Composable () -> Unit) {}
      """
        .trimIndent()
    val file = projectRule.fixture.addFileToProject("src/a/test2.kt", source)

    val debugProcess =
      mockDebugProcess(project, projectRule.testRootDisposable) {
        classType("a.FileClass") {
          method("f", lines = listOf(4, 5, 6, 7, 8))
          method("g", lines = listOf(10))
        }

        classType("a.ComposableSingletons\$Test2Kt")

        classType("a.ComposableSingletons\$Test2Kt\$lambda-1") { method("invoke", lines = listOf(5, 6, 7)) }
      }
    runInDebuggerThread(debugProcess) {
      val composePositionManager = ComposePositionManagerFactory().createPositionManager(debugProcess) as ComposePositionManager
      val sourcePosition = SourcePosition.createFromLine(file, 5)
      composePositionManager.createPrepareRequests(mock(), sourcePosition)

      val refs = composePositionManager.getRefs(sourcePosition) - debugProcess.kotlinPositionManagerRefs(sourcePosition)

      assertThat(debugProcess.prepareRequestPatterns).contains("a.ComposableSingletons\$Test2Kt$*")
      assertThat(refs).containsExactly("a.ComposableSingletons\$Test2Kt\$lambda-1")
    }
  }
}

private fun PositionManager.getRefs(position: SourcePosition) = getAllClasses(position).mapTo(HashSet()) { it.name() }

private fun DebugProcess.kotlinPositionManagerRefs(position: SourcePosition) = KotlinPositionManager(this).getRefs(position)

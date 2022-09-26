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
package com.android.tools.idea.editors.literals

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
internal class CompilerLiveLiteralsManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  @get:Rule
  val fastPreviewRule = SetFlagRule(StudioFlags.COMPOSE_FAST_PREVIEW, true)
  @Test
  fun `check invalid class file does not throw`() {
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
        package test.app

        private val testVal = "TEST"

        fun testCall() {
            println(name = "NAME ${'$'}testVal")
        }
      """)
    val outputDir = Files.createTempDirectory("output")
    val srcDir = Files.createDirectories(outputDir.resolve(Paths.get("test/app")))
    Files.createFile(srcDir.resolve("LiteralsTestKt.class"))
    Files.createFile(srcDir.resolve("LiveLiterals${'$'}LiteralsTestKt.class"))

    ModuleClassLoaderOverlays.getInstance(projectRule.fixture.module).overlayPath = outputDir
    runBlocking {
      // This should not throw even though the classes are invalid
      CompilerLiveLiteralsManager.getInstance().find(file)
    }
  }

  @Test
  fun `verify finding of relative and absolute path literals`() {
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
        package test.app

        private val testVal = "TEST"

        fun testCall() {
            println(name = "NAME ${'$'}testVal")
        }
      """)

    run {
      val path = file.virtualFile.path
      val compilerLiveLiteralsManager = CompilerLiveLiteralsManager.getTestInstance {
        listOf(
          CompilerLiveLiteralsManager.CompilerLiteralDefinition(path, 10),
          CompilerLiveLiteralsManager.CompilerLiteralDefinition(path, 20),
          CompilerLiveLiteralsManager.CompilerLiteralDefinition(path, 30)
        )
      }
      runBlocking {
        val finder = compilerLiveLiteralsManager.find(file)
        assertTrue(finder.hasCompilerLiveLiteral(file, 10))
        assertFalse(finder.hasCompilerLiveLiteral(file, 15))
        assertTrue(finder.hasCompilerLiveLiteral(file, 20))
        assertTrue(finder.hasCompilerLiveLiteral(file, 30))
      }
    }

    run {
      val path = file.getRelativePath()!!
      val compilerLiveLiteralsManager = CompilerLiveLiteralsManager.getTestInstance {
        listOf(
          CompilerLiveLiteralsManager.CompilerLiteralDefinition(path, 10),
          CompilerLiveLiteralsManager.CompilerLiteralDefinition(path, 20),
          CompilerLiveLiteralsManager.CompilerLiteralDefinition(path, 30)
        )
      }
      runBlocking {
        val finder = compilerLiveLiteralsManager.find(file)
        assertTrue(finder.hasCompilerLiveLiteral(file, 10))
        assertFalse(finder.hasCompilerLiveLiteral(file, 15))
        assertTrue(finder.hasCompilerLiveLiteral(file, 20))
        assertTrue(finder.hasCompilerLiveLiteral(file, 30))
      }
    }
  }
}
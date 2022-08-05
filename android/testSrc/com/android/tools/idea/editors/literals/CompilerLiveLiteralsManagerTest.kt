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
      CompilerLiveLiteralsManager.find(file)
    }
  }
}
/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.flags.junit.FlagRule
import com.android.tools.compose.debug.CanonicalFileService.JarDetector
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.RuleChain
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Rule
import org.junit.Test

class CanonicalFileServiceTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val fakeJarDetector = FakeJarDetector()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      FlagRule(StudioFlags.COMPOSE_CLASS_NAME_CALCULATOR_CANONICAL_FILE_CACHE, true),
    )

  @Test
  fun getCanonicalFile_differentFileNames() {
    val file1 = createFile("src", "File1", "com.test")
    val file2 = createFile("src", "File2", "com.test")
    val service = CanonicalFileService(projectRule.project)

    runReadAction {
      assertThat(service.getCanonicalFile(file1)).isEqualTo(file1)
      assertThat(service.getCanonicalFile(file2)).isEqualTo(file2)
    }
  }

  @Test
  fun getCanonicalFile_differentPackages() {
    val file1 = createFile("src", "File", "com.test1")
    val file2 = createFile("src", "File", "com.test2")
    val service = CanonicalFileService(projectRule.project)

    runReadAction {
      assertThat(service.getCanonicalFile(file1)).isEqualTo(file1)
      assertThat(service.getCanonicalFile(file2)).isEqualTo(file2)
    }
  }

  @Test
  fun getCanonicalFile_sameFileFromDifferentJars() {
    val file1 = createFile("jar1", "File", "com.test")
    val file2 = createFile("jar2", "File", "com.test")
    val service = CanonicalFileService(fakeJarDetector)

    runReadAction {
      assertThat(service.getCanonicalFile(file1)).isEqualTo(file1)
      assertThat(service.getCanonicalFile(file2)).isEqualTo(file1)
    }
  }

  @Test
  fun getCanonicalFile_sameFiles_sourcePreferred() {
    val file1 = createFile("jar1", "File", "com.test")
    val file2 = createFile("src", "File", "com.test")
    val service = CanonicalFileService(fakeJarDetector)

    runReadAction {
      assertThat(service.getCanonicalFile(file1)).isEqualTo(file1)
      assertThat(service.getCanonicalFile(file2)).isEqualTo(file2)
    }
  }

  private fun createFile(dir: String, name: String, packageName: String): KtFile {
    val file =
      projectRule.fixture.addFileToProject(
        "$dir/${packageName.replace('.', '/')}/$name.kt",
        """
          package $packageName
        """
          .trimIndent(),
      )
    return file as KtFile
  }

  private fun runReadAction(block: () -> Unit) {
    ApplicationManager.getApplication().runReadAction(block)
  }

  private class FakeJarDetector : JarDetector {
    override fun isFileInJar(file: KtFile) = file.virtualFilePath.contains("jar")
  }
}

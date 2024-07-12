/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompile
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileIr
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.util.containers.stream
import junit.framework.Assert
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ModuleCompileTest {

  val libModule1Name = "lib1"
  val libModule2Name = "lib2"

  @get:Rule
  val projectRule =
    AndroidProjectRule
      // The light weight inMemory() version does not support modules modifications.
      .onDisk(extraModules = listOf(libModule1Name, libModule2Name))
      .withKotlin()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun testLibModule() {
    val file = projectRule.fixture.addFileToProject(
      "$libModule1Name/src/B.kt",
      """
        public class B() {
          internal fun foo(): Int = 2
        }
      """.trimIndent(),
    ) as KtFile
    val cache = MutableIrClassCache()

    // Direct API compile assumes all files are in the same module, so we need to invoke it once per file.
    val apk = projectRule.directApiCompileIr(file).toMutableMap()
    apk.putAll(projectRule.directApiCompileIr(file))

    val compiler = LiveEditCompiler(projectRule.project, cache, object : ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String) = apk[className]
    })

    val output = compile(listOf(LiveEditCompilerInput(file, readPsiValidationState(file))), compiler)
    val clazz = loadClass(output)
    Assert.assertTrue(clazz.declaredMethods.stream().anyMatch {it.name.contains("foo\$$libModule1Name")})
  }

  @Test
  fun testDifferentModules() {
    val file1 = projectRule.fixture.addFileToProject(
      "$libModule1Name/src/A.kt",
      """
        public class A() {
          internal fun foo(): Int = 2
        }
      """.trimIndent(),
    ) as KtFile
    val file2 = projectRule.fixture.addFileToProject(
      "$libModule2Name/src/B.kt",
      """
        public class B() {
          internal fun bar(): Int = 2
        }
      """.trimIndent(),
    ) as KtFile
    val cache = MutableIrClassCache()

    // Direct API compile assumes all files are in the same module, so we need to invoke it once per file.
    val apk = projectRule.directApiCompileIr(file1).toMutableMap()
    apk.putAll(projectRule.directApiCompileIr(file2))

    val compiler = LiveEditCompiler(projectRule.project, cache, object : ApkClassProvider {
      override fun getClass(ktFile: KtFile, className: String) = apk[className]
    })

    val output = compile(listOf(LiveEditCompilerInput(file1, readPsiValidationState(file1)),
                                LiveEditCompilerInput(file2, readPsiValidationState(file2))), compiler)

    val clazzA = loadClass(output, "A")
    Assert.assertTrue(clazzA.declaredMethods.stream().anyMatch { it.name.contains("foo\$$libModule1Name") })

    val clazzB = loadClass(output, "B")
    Assert.assertTrue(clazzB.declaredMethods.stream().anyMatch { it.name.contains("bar\$$libModule2Name") })
  }

  @Test
  fun `Single compiler invocation with files in different modules`() {
    val file1 = projectRule.fixture.addFileToProject(
      "$libModule1Name/src/A.kt",
      """
        public class A() {
          internal fun foo(): Int = 2
        }
      """.trimIndent(),
    ) as KtFile
    val file2 = projectRule.fixture.addFileToProject(
      "$libModule2Name/src/B.kt",
      """
        public class B() {
          internal fun bar(): Int = 2
        }
      """.trimIndent(),
    ) as KtFile
    if (!KotlinPluginModeProvider.isK2Mode()) {
      try {
        projectRule.directApiCompile(listOf(file1, file2))
        Assert.fail("Expecting LiveEditUpdateException")
      }
      catch (l: LiveEditUpdateException) {
        // TODO: This test is wrong. We should *NOT* be getting a null pointer exception if you allow files of two different module to be
        //  compiled at once. I suspect we are not setting up the modules correctly for it to mirror an actual Android project set up.
        Assert.assertTrue(l.cause is NullPointerException)
      }
    } else {
      val cache = MutableIrClassCache()
      val apk = projectRule.directApiCompileIr(listOf(file1, file2))
      val compiler = LiveEditCompiler(projectRule.project, cache, object : ApkClassProvider {
        override fun getClass(ktFile: KtFile, className: String) = apk[className]
      })

      val output = compile(listOf(LiveEditCompilerInput(file1, readPsiValidationState(file1)),
                                  LiveEditCompilerInput(file2, readPsiValidationState(file2))), compiler)

      val clazzA = loadClass(output, "A")
      Assert.assertTrue(clazzA.declaredMethods.stream().anyMatch { it.name.contains("foo\$$libModule1Name") })

      val clazzB = loadClass(output, "B")
      Assert.assertTrue(clazzB.declaredMethods.stream().anyMatch { it.name.contains("bar\$$libModule2Name") })
    }
  }

  private fun readPsiValidationState(file: PsiFile): PsiState {
    return runReadAction {
      getPsiValidationState(file)
    }
  }
}

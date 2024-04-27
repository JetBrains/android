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
package com.android.tools.idea.gradle.refactoring

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@RunsInEdt
class GradleCatalogTomlVetoConditionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var vetoCondition: GradleCatalogTomlVetoCondition
  private lateinit var tomlFile: VirtualFile

  @Before
  fun setUp() {
    runWriteAction {
      tomlFile = projectRule.fixture.tempDirFixture.createFile("libs.versions.toml")
      vetoCondition = GradleCatalogTomlVetoCondition()
    }
  }
  @Test
  fun testDeclaration(){
    doTest("""
      [versions]
      vers|ion = "123"
    """.trimIndent(), false)

    doTest("""
      [libraries]
      declara|tion = "123"
    """.trimIndent(), false)

    doTest("""
      [bundles]
      bu|ndle = [ "123" ]
    """.trimIndent(), false)

    doTest("""
      [plugins]
      plu|gin = "123"
    """.trimIndent(), false)

  }

  @Test
  fun testTables(){
    doTest("""
      [vers|ions]
    """.trimIndent(), true)

    doTest("""
      [lib|aries]
      declara|tion = "123"
    """.trimIndent(), true)

    doTest("""
      [ran|dom]
    """.trimIndent(), true)
  }

  @Test
  fun testOtherKeyElements(){
    doTest("""
      [versions]
      version = { requ|ire = "1.1" }
    """.trimIndent(), true)

    doTest("""
      [libaries]
      declaration = { version.re|f = "some"}
    """.trimIndent(), true)
  }

  private fun doTest(tomlContent: String, expectedResult: Boolean) {
    val caret = tomlContent.indexOf('|')
    assertWithMessage("The tomlContent must include | somewhere to point to the caret position").that(caret).isNotEqualTo(-1)

    val withoutCaret = tomlContent.substring(0, caret) + tomlContent.substring(caret + 1)
    runWriteAction { VfsUtil.saveText(tomlFile, withoutCaret) }

    projectRule.fixture.openFileInEditor(tomlFile)
    runReadAction {
      val element = PsiManager.getInstance(projectRule.project).findFile(tomlFile)!!.findElementAt(caret)?.parent
      assertEquals(expectedResult, vetoCondition.test(element))
    }
  }
}
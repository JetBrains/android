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
package com.android.tools.idea.gradle.catalog

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class VersionCatalogDependencyReferenceContributorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var tomlFile: VirtualFile

  @Before
  fun setUp() {
    runWriteAction {
      tomlFile = projectRule.fixture.tempDirFixture.createFile("libs.versions.toml")
    }
  }
  @Test
  fun testSimpleBundle(){
    doTest("""
      [libraries]
      li^b = "group:name:1.0"
      [bundles]
      bundle = [ "li|b" ]
    """.trimIndent())

    doTest("""
      [libraries]
      lib = "group:name:1.0"
      li^b2 = "group:name:1.0"
      [bundles]
      bundle = [ "lib", "li|b2" ]
    """.trimIndent())

    doTest("""
      [libraries]
      lib = "group:name:1.0"
      li^b2 = "group:name:1.0"
      [bundles]
      bundle = [ "lib", "lib2" ]
      bundle2 = [ "lib", "li|b2" ]
    """.trimIndent())
  }

  @Test
  fun testCatalogWithSameNames(){
    doTest("""
      [plugins]
      lib = "name:1.0"
      [libraries]
      li^b = "group:name:1.0"
      [bundles]
      b = [ "li|b" ]
    """.trimIndent())

    doTest("""
      [versions]
      lib = "1.0"
      [libraries]
      li^b = "group:name:1.0"
      [bundles]
      b = [ "li|b" ]
    """.trimIndent())
  }

  @Test
  fun testFailedReference() {
    val content = """
      [bundles]
      b = [ "lib" ]
    """.trimIndent()
    runWriteAction { VfsUtil.saveText(tomlFile, content) }
    projectRule.fixture.openFileInEditor(tomlFile)
    runReadAction {
      val file = PsiManager.getInstance(projectRule.project).findFile(tomlFile)!!
      val referee = file.findElementAt(content.indexOf("lib"))!!.parent
      assertThat(referee.references.size).isAtLeast(1)
      assertThat(referee.references.any { it.resolve() == null }).isTrue()
    }
  }

  private fun doTest(tomlContent: String) {
    val caret = tomlContent.indexOf('|')
    Truth.assertWithMessage("The tomlContent must include | somewhere to point to the caret position").that(caret).isNotEqualTo(-1)

    val withoutCaret = tomlContent.substring(0, caret) + tomlContent.substring(caret + 1)

    val resolvedElementPosition = withoutCaret.indexOf('^')
    Truth.assertWithMessage("The tomlContent must include ^ somewhere to point the resolved element").that(caret).isNotEqualTo(-1)

    val cleanToml = withoutCaret.substring(0, resolvedElementPosition) + withoutCaret.substring(resolvedElementPosition + 1)

    runWriteAction { VfsUtil.saveText(tomlFile, cleanToml) }

    projectRule.fixture.openFileInEditor(tomlFile)
    runReadAction {
      val file = PsiManager.getInstance(projectRule.project).findFile(tomlFile)!!
      val referee = file.findElementAt(caret)!!.parent
      assertThat(referee.references.size).isAtLeast(1)
      val resolved = file.findElementAt(resolvedElementPosition)!!.parent
      assertThat(referee.references.map(PsiReference::resolve)).contains(resolved)
    }
  }

}
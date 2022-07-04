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
package com.android.tools.idea.gradle.dsl.parser.toml

import com.android.testutils.MockitoKt
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import org.junit.Test

class TomlDslChangerTest : PlatformTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.GRADLE_DSL_TOML_SUPPORT.override(true)
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
  }

  override fun tearDown() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    StudioFlags.GRADLE_DSL_TOML_SUPPORT.clearOverride()
    super.tearDown()
  }

  @Test
  fun testDeleteSingleLiteral() {
    val toml = """
      foo = "bar"
    """.trimIndent()
    val expected = ""
    doTest(toml, expected) { removeProperty("foo") }
  }

  @Test
  fun testDeleteMiddleLiteral() {
    val toml = """
      one = "one"
      two = "two"
      three = "three"
    """.trimIndent()
    val expected = """
      one = "one"
      three = "three"
    """.trimIndent()
    doTest(toml, expected) { removeProperty("two") }
  }

  @Test
  fun testDeleteSingleLiteralInTable() {
    val toml = """
      [table]
      foo = "bar"
    """.trimIndent()
    val expected = """
      [table]
      
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("table") as? GradleDslExpressionMap)?.removeProperty("foo") }
  }

  private fun doTest(toml: String, expected: String, changer: GradleDslFile.() -> Unit) {
    val libsTomlFile = writeLibsTomlFile(toml)
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, MockitoKt.mock())) {}
    dslFile.parse()
    changer(dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val text = VfsUtil.loadText(libsTomlFile).replace("\r", "")
    assertEquals(expected, text)
  }

  private fun writeLibsTomlFile(text: String): VirtualFile {
    lateinit var libsTomlFile: VirtualFile
    runWriteAction {
      val baseDir = getOrCreateProjectBaseDir()
      val gradlePath = VfsUtil.createDirectoryIfMissing(baseDir, "gradle")
      libsTomlFile = gradlePath.createChildData(this, "libs.versions.toml")
      VfsUtil.saveText(libsTomlFile, text)
    }
    return libsTomlFile
  }
}
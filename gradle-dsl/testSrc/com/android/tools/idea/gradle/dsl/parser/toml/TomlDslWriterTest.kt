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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase

class TomlDslWriterTest : PlatformTestCase() {
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

  fun testSingleLiteral() {
    val contents = mapOf("foo" to "bar")
    val expected = """
      foo = "bar"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testSingleTable() {
    val contents = mapOf("foo" to mapOf("bar" to "baz"))
    val expected = """
      [foo]
      bar = "baz"
    """.trimIndent()

    doTest(contents, expected)
  }

  private fun doTest(contents: Map<String,Any>, expected: String) {
    val libsTomlFile = writeLibsTomlFile("")
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, MockitoKt.mock())) {}
    dslFile.parse()
    mapToProperties(contents, dslFile)
    runWriteCommandAction(project) {
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

  private fun mapToProperties(map: Map<String,Any>, dslFile: GradleDslFile) {
    fun populate(key: String, value: Any, element: GradlePropertiesDslElement) {
      when (value) {
        is String -> element.setNewLiteral(key, value)
        is Map<*,*> -> {
          val dslMap = GradleDslExpressionMap(element, GradleNameElement.create(key))
          value.forEach { (k, v) -> populate(k as String, v as Any, dslMap) }
          element.setNewElement(dslMap)
        }
      }
    }
    map.forEach { (k, v) -> populate(k, v, dslFile) }
  }
}
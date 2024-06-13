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
package com.android.tools.idea.gradle.dsl.parser.catalog

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil.createFile
import org.junit.Test
import org.mockito.Mockito

class CatalogTomlDslChangerTest : LightPlatformTestCase() {

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

  @Test
  fun testDeleteSingleLiteralInSegmentedTable() {
    val toml = """
      [table1.table2]
      foo = "bar"
    """.trimIndent()
    val expected = """
      [table1.table2]

    """.trimIndent()
    doTest(toml, expected) {
      val table1 = (getPropertyElement("table1") as? GradleDslExpressionMap)
      val table2  = table1?.getPropertyElement("table2") as? GradleDslExpressionMap
      table2?.removeProperty("foo")
    }
  }

  @Test
  fun testDeleteSingleLiteralInInlineTable() {
    val toml = """
      foo = { bar = "baz" }
    """.trimIndent()
    val expected = """
      foo = { }
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionMap)?.removeProperty("bar") }
  }

  @Test
  fun testDeleteSingleLiteralInArray() {
    val toml = """
      foo = ["bar"]
    """.trimIndent()
    val expected = """
      foo = []
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionList)?.run { removeProperty(getElementAt(0)) } }
  }

  private fun doTest(toml: String, expected: String, changer: GradleDslFile.() -> Unit) {
    val libsTomlFile = createFile(
      project.guessProjectDir()!!,
      "gradle/libs.versions.toml",
      toml
    )
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    dslFile.parse()
    changer(dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val text = VfsUtil.loadText(libsTomlFile).replace("\r", "")
    assertEquals(expected, text)
  }
}
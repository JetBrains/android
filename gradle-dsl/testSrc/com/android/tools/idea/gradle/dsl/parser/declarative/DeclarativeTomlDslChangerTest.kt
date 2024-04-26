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
package com.android.tools.idea.gradle.dsl.parser.declarative

import com.android.testutils.MockitoKt
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test

class DeclarativeTomlDslChangerTest : LightPlatformTestCase() {

  override fun setUp(){
    Registry.`is`("android.gradle.declarative.plugin.studio.support", true)
    super.setUp()
  }

  fun testDeleteFromArrayTable() {
    val toml = """
      [[a]]
      foo = "foo"
      bar = "bar"
    """.trimIndent()
    val expected = """
      [[a]]
      foo = "foo"

    """.trimIndent()
    doTest(toml, expected) {
      val map = ((elements["a"] as GradleDslExpressionList).getElementAt(0) as GradleDslExpressionMap)
      map.removeProperty("bar")
    }
  }

  @Test
  fun testDeleteSingleLiteralInTable() {
    val toml = """
      [table]
      foo = "bar"
    """.trimIndent()
    val expected = ""
    doTest(toml, expected) { (getPropertyElement("table") as? GradleDslExpressionMap)?.removeProperty("foo") }
  }

  @Test
  fun testDeleteSingleLiteralInSegmentedTable() {
    val toml = """
      [table1.table2]
      foo = "bar"
    """.trimIndent()
    val expected = ""
    doTest(toml, expected) {
      val table1 = (getPropertyElement("table1") as? GradleDslExpressionMap)
      val table2  = table1?.getPropertyElement("table2") as? GradleDslExpressionMap
      table2?.removeProperty("foo")
    }
  }

  @Test
  fun testDeleteLastFromSegmentedTable() {
    val toml = """
      [a.b]
      foo = "foo"
      bar = "bar"
    """.trimIndent()
    val expected = """
      [a.b]
      foo = "foo"

    """.trimIndent()
    doTest(toml, expected) {
      ((elements["a"] as GradleDslExpressionMap)
        .getElement("b") as GradleDslExpressionMap)
        .removeProperty("bar")
    }
  }

  fun testDeleteFirstFromSegmentedTable() {
    val toml = """
      [a.b]
      foo = "foo"
      bar = "bar"
    """.trimIndent()
    val expected = """
      [a.b]
      bar = "bar"
    """.trimIndent()
    doTest(toml, expected) {
      ((elements["a"] as GradleDslExpressionMap)
        .getElement("b") as GradleDslExpressionMap)
        .removeProperty("foo")
    }
  }

  fun testDeleteMiddleFromSegmentedTable() {
    val toml = """
      [a.b]
      foo = "foo"
      bar = "bar"
      baz = "baz"
    """.trimIndent()
    val expected = """
      [a.b]
      foo = "foo"
      baz = "baz"
    """.trimIndent()
    doTest(toml, expected) {
      ((elements["a"] as GradleDslExpressionMap)
        .getElement("b") as GradleDslExpressionMap)
        .removeProperty("bar")
    }
  }

  @Test
  fun testAddToArrayTable() {
    val toml = """
      [[a]]
      foo = "foo"
    """.trimIndent()
    val expected = """
      [[a]]
      foo = "foo"
      bar = "bar"
    """.trimIndent()
    doTest(toml, expected) {
      val map = ((elements["a"] as GradleDslExpressionList).getElementAt(0) as GradleDslExpressionMap)
      map.addNewLiteral("bar","bar")
    }
  }

  // Toml does not allow to have duplicate tables - so next cases are mostly about that we don't crash
  @Test
  fun testUpdateInSplitElement() {
    val toml = """
      [a]
      foo = "foo"
      [a]
      bar = "bar"
    """.trimIndent()
    val expected = """
      [a]
      foo = "foo_updated"
      [a]
      bar = "bar"
    """.trimIndent()
    doTest(toml, expected) {
      ((elements["a"] as GradleDslExpressionMap).getElement("foo") as GradleDslLiteral).setValue("foo_updated")
    }
  }
  @Test
  fun testUpdateInSplitElement2() {
    val toml = """
      [a]
      foo = "foo"
      [a]
      bar = "bar"
    """.trimIndent()
    val expected = """
      [a]
      foo = "foo"
      [a]
      bar = "bar_updated"
    """.trimIndent()
    doTest(toml, expected) {
      ((elements["a"] as GradleDslExpressionMap).getElement("bar") as GradleDslLiteral).setValue("bar_updated")
    }
  }

  fun testAddIntoASplitElement() {
    val toml = """
      [a]
      foo = "foo"
      [a]
      bar = "bar"
    """.trimIndent()
    val expected = """
      [a]
      foo = "foo"
      [a]
      bar = "bar"
      baz = "baz"
    """.trimIndent()
    doTest(toml, expected) {
      val a = getPropertyElement("a") as GradleDslExpressionMap
      val baz = GradleDslLiteral(a, GradleNameElement.create("baz"))
      baz.setValue("baz")
      a.setNewElement(baz)
    }
  }

  fun testAddIntoASplitElement2() {
    val toml = """
      [a.b]
      foo = "foo"
      [a.b]
      bar = "bar"
    """.trimIndent()
    val expected = """
      [a.b]
      foo = "foo"
      [a.b]
      bar = "bar"
      baz = "baz"
    """.trimIndent()
    doTest(toml, expected) {
      val a = getPropertyElement("a") as GradleDslExpressionMap
      val b = a.getPropertyElement("b") as GradleDslExpressionMap
      val baz = GradleDslLiteral(b, GradleNameElement.create("baz"))
      baz.setValue("baz")
      b.setNewElement(baz)
    }
  }

  @Test
  fun testDeleteSingleLiteralInInlineTable() {
    val toml = """
      foo = { bar = "baz" }
    """.trimIndent()
    val expected = ""
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionMap)?.removeProperty("bar") }
  }

  @Test
  fun testDeleteSingleLiteralInArray() {
    val toml = """
      foo = ["bar"]
    """.trimIndent()
    val expected = ""
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionList)?.run { removeProperty(getElementAt(0)) } }
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
      val baseDir = project.guessProjectDir()!!
      libsTomlFile = baseDir.createChildData(this, "build.gradle.toml")
      VfsUtil.saveText(libsTomlFile, text)
    }
    return libsTomlFile
  }
}
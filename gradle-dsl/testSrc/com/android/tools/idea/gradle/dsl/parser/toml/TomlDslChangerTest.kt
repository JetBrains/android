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

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.junit.Test
import org.junit.runners.Parameterized
import org.mockito.Mockito

class TomlDslChangerTest : LightPlatformTestCase() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "For file: {0}")
    fun filePath() = listOf("gradle/libs.versions.toml", "build.gradle.toml")
  }

  override fun setUp(){
    Registry.`is`("android.gradle.declarative.plugin.studio.support", true)
    super.setUp()
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
  fun testRenameSingleLiteral() {
    val toml = """
      foo = "bar"
    """.trimIndent()
    val expected = """
      foo_updated = "bar"
    """.trimIndent()
    doTest(toml, expected) { children.first().rename("foo_updated") }
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
  fun testRenameMiddleLiteral() {
    val toml = """
      one = "one"
      two = "two"
      three = "three"
    """.trimIndent()
    val expected = """
      one = "one"
      two_updated = "two"
      three = "three"
    """.trimIndent()
    doTest(toml, expected) { elements["two"]?.rename("two_updated") }
  }

  @Test
  fun testRenameSingleLiteralInTable() {
    val toml = """
      [table]
      foo = "bar"
    """.trimIndent()
    val expected = """
      [table]
      foo_updated = "bar"
    """.trimIndent()
    doTest(toml, expected) {
      val table = (getPropertyElement("table") as? GradleDslExpressionMap)
      table?.elements?.get("foo")?.rename("foo_updated")
    }
  }

  @Test
  fun testRenameInInlineTable() {
    val toml = """
      foo = { bar = "baz" }
    """.trimIndent()
    val expected = """
      foo_updated = { bar = "baz" }
    """.trimIndent()
    doTest(toml, expected) {
      elements["foo"]?.rename("foo_updated")
    }
  }

  @Test
  fun testDeleteFirstLiteralInInlineTable() {
    val toml = """
      foo = { one = "one", two = "two", three = "three" }
    """.trimIndent()
    val expected = """
      foo = { two = "two", three = "three" }
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionMap)?.removeProperty("one") }
  }

  @Test
  fun testDeleteMiddleLiteralInInlineTable() {
    val toml = """
      foo = { one = "one", two = "two", three = "three" }
    """.trimIndent()
    val expected = """
      foo = { one = "one", three = "three" }
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionMap)?.removeProperty("two") }
  }

  @Test
  fun testDeleteLastLiteralInInlineTable() {
    val toml = """
      foo = { one = "one", two = "two", three = "three" }
    """.trimIndent()
    val expected = """
      foo = { one = "one", two = "two" }
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionMap)?.removeProperty("three") }
  }

  @Test
  fun testDeleteFirstLiteralInArray() {
    val toml = """
      foo = ["one", "two", "three"]
    """.trimIndent()
    val expected = """
      foo = ["two", "three"]
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionList)?.run { removeProperty(getElementAt(0)) } }
  }

  @Test
  fun testDeleteMiddleLiteralInArray() {
    val toml = """
      foo = ["one", "two", "three"]
    """.trimIndent()
    val expected = """
      foo = ["one", "three"]
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionList)?.run { removeProperty(getElementAt(1)) } }
  }

  @Test
  fun testDeleteLastLiteralInArray() {
    val toml = """
      foo = ["one", "two", "three"]
    """.trimIndent()
    val expected = """
      foo = ["one", "two"]
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionList)?.run { removeProperty(getElementAt(2)) } }
  }

  @Test
  fun testDeleteInlineTable() {
    val toml = """
      foo = { one = "one", two = "two", three = "three" }
    """.trimIndent()
    val expected = ""
    doTest(toml, expected) { removeProperty("foo") }
  }

  @Test
  fun testDeleteArray() {
    val toml = """
      foo = [1, 2, 3]
    """.trimIndent()
    val expected = ""
    doTest(toml, expected) { removeProperty("foo") }
  }

  @Test
  fun testRenameArray() {
    val toml = """
      foo = [1, 2, 3]
    """.trimIndent()
    val expected = "foo_updated = [1, 2, 3]"
    doTest(toml, expected) { children.first().rename("foo_updated") }
  }

  @Test
  fun testAddSecondTable() {
    val toml = """
       [table]
       foo = "bar"
     """.trimIndent()
    val expected = """
       [table]
       foo = "bar"
       [table2]
       baz = "baz"
     """.trimIndent()
    doTest(toml, expected) {
      val table2 = GradleDslExpressionMap(this, GradleNameElement.create("table2"))
      val baz = GradleDslLiteral(table2, GradleNameElement.create("baz"))
      baz.setValue("baz")
      this.setNewElement(table2)
      table2.setNewElement(baz)
    }
  }

  @Test
  fun testAddToSegmentedTable() {
    val toml = """
      [a.b]
      foo = "foo"
    """.trimIndent()
    val expected = """
      [a.b]
      foo = "foo"
      bar = "bar"
    """.trimIndent()
    doTest(toml, expected) {
      ((elements["a"] as GradleDslExpressionMap)
        .getElement("b") as GradleDslExpressionMap)
        .addNewLiteral("bar", "bar")
    }
  }

  @Test
  fun testInsertLiteralFirstInInlineTable() {
    val toml = """
      foo = { two = "two" }
    """.trimIndent()
    val expected = """
      foo = { one = "one", two = "two" }
    """.trimIndent()
    doTest (toml, expected) {
      val foo = getPropertyElement("foo") as GradleDslExpressionMap
      val one = GradleDslLiteral(foo, GradleNameElement.create("one"))
      one.setValue("one")
      foo.addNewElementAt(0, one)
    }
  }

  @Test
  fun testInsertLiteralLastInInlineTable() {
    val toml = """
      foo = { two = "two" }
    """.trimIndent()
    val expected = """
      foo = { two = "two", three = "three" }
    """.trimIndent()
    doTest (toml, expected) {
      val foo = getPropertyElement("foo") as GradleDslExpressionMap
      val three = GradleDslLiteral(foo, GradleNameElement.create("three"))
      three.setValue("three")
      foo.addNewElementAt(1, three)
    }
  }

  @Test
  fun testInsertLiteralFirstInArray() {
    val toml = """
      foo = [2]
    """.trimIndent()
    val expected = """
      foo = [1, 2]
    """.trimIndent()
    doTest (toml, expected) {
      val foo = getPropertyElement("foo") as GradleDslExpressionList
      val one = GradleDslLiteral(foo, GradleNameElement.empty())
      one.setValue(1)
      foo.addNewElementAt(0, one)
    }
  }

  @Test
  fun testInsertLiteralLastInArray() {
    val toml = """
      foo = [2]
    """.trimIndent()
    val expected = """
      foo = [2, 3]
    """.trimIndent()
    doTest (toml, expected) {
      val foo = getPropertyElement("foo") as GradleDslExpressionList
      val three = GradleDslLiteral(foo, GradleNameElement.empty())
      three.setValue(3)
      foo.addNewElementAt(1, three)
    }
  }

  private fun doTest(toml: String, expected: String, changer: GradleDslFile.() -> Unit) {
    val libsTomlFile = VfsTestUtil.createFile(
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
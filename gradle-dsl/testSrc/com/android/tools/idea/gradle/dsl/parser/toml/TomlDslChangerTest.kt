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
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import org.junit.Test

class TomlDslChangerTest : PlatformTestCase() {

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
  fun testDeleteSingleLiteralInArray() {
    val toml = """
      foo = ["bar"]
    """.trimIndent()
    val expected = """
      foo = []
    """.trimIndent()
    doTest(toml, expected) { (getPropertyElement("foo") as? GradleDslExpressionList)?.run { removeProperty(getElementAt(0)) } }
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
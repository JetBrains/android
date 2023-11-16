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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
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

  fun testSingleLiteral() {
    val contents = mapOf("foo" to "bar")
    val expected = """
      foo = "bar"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testMultipleLiterals() {
    val contents = mapOf("foo" to "bar", "baz" to "quux")
    val expected = """
      foo = "bar"
      baz = "quux"
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

  fun testMultipleTables() {
    val contents = mapOf("foo" to mapOf("fooA" to "fooB"), "bar" to mapOf ("barA" to "barB"))
    val expected = """
      [foo]
      fooA = "fooB"
      [bar]
      barA = "barB"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testTableAfterLiteral() {
    val contents = mapOf("foo" to "bar", "baz" to mapOf("bazA" to "bazB"))
    val expected = """
      foo = "bar"
      [baz]
      bazA = "bazB"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testEmptyInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf<String,Any>()))
    val expected = """
      [foo]
      bar = { }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("baz" to "quux")))
    val expected = """
      [foo]
      bar = { baz = "quux" }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testMultipleEntriesInInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("a" to "b", "c" to "d", "e" to "f")))
    val expected = """
      [foo]
      bar = { a = "b", c = "d", e = "f" }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testEntriesOfMultipleKindsInInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("a" to "b", "c" to mapOf("d" to "e"), "f" to "g")))
    val expected = """
      [foo]
      bar = { a = "b", c = { d = "e" }, f = "g" }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testNestedInlineTables() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("baz" to mapOf("quux" to "frob"))))
    val expected = """
      [foo]
      bar = { baz = { quux = "frob" } }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testEmptyArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf<String>()))
    val expected = """
      [foo]
      bar = []
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", "two", "three")))
    val expected = """
      [foo]
      bar = ["one", "two", "three"]
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testNestedEmptyArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", listOf<String>(), "two")))
    val expected = """
      [foo]
      bar = ["one", [], "two"]
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testNestedArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", listOf("two", "three"), "four")))
    val expected = """
      [foo]
      bar = ["one", ["two", "three"], "four"]
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testInlineTableInArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", mapOf("two" to "three"), "four")))
    val expected = """
      [foo]
      bar = ["one", { two = "three" }, "four"]
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testArrayInInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("baz" to listOf("one", "two"), "quux" to "frob")))
    val expected = """
      [foo]
      bar = { baz = ["one", "two"], quux = "frob" }
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

  fun testSimpleBlock() {
    val contents = mapOf("block" to blockOf("key1" to "value1", "key2" to "value2"))
    val expected = """
      [block]
      key1 = "value1"
      key2 = "value2"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testParentChildBlocksWithAttributes() {
    val contents = mapOf("block1" to blockOf("key1" to "value1", "block2" to blockOf( "key2" to "value2")))
    val expected = """
      [block1]
      key1 = "value1"
      [block1.block2]
      key2 = "value2"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testCreatingSegmentedBlock() {
    val contents = mapOf("block1" to blockOf("block2" to blockOf( "key2" to "value2")))
    val expected = """
      [block1.block2]
      key2 = "value2"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testCreatingArrayInBlock() {
    val contents = mapOf("block1" to blockOf("array" to listOf("element1", "element2")))
    val expected = """
      [block1]
      array = ["element1", "element2"]
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testCreatingMapInBlock  () {
    val contents = mapOf("block1" to blockOf("key" to mapOf("key1" to "value1")))
    val expected = """
      [block1]
      key = { key1 = "value1" }
    """.trimIndent()

    doTest(contents, expected)
  }

  private fun mapToProperties(map: Map<String,Any>, dslFile: GradleDslFile) {
    fun populate(key: String, value: Any, element: GradlePropertiesDslElement) {
      when (value) {
        is String -> element.setNewLiteral(key, value)
        is Block<*,*> -> {
          val block = TestBlockElement(element, key)
          value.forEach { (k, v) -> populate(k as String, v as Any, block) }
          element.setNewElement(block)
        }
        is List<*> -> {
          val dslList = GradleDslExpressionList(element, GradleNameElement.create(key), true)
          value.forEachIndexed { i, v -> populate(i.toString(), v as Any, dslList) }
          element.setNewElement(dslList)
        }
        is Map<*,*> -> {
          val dslMap = GradleDslExpressionMap(element, GradleNameElement.create(key))
          value.forEach { (k, v) -> populate(k as String, v as Any, dslMap) }
          element.setNewElement(dslMap)
        }
      }
    }
    map.forEach { (k, v) -> populate(k, v, dslFile) }
  }

  private class TestBlockElement(parent: GradleDslElement, name: String) : GradleDslBlockElement(parent, GradleNameElement.create(name))

  private class Block<K,V> : HashMap<K,V>()

  private fun <K, V> blockOf(vararg pairs: Pair<K, V>): Map<K, V> {
    val b = Block<K,V>()
    pairs.toMap(b)
    return b
  }

  private fun <K,V> blockOf(pair: Pair<K, V>): Map<K, V> {
    val b = Block<K,V>()
    b[pair.first] =  pair.second
    return b
  }
}
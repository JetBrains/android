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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
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

class TomlDslWriterTest: LightPlatformTestCase() {
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
  fun testSingleLiteral() {
    val contents = mapOf("foo" to "bar")
    val expected = """
      foo = "bar"
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testSingleLiteralDottedName() {
    val contents = mapOf("foo.bar" to "val")
    val expected = """
      "foo.bar" = "val"
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testMultipleLiterals() {
    val contents = mapOf("foo" to "bar", "baz" to "quux")
    val expected = """
      foo = "bar"
      baz = "quux"
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testSingleTable() {
    val contents = mapOf("foo" to mapOf("bar" to "baz"))
    val expected = """
      [foo]
      bar = "baz"
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
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

  @Test
  fun testTableAfterLiteral() {
    val contents = mapOf("foo" to "bar", "baz" to mapOf("bazA" to "bazB"))
    val expected = """
      foo = "bar"
      [baz]
      bazA = "bazB"
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testEmptyInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf<String,Any>()))
    val expected = """
      [foo]
      bar = { }
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("baz" to "quux")))
    val expected = """
      [foo]
      bar = { baz = "quux" }
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testMultipleEntriesInInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("a" to "b", "c" to "d", "e" to "f")))
    val expected = """
      [foo]
      bar = { a = "b", c = "d", e = "f" }
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testEntriesOfMultipleKindsInInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("a" to "b", "c" to mapOf("d" to "e"), "f" to "g")))
    val expected = """
      [foo]
      bar = { a = "b", c = { d = "e" }, f = "g" }
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testNestedInlineTables() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("baz" to mapOf("quux" to "frob"))))
    val expected = """
      [foo]
      bar = { baz = { quux = "frob" } }
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testEmptyArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf<String>()))
    val expected = """
      [foo]
      bar = []
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", "two", "three")))
    val expected = """
      [foo]
      bar = ["one", "two", "three"]
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testNestedEmptyArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", listOf<String>(), "two")))
    val expected = """
      [foo]
      bar = ["one", [], "two"]
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testNestedArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", listOf("two", "three"), "four")))
    val expected = """
      [foo]
      bar = ["one", ["two", "three"], "four"]
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testInlineTableInArray() {
    val contents = mapOf("foo" to mapOf("bar" to listOf("one", mapOf("two" to "three"), "four")))
    val expected = """
      [foo]
      bar = ["one", { two = "three" }, "four"]
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testArrayInInlineTable() {
    val contents = mapOf("foo" to mapOf("bar" to mapOf("baz" to listOf("one", "two"), "quux" to "frob")))
    val expected = """
      [foo]
      bar = { baz = ["one", "two"], quux = "frob" }
    """.trimIndent()

    doTest(contents, expected)
  }

  private fun doTest(contents: Map<String,Any>, expected: String) {
    val libsTomlFile = VfsTestUtil.createFile(project.guessProjectDir()!!, "gradle/libs.versions.toml", "")
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    dslFile.parse()
    mapToProperties(contents, dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val text = VfsUtil.loadText(libsTomlFile).replace("\r", "")
    assertEquals(expected, text)
  }

  private fun mapToProperties(map: Map<String,Any>, dslFile: GradleDslFile) {
    fun populate(key: String, value: Any, element: GradlePropertiesDslElement) {
      when (value) {
        is String -> element.setNewLiteral(key, value)
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
}
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
package com.android.tools.idea.gradle.dsl.parser.catalog

import com.android.testutils.MockitoKt
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Test
import java.io.File
import java.util.Collections.swap

class CatalogTomlDslWriterTest : LightPlatformTestCase() {

  @Test
  fun testAddTablesInReverseOrder() {
    val contents = mapOf("libraries" to mapOf("lib1" to "lib1val"), "versions" to mapOf("version1" to "version1val"))
    val expected = """
      [versions]
      version1 = "version1val"
      [libraries]
      lib1 = "lib1val"
    """.trimIndent()

    doTest(contents, expected)
  }

  @Test
  fun testAddTablesInMixedOrderExhaustive() {
    val list = listOf("versions" to mapOf("version1" to "version1val"),
                      "libraries" to mapOf("lib1" to "lib1val"),
                      "plugins" to mapOf("plugin1" to "plugin1value"),
                      "bundles" to mapOf("bundle1" to "bundle1value"))

    val expected = """
      [versions]
      version1 = "version1val"
      [libraries]
      lib1 = "lib1val"
      [plugins]
      plugin1 = "plugin1value"
      [bundles]
      bundle1 = "bundle1value"
    """.trimIndent()

    list.permutate().map { permutation -> permutation.associate { it } }.forEach { doTest(it, expected) }

  }

  @Test
  fun testAddTablesInMixedOrder2() {
    val contents = mapOf(
      "versions" to mapOf("version1" to "version1val"),
      "plugins" to mapOf("plugin1" to "plugin1value"),
      "libraries" to mapOf("lib1" to "lib1val"))
    val expected = """
      [versions]
      version1 = "version1val"
      [libraries]
      lib1 = "lib1val"
      [plugins]
      plugin1 = "plugin1value"
    """.trimIndent()

    doTest(contents, expected)
  }

  // main goal here to check that toml will be valid as such situation is extremely rare
  @Test
  fun testAddTablesInWithNonCatalogTables() {
    val contents = mapOf("foo" to mapOf("bar" to "baz"),
                         "libraries" to mapOf("lib1" to "lib1val"),
                         "plugins" to mapOf("plugin1" to "plugin1value"),
                         "foo2" to mapOf("bar2" to "baz2"),
                         "versions" to mapOf("version1" to "version1val")
    )
    val expected = """
      [foo]
      bar = "baz"
      [versions]
      version1 = "version1val"
      [libraries]
      lib1 = "lib1val"
      [plugins]
      plugin1 = "plugin1value"
      [foo2]
      bar2 = "baz2"
    """.trimIndent()

    doTest(contents, expected)
  }

  private fun doTest(contents: Map<String, Any>, expected: String) {
    val libsTomlFile = writeLibsTomlFile("")
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, MockitoKt.mock())) {}
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.saveAllChanges() // need commit document once the file is reused in test
    }
    mapToProperties(contents, dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val text = VfsUtil.loadText(libsTomlFile).replace("\r", "")
    assertEquals(expected, text)
  }

  private fun mapToProperties(map: Map<String, Any>, dslFile: GradleDslFile) {
    fun populate(key: String, value: Any, element: GradlePropertiesDslElement) {
      when (value) {
        is String -> element.setNewLiteral(key, value)
        is List<*> -> {
          val dslList = GradleDslExpressionList(element, GradleNameElement.create(key), true)
          value.forEachIndexed { i, v -> populate(i.toString(), v as Any, dslList) }
          element.setNewElement(dslList)
        }

        is Map<*, *> -> {
          val dslMap = GradleDslExpressionMap(element, GradleNameElement.create(key))
          value.forEach { (k, v) -> populate(k as String, v as Any, dslMap) }
          element.setNewElement(dslMap)
        }
      }
    }
    map.forEach { (k, v) -> populate(k, v, dslFile) }
  }

  private fun <T> List<T>.permutate(): List<List<T>> {
    val solutions = mutableListOf<List<T>>()
    permutationsRecursive(this, 0, solutions)
    return solutions
  }

  private fun <T> permutationsRecursive(input: List<T>, index: Int, answers: MutableList<List<T>>) {
    if (index == input.lastIndex) answers.add(input.toList())
    for (i in index..input.lastIndex) {
      swap(input, index, i)
      permutationsRecursive(input, index + 1, answers)
      swap(input, i, index)
    }
  }

  private fun writeLibsTomlFile(text: String): VirtualFile {
    lateinit var libsTomlFile: VirtualFile
    runWriteAction {
      val file: File = File(project.basePath, "gradle/libs.versions.toml").getCanonicalFile()
      FileUtil.createParentDirs(file)
      val parent = VfsUtil.findFile(file.parentFile.toPath(), true)!!
      libsTomlFile = parent.createChildData(this, file.name)
      VfsUtil.saveText(libsTomlFile, text)
    }
    return libsTomlFile
  }
}
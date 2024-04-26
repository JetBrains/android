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
package com.android.tools.idea.gradle.dsl.parser.declarative

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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase

class DeclarativeTomlDslWriterTest : LightPlatformTestCase() {

  override fun setUp(){
    Registry.`is`("android.gradle.declarative.plugin.studio.support", true)
    super.setUp()
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

  fun testCreatingMapInBlock() {
    val contents = mapOf("block1" to blockOf("key" to mapOf("key1" to "value1")))
    val expected = """
      [block1]
      key = { key1 = "value1" }
    """.trimIndent()

    doTest(contents, expected)
  }


  private fun doTest(contents: Map<String,Any>, expected: String) {
    val libsTomlFile = writeDeclarativeTomlFile("")
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, MockitoKt.mock())) {}
    dslFile.parse()
    mapToProperties(contents, dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val text = VfsUtil.loadText(libsTomlFile).replace("\r", "")
    assertEquals(expected, text)
  }

  private fun writeDeclarativeTomlFile(text: String): VirtualFile {
    lateinit var libsTomlFile: VirtualFile
    runWriteAction {
      val baseDir = project.guessProjectDir()!!
      libsTomlFile = baseDir.createChildData(this, "build.gradle.toml")
      VfsUtil.saveText(libsTomlFile, text)
    }
    return libsTomlFile
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
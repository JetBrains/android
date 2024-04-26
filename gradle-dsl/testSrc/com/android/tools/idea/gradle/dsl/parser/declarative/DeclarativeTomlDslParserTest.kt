/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase

class DeclarativeTomlDslParserTest : LightPlatformTestCase() {

  override fun setUp(){
    Registry.`is`("android.gradle.declarative.plugin.studio.support", true)
    super.setUp()
  }

  fun testEmptyNameTable() {
    val toml = """
      []
      module = "junit:junit"
      version = "4.13"
    """.trimIndent()
    val expected = mapOf<String, Any>()
    doTest(toml, expected)
  }

  fun testArrayTable() {
    val toml = """
      [[arrayTable]]
      key1 = "val1"
      key2 = "val2"
    """.trimIndent()
    val expected = mapOf("arrayTable" to listOf(mapOf("key1" to "val1", "key2" to "val2")))
    doTest(toml, expected)
  }

  fun testArrayTableEmptyName() {
    val toml = """
      [[ ]]
      key1 = "val1"
      key2 = "val2"
    """.trimIndent()
    val expected = mapOf<String, Any>()
    doTest(toml, expected)
  }

  fun testArrayTableMultipleElements() {
    val toml = """
      [[arrayTable]]
      key1 = "val1"
      key2 = "val2"

      [[arrayTable]]
      key3 = "val3"
      key4 = "val4"
    """.trimIndent()
    val expected = mapOf("arrayTable" to listOf(mapOf("key1" to "val1", "key2" to "val2"), mapOf("key3" to "val3", "key4" to "val4")))
    doTest(toml, expected)
  }

  fun testArrayTableMultipleElements2() {
    val toml = """
      [[table.arrayTable]]
      key1 = "val1"
      key2 = "val2"

      [[table.arrayTable]]
      key3 = "val3"
      key4 = "val4"
    """.trimIndent()
    val expected = mapOf(
      "table" to mapOf("arrayTable" to listOf(mapOf("key1" to "val1", "key2" to "val2"), mapOf("key3" to "val3", "key4" to "val4"))))
    doTest(toml, expected)
  }

  fun testSegmentedArrayTable() {
    val toml = """
      [[table.arrayTable]]
      key1 = "val1"
      key2 = "val2"
    """.trimIndent()
    val expected = mapOf("table" to mapOf("arrayTable" to listOf(mapOf("key1" to "val1", "key2" to "val2"))))
    doTest(toml, expected)
  }

  fun testSegmentedArrayTable2() {
    val toml = """
      [table]
      key1 = "val1"
      [[table.arrayTable]]
      key2 = "val2"
      key3 = "val3"
    """.trimIndent()
    val expected = mapOf("table" to mapOf("key1" to "val1", "arrayTable" to listOf(mapOf("key2" to "val2", "key3" to "val3"))))
    doTest(toml, expected)
  }

  fun testSegmentedArrayTableReverse() {
    val toml = """
      [[table.arrayTable]]
      key2 = "val2"
      key3 = "val3"
      [table]
      key1 = "val1"
    """.trimIndent()
    val expected = mapOf("table" to mapOf("key1" to "val1", "arrayTable" to listOf(mapOf("key2" to "val2", "key3" to "val3"))))
    doTest(toml, expected)
  }

  fun testIntegerRadix10() {
    val toml = """
      int1 = +99
      int2 = 42
      int3 = 0
      int4 = -17
      int5 = 1_000
      int6 = 5_349_221
      int7 = 53_49_221  # Indian number system grouping
      int8 = 1_2_3_4_5  # VALID but discouraged
    """.trimIndent()
    val expected = mapOf("int1" to 99, "int2" to 42, "int3" to 0, "int4" to -17, "int5" to 1000, "int6" to 5349221,
                                       "int7" to 5349221, "int8" to 12345)
    doTest(toml, expected)
  }

  fun testLong() {
    val toml = """
      long1 = 1844674407370955161
      long2 = -1844674407370955161
    """.trimIndent()
    val expected = mapOf("long1" to 1844674407370955161L, "long2" to -1844674407370955161L)
    doTest(toml, expected)
  }


  fun testIntegerRadixPrefixes() {
    val toml = """
      # hexadecimal with prefix `0x`
      hex1 = 0xDEADBEEF
      hex2 = 0xdeadbeef
      hex3 = 0xdead_beef

      # octal with prefix `0o`
      oct1 = 0o01234567
      oct2 = 0o755 # useful for Unix file permissions

      # binary with prefix `0b`
      bin1 = 0b11010110
    """.trimIndent()
    val expected = mapOf<String, Long>("hex1" to 3735928559, "hex2" to 3735928559, "hex3" to 3735928559, "oct1" to 342391, "oct2" to 493,
                                       "bin1" to 214)
    doTest(toml, expected)
  }

  fun testFloat() {
    val toml = """
      # fractional
      flt1 = +1.0
      flt2 = 3.1415
      flt3 = -0.01

      # exponent
      flt4 = 5e+22
      flt5 = 1e06
      flt6 = -2E-2

      # both
      flt7 = 6.626e-34
      flt8 = 224_617.445_991_228

      # infinity
      sf1 = inf  # positive infinity
      sf2 = +inf # positive infinity
      sf3 = -inf # negative infinity

      # not a number
      sf4 = nan  # actual sNaN/qNaN encoding is implementation-specific
      sf5 = +nan # same as `nan`
      sf6 = -nan # valid, actual encoding is implementation-specific
    """.trimIndent()
    val expected = mapOf(
      "flt1" to 1.0,
      "flt2" to 3.1415,
      "flt3" to -0.01,
      "flt4" to 5e22,
      "flt5" to 1e06,
      "flt6" to -2e-2,
      "flt7" to 6.626e-34,
      "flt8" to 224617.445991228,
      "sf1" to Double.POSITIVE_INFINITY,
      "sf2" to Double.POSITIVE_INFINITY,
      "sf3" to Double.NEGATIVE_INFINITY,
      "sf4" to Double.NaN,
      "sf5" to Double.NaN,
      "sf6" to Double.NaN,
    )
    doTest(toml, expected)
  }

  private fun doTest(text: String, expected: Map<String,Any>) {
    val libsTomlFile = writeDeclarativeTomlFile(text)
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    assertEquals(expected, propertiesToMap(dslFile))
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

  private fun propertiesToMap(dslFile: GradleDslFile): Map<String, Any> {
    fun populate(key: String, element: GradleDslElement?, setter: (String, Any) -> Unit) {
      val value = when (element) {
        is GradleDslLiteral -> element.value ?: "null literal"
        is GradleDslExpressionMap -> {
          val newMap = LinkedHashMap<String, Any>()
          element.properties.forEach { populate(it, element.getElement(it)) { k, v -> newMap[k] = v } }
          newMap
        }
        is GradleDslExpressionList -> {
          val newList = ArrayList<Any>()
          element.allElements.forEach { populate("", it) { _, v -> newList.add(v) } }
          newList
        }
        else -> "Unknown element: $element"
      }
      setter(key, value)
    }
    val map = LinkedHashMap<String,Any>()
    dslFile.properties.forEach { populate(it, dslFile.getElement(it)) { key, value -> map[key] = value } }
    return map
  }
}
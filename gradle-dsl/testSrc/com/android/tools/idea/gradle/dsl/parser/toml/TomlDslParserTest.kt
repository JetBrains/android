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
package com.android.tools.idea.gradle.dsl.parser.toml

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase
import org.junit.Assume.assumeTrue

class TomlDslParserTest : PlatformTestCase() {

  fun testSingleLibraryLiteralString() {
    val toml = """
      [libraries]
      junit = 'junit:junit:4.13'
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testSingleLibraryMultiLineLiteralString() {
    val toml = """
      [libraries]
      junit = '''junit:junit:4.13'''
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testSingleLibraryBasicString() {
    val singleQuote = "\""
    val singleQuotedJunitWithEscapes = "junit:junit:4.13"
      .mapIndexed { i, c -> if ((i % 2) == 0) c.toString() else String.format("\\u%04x", c.code) }
      .joinToString(separator = "", prefix = singleQuote, postfix = singleQuote)
    val toml = """
      [libraries]
      junit = $singleQuotedJunitWithEscapes
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testSingleLibraryMultiLineBasicString() {
    val tripleQuote = "\""
    val tripleQuotedJunitWithEscapes = "junit:junit:4.13"
      .mapIndexed { i, c -> if ((i % 2) == 1) c.toString() else String.format("\\u%04x", c.code) }
      .joinToString(separator = "", prefix = tripleQuote, postfix = tripleQuote)
    val toml = """
      [libraries]
      junit = $tripleQuotedJunitWithEscapes
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun _testSingleLibraryMultiLineLiteralStringInitialNewline() {
    assumeTrue("Toml unescaper does not handle removal of initial newline: https://github.com/JetBrains/intellij-community/pull/1754/commits/11fcd6614b20c8f518acbebc6c34493963f2d6e4", false)
    val toml = """
      [libraries]
      junit = '''
      junit:junit:4.13'''
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun _testSingleLibraryMultiLineBasicStringInitialNewline() {
    assumeTrue("Toml unescaper does not handle removal of initial newline: https://github.com/JetBrains/intellij-community/pull/1754/commits/11fcd6614b20c8f518acbebc6c34493963f2d6e4", false)
    val tripleQuote = "\"\"\""
    val junitWithEscapes = "junit:junit:4.13"
      .mapIndexed { i, c -> if ((i % 2) == 1) c.toString() else String.format("\\u%04x", c.code) }
      .joinToString(separator = "")
    val toml = """
      [libraries]
      junit = $tripleQuote
      $junitWithEscapes$tripleQuote
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testLiteralStringKey() {
    val toml = """
      [libraries]
      'junit' = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testBasicStringKey() {
    val toml = """
      [libraries]
      "junit" = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun _testBasicStringEscapesKey() {
    assumeTrue("Toml does not unescape names from quoted keys: https://github.com/JetBrains/intellij-community/pull/1754/commits/d97f0e1cc4fd6fede790f39ac3e9d3c4cef57ed4", false)
    val toml = """
      [libraries]
      "\u006au\u006ei\u0074" = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testEmptyBasicStringKey() {
    val toml = """
      [libraries]
      "" = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testEmptyLiteralStringKey() {
    val toml = """
      [libraries]
      '' = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  fun testImplicitMap() {
    val toml = """
      [libraries]
      junit.module = "junit:junit"
      junit.version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  fun testImplicitMapWithQuotedKeys() {
    val toml = """
      [libraries]
      'junit'.module = "junit:junit"
      junit."version" = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  fun testQuotedDottedKeys() {
    val toml = """
      [libraries]
      'junit.module' = "junit:junit"
      "junit.version" = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit.module" to "junit:junit", "junit.version" to "4.13"))
    doTest(toml, expected)
  }

  fun testImplicitTable() {
    val toml = """
      [libraries.junit]
      module = "junit:junit"
      version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  fun testImplicitTableQuoted() {
    val toml = """
      ['libraries'."junit"]
      module = "junit:junit"
      version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  fun testQuotedDottedTable() {
    val toml = """
      ["libraries.junit"]
      module = "junit:junit"
      version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries.junit" to mapOf("module" to "junit:junit", "version" to "4.13"))
    doTest(toml, expected)
  }

  fun testInlineTable() {
    val toml = """
      [libraries]
      junit = { module = "junit:junit", version = "4.13" }
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  fun testInlineTableWithImplicitTables() {
    val toml = """
      [libraries]
      junit = { module = "junit:junit", version.ref = "junit" }
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to mapOf("ref" to "junit"))))
    doTest(toml, expected)
  }

  fun testArray() {
    val toml = """
      [bundles]
      groovy = ["groovy-core", "groovy-json", "groovy-nio"]
    """.trimIndent()
    val expected = mapOf("bundles" to mapOf("groovy" to listOf("groovy-core", "groovy-json", "groovy-nio")))
    doTest(toml, expected)
  }

  fun testArrayWithInlineTable() {
    val toml = """
      [bundles]
      groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
    """.trimIndent()
    val expected = mapOf("bundles" to mapOf("groovy" to listOf("groovy-core", "groovy-json", mapOf("name" to "groovy-nio", "version" to "3.14"))))
    doTest(toml, expected)
  }

  fun testInlineTableWithArray() {
    val toml = """
      [libraries]
      junit = { module = ["junit", "junit"], version = "4.13" }
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to listOf("junit", "junit"), "version" to "4.13")))
    doTest(toml, expected)
  }

  fun testBoolean() {
    val toml = """
      a = true
      b = false
    """.trimIndent()
    val expected = mapOf("a" to true, "b" to false)
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
    val expected = mapOf<String, Long>("int1" to 99, "int2" to 42, "int3" to 0, "int4" to -17, "int5" to 1000, "int6" to 5349221,
                                       "int7" to 5349221, "int8" to 12345)
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
      // https://github.com/JetBrains/intellij-community/pull/2338 "flt5" to 1e06,
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
    val libsTomlFile = writeLibsTomlFile(text)
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    assertEquals(expected, propertiesToMap(dslFile))
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
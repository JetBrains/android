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

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.Mockito.mock
import com.google.common.truth.Truth.assertThat

class TomlDslParserTest : LightPlatformTestCase() {

  @Test
  fun testSingleLibraryLiteralString() {
    val toml = """
      [libraries]
      junit = 'junit:junit:4.13'
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testSingleLibraryMultiLineLiteralString() {
    val toml = """
      [libraries]
      junit = '''junit:junit:4.13'''
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
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

  @Test
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


  // this case is not supported by tomlj
  @Test
  fun testLiteralStringKey() {
    val toml = """
      [libraries]
      'junit' = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  // this case is not supported by tomlj
  @Test
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

  @Test
  fun testEmptyBasicStringKey() {
    val toml = """
      [libraries]
      "" = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testEmptyLiteralStringKey() {
    val toml = """
      [libraries]
      '' = "junit:junit:4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("" to "junit:junit:4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitMap() {
    val toml = """
      [libraries]
      junit.module = "junit:junit"
      junit.version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitMapWithQuotedKeys() {
    val toml = """
      [libraries]
      'junit'.module = "junit:junit"
      junit."version" = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testQuotedDottedKeys() {
    val toml = """
      [libraries]
      'junit.module' = "junit:junit"
      "junit.version" = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit.module" to "junit:junit", "junit.version" to "4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitTable() {
    val toml = """
      [libraries.junit]
      module = "junit:junit"
      version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitTable2() {
    val toml = """
      [libraries.junit]
      module = "junit:junit"
      [libraries.guava]
      module = "com.google.guava:guava"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit"), "guava" to mapOf( "module" to "com.google.guava:guava")))
    doTest(toml, expected)
  }

  @Test
  fun testComplexTable() {
    val toml = """
      [libraries]
      junit = "junit:junit:4.1"
      [libraries.guava]
      module = "com.google.guava:guava"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to "junit:junit:4.1", "guava" to mapOf("module" to "com.google.guava:guava")))
    doTest(toml, expected)
  }

  @Test
  fun testComplexTableReverse() {
    val toml = """
      [table1.table2]
      key2 = "value2"
      [table1]
      key1 = "value1"
    """.trimIndent()
    val expected = mapOf("table1" to mapOf("table2" to mapOf("key2" to "value2"), "key1" to "value1"))
    doTest(toml, expected)
  }

  // Key/Table duplication is NOT allowed in TOML https://github.com/toml-lang/toml/issues/697
  // but at least we make sure we don't fail in this case
  @Test
  fun testImplicitTableWithContinuation() {
    val toml = """
      [libraries.junit]
      module = "junit:junit"
      [libraries.junit]
      version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testImplicitTableQuoted() {
    val toml = """
      ['libraries'."junit"]
      module = "junit:junit"
      version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testQuotedDottedTable() {
    val toml = """
      ["libraries.junit"]
      module = "junit:junit"
      version = "4.13"
    """.trimIndent()
    val expected = mapOf("libraries.junit" to mapOf("module" to "junit:junit", "version" to "4.13"))
    doTest(toml, expected)
  }

  @Test
  fun testInlineTable() {
    val toml = """
      [libraries]
      junit = { module = "junit:junit", version = "4.13" }
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testInlineTableWithImplicitTables() {
    val toml = """
      [libraries]
      junit = { module = "junit:junit", version.ref = "junit" }
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to "junit:junit", "version" to mapOf("ref" to "junit"))))
    doTest(toml, expected)
  }

  @Test
  fun testArray() {
    val toml = """
      [bundles]
      groovy = ["groovy-core", "groovy-json", "groovy-nio"]
    """.trimIndent()
    val expected = mapOf("bundles" to mapOf("groovy" to listOf("groovy-core", "groovy-json", "groovy-nio")))
    doTest(toml, expected)
  }

  @Test
  fun testArrayWithInlineTable() {
    val toml = """
      [bundles]
      groovy = ["groovy-core", "groovy-json", { name = "groovy-nio", version = "3.14" } ]
    """.trimIndent()
    val expected = mapOf("bundles" to mapOf("groovy" to listOf("groovy-core", "groovy-json", mapOf("name" to "groovy-nio", "version" to "3.14"))))
    doTest(toml, expected)
  }

  @Test
  fun testInlineTableWithArray() {
    val toml = """
      [libraries]
      junit = { module = ["junit", "junit"], version = "4.13" }
    """.trimIndent()
    val expected = mapOf("libraries" to mapOf("junit" to mapOf("module" to listOf("junit", "junit"), "version" to "4.13")))
    doTest(toml, expected)
  }

  @Test
  fun testBoolean() {
    val toml = """
      a = true
      b = false
    """.trimIndent()
    val expected = mapOf("a" to true, "b" to false)
    doTest(toml, expected)
  }

  @Test
  fun testVersionReference() {
    val toml = """
      [versions]
      aVersion = "1.0"
      bVersion = "2.0"
      [libraries]
      aLib = { module = "example:example", version.ref = "aVersion" }
      bLib = { module = "example:example", version = { ref = "aVersion" } }
      [plugins]
      aPlugin = { id = "plugin" version.ref = "bVersion" }
      bPlugin = { id = "plugin" version = { ref= "bVersion" } }
    """.trimIndent()
    verifyVersionReferences(toml)
  }

  @Test
  fun testVersionReferenceReverseOrder() {
    val toml = """
      [libraries]
      aLib = { module = "example:example", version.ref = "aVersion" }
      bLib = { module = "example:example", version = { ref = "aVersion" } }
      [plugins]
      aPlugin = { id = "plugin" version.ref = "bVersion" }
      bPlugin = { id = "plugin" version = { ref= "bVersion" } }
      [versions]
      aVersion = "1.0"
      bVersion = "2.0"
    """.trimIndent()
    verifyVersionReferences(toml)
  }

  private fun verifyVersionReferences(tomlContent: String) {
    val libsTomlFile = VfsTestUtil.createFile(project.guessProjectDir()!!, "gradle/libs.versions.toml", tomlContent)
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    verifyVersionReference(dslFile, "libraries", "aLib", "aVersion", "1.0")
    verifyVersionReference(dslFile, "libraries", "bLib", "aVersion", "1.0")
    verifyVersionReference(dslFile, "plugins", "aPlugin", "bVersion", "2.0")
    verifyVersionReference(dslFile, "plugins", "bPlugin", "bVersion", "2.0")
  }

  private fun verifyVersionReference(dslFile: GradleDslFile, tableName: String, alias: String, versionAlias: String, versionText: String) {
    // checking from reference side
    val declaration = dslFile.getPropertyElement(tableName, GradleDslExpressionMap::class.java)?.getPropertyElement(alias)
    assertThat(declaration).isInstanceOf(GradleDslExpressionMap::class.java)
    val version = (declaration as GradleDslExpressionMap).getPropertyElement("version")
    assertThat(version).isNotNull()
    assertThat(version!!.dependencies).hasSize(1)
    val injection = version.dependencies[0]
    assertThat(injection.toBeInjected).isNotNull()
    assertThat(injection.toBeInjected!!.name).isEqualTo(versionAlias)
    assertThat(injection.toBeInjected).isInstanceOf(GradleDslLiteral::class.java)
    assertThat((injection.toBeInjected as GradleDslLiteral).value).isEqualTo(versionText)

    // checking from version declaration side
    val versionDeclaration = dslFile.getPropertyElement("versions", GradleDslExpressionMap::class.java)?.getPropertyElement(versionAlias)
    assertThat(versionDeclaration).isNotNull()
    assertThat(versionDeclaration).isInstanceOf(GradleDslLiteral::class.java)
    assertThat(injection.toBeInjected).isEqualTo(versionDeclaration)
    assertThat(versionDeclaration!!.dependents).contains(injection)
  }

  @Test
  fun testBundleReference() {
    val toml = """
      [libraries]
      aLib = "example:example:aVersion"
      bLib = "example:example:bVersion"
      [bundles]
      aBundle = ["aLib","bLib"]
    """.trimIndent()
    verifyBundleReferences(toml)
  }

  @Test
  fun testBundleReferenceReverseOrder() {
    val toml = """
      [bundles]
      aBundle = ["aLib","bLib"]
      [libraries]
      aLib = "example:example:aVersion"
      bLib = "example:example:bVersion"
    """.trimIndent()
    verifyBundleReferences(toml)
  }

  private fun verifyBundleReferences(tomlContent: String) {
    val libsTomlFile = VfsTestUtil.createFile(project.guessProjectDir()!!, "gradle/libs.versions.toml", tomlContent)
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    verifyBundleReference(dslFile, "aBundle", "aLib", "example:example:aVersion")
    verifyBundleReference(dslFile, "aBundle", "bLib", "example:example:bVersion")
  }

  private fun verifyBundleReference(dslFile: GradleDslFile, alias: String, libAlias: String, libValue:String) {
    // checking from library reference side
    val bundle = dslFile.getPropertyElement("bundles", GradleDslExpressionMap::class.java)?.getPropertyElement(alias)
    assertThat(bundle).isInstanceOf(GradleDslExpressionList::class.java)
    val reference = (bundle as GradleDslExpressionList).children.find { it.name == libAlias}
    assertThat(reference).isNotNull()
    assertThat(reference!!.dependencies).hasSize(1)
    val injection = reference.dependencies[0]
    assertThat(injection.toBeInjected).isNotNull()
    assertThat(injection.toBeInjected!!.name).isEqualTo(libAlias)
    assertThat((injection.toBeInjected as GradleDslLiteral).value).isEqualTo(libValue)

    // checking from library declaration side
    val libraryDeclaration = dslFile.getPropertyElement("libraries", GradleDslExpressionMap::class.java)?.getPropertyElement(libAlias)
    assertThat(libraryDeclaration).isNotNull()
    assertThat(libraryDeclaration).isInstanceOf(GradleDslLiteral::class.java)
    assertThat(injection.toBeInjected).isEqualTo(libraryDeclaration)
    assertThat(libraryDeclaration!!.dependents).contains(injection)
  }

  private fun doTest(text: String, expected: Map<String,Any>) {
    val libsTomlFile = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "gradle/libs.versions.toml",
      text
    )
    val dslFile = object : GradleDslFile(libsTomlFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    assertEquals(expected, propertiesToMap(dslFile))
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
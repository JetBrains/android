/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.something

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.blockOf
import com.android.tools.idea.gradle.dsl.parser.factoryOf
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.mapToProperties
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.mockito.Mockito

class SomethingDslWriterTest : LightPlatformTestCase() {

  fun testAssignmentWithString() {
    val contents = mapOf("key1" to "value1")
    val expected = """
      key1 = "value1"
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testAssignmentWithInt() {
    val contents = mapOf("key1" to 123)
    val expected = """
      key1 = 123
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testEmptyBlock() {
    val contents = mapOf("block" to blockOf())
    val expected = """
      block {
      }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testSimpleBlockWithAssignments() {
    val contents = mapOf("block" to blockOf("key1" to "value1", "key2" to "value2"))
    val expected = """
      block {
      key1 = "value1"
      key2 = "value2"
      }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testSimpleBlockWithMixedAssignments() {
    val contents = mapOf("block" to blockOf("key1" to "value1", "key2" to 123, "key3" to true))
    val expected = """
      block {
      key1 = "value1"
      key2 = 123
      key3 = true
      }
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testFactoryWithStringArgument() {
    val contents = mapOf("factory" to factoryOf("value1"))
    val expected = """
      factory("value1")
     """.trimIndent()

    doTest(contents, expected)
  }

  fun testFactoryWithIntArgument() {
    val contents = mapOf("factory" to factoryOf(123))
    val expected = """
      factory(123)
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testFactoryWithBooleanArgument() {
    val contents = mapOf("factory" to factoryOf(true))
    val expected = """
      factory(true)
    """.trimIndent()

    doTest(contents, expected)
  }

  fun testEmbeddedFactory() {
    val contents = mapOf("factory" to factoryOf(mapOf("embeddedFactory" to factoryOf("value"))))
    val expected = """
      factory(embeddedFactory("value"))
    """.trimIndent()

    doTest(contents, expected)
  }

  private fun doTest(contents: Map<String, Any>, expected: String) {
    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "build.gradle.something",
      ""
    )
    val dslFile = object : GradleDslFile(file, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    dslFile.parse()
    mapToProperties(contents, dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val text = VfsUtil.loadText(file).replace("\r", "")
    assertEquals(expected, text)
  }

}
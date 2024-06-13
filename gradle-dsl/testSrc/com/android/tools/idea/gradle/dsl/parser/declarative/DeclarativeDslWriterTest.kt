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
package com.android.tools.idea.gradle.dsl.parser.declarative

import com.android.tools.idea.gradle.dsl.model.BuildModelContext
import com.android.tools.idea.gradle.dsl.parser.blockOf
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.factoryOf
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.mapToProperties
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.mockito.Mockito

class DeclarativeDslWriterTest : LightPlatformTestCase() {

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
    val expected = ""
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

  fun testFactoryWithNoArguments() {
    val contents = mapOf("factory" to factoryOf<String>())
    val expected = """
      factory()
     """.trimIndent()

    doTest(contents, expected)
  }

  fun testFactoryWithMultipleArguments() {
    val contents = mapOf("factory" to factoryOf<Any>("value1", 123, true))
    val expected = """
      factory("value1", 123, true)
     """.trimIndent()

    doTest(contents, expected)
  }

  fun testFactoryWithEmbeddedMultipleArguments() {
    val contents = mapOf("factory" to factoryOf(mapOf("factory2" to factoryOf<Any>("value1")), 123, true))
    val expected = """
      factory(factory2("value1"), 123, true)
     """.trimIndent()

    doTest(contents, expected)
  }

  fun testFactoryWithEmbeddedMultipleArguments2() {
    val contents = mapOf("factory" to factoryOf(mapOf("factory2" to factoryOf("value2", mapOf("factory3" to factoryOf<Any>("value3", false)))), 123))
    val expected = """
      factory(factory2("value2", factory3("value3", false)), 123)
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

  fun testDoubleFunction(){
    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "build.gradle.dcl",
      ""
    )
    val dslFile = object : GradleDslFile(file, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    dslFile.parse()

    val block = DependenciesDslElement(dslFile, GradleNameElement.create("dependenciesDeclarative"))
    dslFile.setNewElement(block)

    val doubleFunction = GradleDslMethodCall(block, GradleNameElement.create("api"), "project")
    block.setNewElement(doubleFunction)

    val label = GradleDslLiteral(doubleFunction.argumentsElement, GradleNameElement.empty())
    label.setValue(":my")
    doubleFunction.addNewArgument(label)

    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val text = VfsUtil.loadText(file).replace("\r", "")
    assertEquals("""
      dependenciesDeclarative {
          api(project(":my"))
      }
    """.trimIndent(), text)
  }

  private fun doTest(contents: Map<String, Any>, expected: String) {
    val file = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "build.gradle.dcl",
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
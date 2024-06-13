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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.mockito.Mockito.mock
import java.util.LinkedList

class DeclarativeDslParserTest : LightPlatformTestCase() {

  fun testBlock() {
    val text = """
      androidApplication {
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf<String, Any>())
    doTest(text, expected)
  }

  fun testUnknownBlockName() {
    val text = """
      google {
      }
    """.trimIndent()
    val expected = mapOf<String, Any>()
    doTest(text, expected)
  }

  fun testRepeatingEmbeddedBlock() {
    val text = """
      androidApplication {
        defaultConfig {
        }
      }
      androidApplication {
        dataBinding {
        }
        defaultConfig {
        }
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("defaultConfig" to mapOf<String, Any>(), "dataBinding" to mapOf()))
    doTest(text, expected)
  }

  fun testAssignmentWithString() {
    val file = """
      androidApplication {
        namespace = "com.my"
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("mNamespace" to "com.my"))
    doTest(file, expected)
  }

  fun testAssignmentWithNumber() {
    val file = """
      androidApplication {
        namespace = 5
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("mNamespace" to 5))
    doTest(file, expected)
  }


  fun testAssignmentWithBoolean() {
    val file = """
      androidApplication {
        namespace = true
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("mNamespace" to true))
    doTest(file, expected)
  }

  fun testFactory() {
    val file = """
      androidApplication {
        api("androidx.application")
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("api" to listOf("androidx.application")))
    doTest(file, expected)
  }

  fun testFactoryWithMultipleArguments() {
    val toml = """
      androidApplication {
        api("androidx.application", true,123)
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("api" to listOf("androidx.application", true, 123)))
    doTest(toml, expected)
  }

  fun testTwoFactoryMethods() {
    val file = """
      androidApplication {
        api("androidx.application")
        api2("androidx.application2")
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("api" to listOf("androidx.application"), "api2" to listOf("androidx.application2")))
    doTest(file, expected)
  }

  fun testFactoryMethodNumberArgument() {
    val file = """
      androidApplication {
        api(23)
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("api" to listOf(23)))
    doTest(file, expected)
  }

  fun testFactoryMethodBooleanArgument() {
    val file = """
      androidApplication {
        fixit(true)
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("fixit" to listOf(true)))
    doTest(file, expected)
  }

  fun testFactoryMethodRecursiveArgument() {
    val file = """
      androidApplication {
        api(project(":myProject"))
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("api" to listOf(mapOf("project" to listOf(":myProject")))))
    doTest(file, expected)
  }

  fun testFactoryMethodNoAttributes(){
    val file = """
      androidApplication {
        method()
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("method" to listOf<String>()))
    doTest(file, expected)
  }

  private fun doTest(text: String, expected: Map<String, Any>) {
    val declarativeFile = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "build.gradle.dcl",
      text
    )
    val dslFile = object : GradleBuildFile(declarativeFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    assertEquals(expected, propertiesToMap(dslFile))
  }

  private fun propertiesToMap(file: GradleDslFile): Map<String, Any> {
    fun populate(key: String, element: GradleDslElement?, setter: (String, Any) -> Unit) {
      val value = when (element) {
        is GradleDslBlockElement -> {
          val newMap = LinkedHashMap<String, Any>()
          element.currentElements.forEach { populate(it.name, element.getElement(it.name)) { k, v -> newMap[k] = v } }
          newMap
        }

        is GradleDslMethodCall -> {
          val newList = LinkedList<Any>()
          element.arguments.forEach {
            if (it is GradleDslMethodCall) {
              populate(it.methodName, it) { k, v -> newList.add(mapOf(k to v)) }
            }
            else populate("", it) { _, v -> newList.add(v) }

          }
          newList
        }

        is GradleDslLiteral -> element.value ?: "null literal"

        else -> {
          "Unknown element: $element"
        }
      }
      setter(key, value)
    }

    val map = LinkedHashMap<String, Any>()
    file.properties.forEach { populate(it, file.getElement(it)) { key, value -> map[key] = value } }
    return map
  }
}
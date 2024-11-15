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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.mockito.Mockito.mock
import java.util.LinkedList

class DeclarativeDslParserTest : LightPlatformTestCase() {

  fun testBlock() {
    val text = """
      androidApp {
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf<String, Any>())
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
      androidApp {
        defaultConfig {
        }
      }
      androidApp {
        dataBinding {
        }
        defaultConfig {
        }
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("defaultConfig" to mapOf<String, Any>(), "dataBinding" to mapOf()))
    doTest(text, expected)
  }

  fun testAssignmentWithString() {
    val file = """
      androidApp {
        namespace = "com.my"
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("mNamespace" to "com.my"))
    doTest(file, expected)
  }

  fun testAssignmentWithNumber() {
    val file = """
      androidApp {
        namespace = 5
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("mNamespace" to 5))
    doTest(file, expected)
  }


  fun testAssignmentWithBoolean() {
    val file = """
      androidApp {
        namespace = true
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("mNamespace" to true))
    doTest(file, expected)
  }

  fun testFactory() {
    val file = """
      androidApp {
        api("androidx.application")
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("api" to listOf("androidx.application")))
    doTest(file, expected)
  }

  fun testFactoryWithMultipleArguments() {
    val toml = """
      androidApp {
        api("androidx.application", true, 123)
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("api" to listOf("androidx.application", true, 123)))
    doTest(toml, expected)
  }

  fun testTwoFactoryMethods() {
    val file = """
      androidApp {
        api("androidx.application")
        api2("androidx.application2")
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("api" to listOf("androidx.application"), "api2" to listOf("androidx.application2")))
    doTest(file, expected)
  }

  fun testFactoryMethodNumberArgument() {
    val file = """
      androidApp {
        api(23)
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("api" to listOf(23)))
    doTest(file, expected)
  }

  fun testFactoryMethodBooleanArgument() {
    val file = """
      androidApp {
        fixit(true)
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("fixit" to listOf(true)))
    doTest(file, expected)
  }

  fun testFactoryMethodRecursiveArgument() {
    val file = """
      androidApp {
        api(project(":myProject"))
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("api" to listOf(mapOf("project" to listOf(":myProject")))))
    doTest(file, expected)
  }

  fun testFactoryMethodNoAttributes(){
    val file = """
      androidApp {
        method()
      }
    """.trimIndent()
    val expected = mapOf("androidApp" to mapOf("method" to listOf<String>()))
    doTest(file, expected)
  }

  fun testExpressionFunction(){
    val file = """
      plugins {
        id("org.example").version("1")
        id("org.other").version("2")
      }
    """.trimIndent()
    val expected = mapOf("plugins" to listOf(
      mapOf("id" to "org.example", "version" to "1", ),
      mapOf("id" to "org.other", "version" to "2"))
    )
    doSettingsTest(file, expected)
  }

  fun testAssignment(){
    val file = """
      rootProject.name = "someName"
    """.trimIndent()
    val expected = mapOf("rootProject.name" to "someName")
    doSettingsTest(file, expected)
  }

  private fun doSettingsTest(text: String, expected: Map<String, Any>) {
    val declarativeFile = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "settings.gradle.dcl",
      text
    )
    val dslFile = object : GradleSettingsFile(declarativeFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    assertEquals(expected, propertiesToMap(dslFile))
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
    fun populate(element: GradleDslElement?): Any {
       return when (element) {
        is GradleDslElementList -> {
          val list = LinkedList<Any>()
          element.currentElements.forEach { list.add(populate(it)) }
          list
        }

        is GradlePropertiesDslElement -> {
          val newMap = LinkedHashMap<String, Any>()
          element.currentElements.forEach { newMap[it.name] = populate(element.getElement(it.name)) }
          newMap
        }

        is GradleDslMethodCall -> {
          val newList = LinkedList<Any>()
          element.arguments.forEach {
            if (it is GradleDslMethodCall) {
              newList.add(mapOf(it.methodName to populate(it)))
            }
            else newList.add(populate(it))
          }
          newList
        }

        is GradleDslLiteral -> element.value ?: "null literal"

        else -> {
          "Unknown element: $element"
        }
      }
    }

    val map = LinkedHashMap<String, Any>()
    file.properties.forEach {
      map[it] = populate(file.getElement(it))
    }
    return map
  }
}
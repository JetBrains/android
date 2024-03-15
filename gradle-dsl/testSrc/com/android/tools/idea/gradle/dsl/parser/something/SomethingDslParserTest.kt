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
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import org.mockito.Mockito.mock

class SomethingDslParserTest : LightPlatformTestCase() {

  fun testBlock() {
    val text = """
      android {
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
      android {
        defaultConfig {
        }
      }
      android {
        dataBinding {
        }
        defaultConfig {
        }
      }
    """.trimIndent()
    val expected = mapOf("android" to mapOf("defaultConfig" to mapOf<String, Any>(), "dataBinding" to mapOf()))
    doTest(text, expected)
  }


  private fun doTest(text: String, expected: Map<String, Any>) {
    val somethingFile = writeSomethingFile(text)
    val dslFile = object : GradleBuildFile(somethingFile, project, ":", BuildModelContext.create(project, mock())) {}
    dslFile.parse()
    assertEquals(expected, propertiesToMap(dslFile))
  }

  private fun writeSomethingFile(text: String): VirtualFile {
    lateinit var somethingFile: VirtualFile
    runWriteAction {
      val baseDir = project.guessProjectDir()!!
      somethingFile = baseDir.createChildData(this, "build.gradle.something")
      VfsUtil.saveText(somethingFile, text)
    }
    return somethingFile
  }

  private fun propertiesToMap(file: GradleDslFile): Map<String, Any> {
    fun populate(key: String, element: GradleDslElement?, setter: (String, Any) -> Unit) {
      val value = when (element) {
        is GradleDslBlockElement -> {
          val newMap = LinkedHashMap<String, Any>()
          element.currentElements.forEach { populate(it.name, element.getElement(it.name)) { k, v -> newMap[k] = v } }
          newMap
        }
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
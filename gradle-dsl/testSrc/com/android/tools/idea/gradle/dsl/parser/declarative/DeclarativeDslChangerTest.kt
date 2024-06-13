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
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.mockito.Mockito

class DeclarativeDslChangerTest : LightPlatformTestCase() {

  fun testUpdateAssignmentIntValue() {
    val file = """
      androidApplication {
        compileSdk = 33
      }
    """.trimIndent()
    val expected = """
      androidApplication {
        compileSdk = 34
      }
    """.trimIndent()
    doTest(file, expected) {
      val literal = (elements.values.first() as GradleDslBlockElement).elements.values.first() as GradleDslLiteral
      literal.setValue(34)
    }
  }

  fun testUpdateAssignmentStringValue() {
    val file = """
      androidApplication {
        namespace = "abc"
      }
    """.trimIndent()
    val expected = """
      androidApplication {
        namespace = "bcd"
      }
    """.trimIndent()
    doTest(file, expected) {
      val literal = (elements.values.first() as GradleDslBlockElement).elements.values.first() as GradleDslLiteral
      literal.setValue("bcd")
    }
  }

  fun testUpdateFactoryParameter() {
    val file = """
      declarativeDependencies {
        implementation("abc")
      }
    """.trimIndent()
    val expected = """
      declarativeDependencies {
        implementation("bcd")
      }
    """.trimIndent()
    doTest(file, expected) {
      val call = (elements.values.first() as DependenciesDslElement).elements.values.first() as GradleDslMethodCall
      (call.argumentsElement.expressions[0] as GradleDslLiteral).setValue("bcd")
    }
  }

  fun testUpdateFactoryName() {
    val file = """
      declarativeDependencies {
        implementation("abc")
      }
    """.trimIndent()
    val expected = """
      declarativeDependencies {
        api("abc")
      }
    """.trimIndent()
    doTest(file, expected) {
      val call = (elements.values.first() as DependenciesDslElement).elements.values.first() as GradleDslMethodCall
      call.nameElement.rename("api")
    }
  }

  fun testDeleteAssignment() {
    val file = """
      androidApplication {
          namespace = "abc"
          compileSdk = 33
      }
    """.trimIndent()
    val expected = """
      androidApplication {
          compileSdk = 33
      }
    """.trimIndent()
    doTest(file, expected) {
      val block = (elements.values.first() as GradleDslBlockElement)
      block.removeProperty("mNamespace")
    }
  }

  fun testAppendDependencyToBlock(){
    val file = """
      declarativeDependencies {
          api("someDependency")
      }
    """.trimIndent()
    val expected = """
      declarativeDependencies {
          api("someDependency")
          implementation("newDependency")
      }
    """.trimIndent()
    doTest(file, expected) {
      val block = (elements.values.first() as DependenciesDslElement)
      // new literal has externalSyntax = METHOD by default
      block.setNewLiteral("implementation", "newDependency")
    }
  }

  private fun doTest(text: String, expected: String, changer: GradleDslFile.() -> Unit) {
    val declarativeFile = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "build.gradle.dcl",
      text
    )
    val dslFile = object : GradleBuildFile(declarativeFile, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    dslFile.parse()
    changer(dslFile)
    WriteCommandAction.runWriteCommandAction(project) {
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val newText = VfsUtil.loadText(declarativeFile).replace("\r", "")
    assertEquals(expected, newText)
  }
}
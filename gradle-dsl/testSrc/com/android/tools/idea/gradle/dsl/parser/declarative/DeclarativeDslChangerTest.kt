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
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.android.DeclarativeAndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.compareWithExpectedPsi
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleScriptFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

@RunWith(JUnit4::class)
class DeclarativeDslChangerTest : LightPlatformTestCase() {

  @Test
  fun testUpdateAssignmentIntValue() {
    val file = """
      androidApp {
        compileSdk = 33
      }
    """.trimIndent()
    val expected = """
      androidApp {
        compileSdk = 34
      }
    """.trimIndent()
    doTest(file, expected) {
      val literal = (elements.values.first() as GradleDslBlockElement).elements.values.first() as GradleDslLiteral
      literal.setValue(34)
    }
  }

  @Test
  fun testUpdateAssignmentStringValue() {
    val file = """
      androidApp {
        namespace = "abc"
      }
    """.trimIndent()
    val expected = """
      androidApp {
        namespace = "bcd"
      }
    """.trimIndent()
    doTest(file, expected) {
      val literal = (elements.values.first() as GradleDslBlockElement).elements.values.first() as GradleDslLiteral
      literal.setValue("bcd")
    }
  }

  @Test
  fun testUpdateFactoryParameter() {
    val file = """
      androidApp {
        dependenciesDcl {
          implementation("abc")
        }
      }
    """.trimIndent()
    val expected = """
      androidApp {
        dependenciesDcl {
          implementation("bcd")
        }
      }
    """.trimIndent()
    doTest(file, expected) {
      val dependencies = (elements["androidApp"] as DeclarativeAndroidDslElement).elements["dependenciesDcl"]
      val call = (dependencies as DependenciesDslElement).elements.values.first() as GradleDslMethodCall
      (call.argumentsElement.expressions[0] as GradleDslLiteral).setValue("bcd")
    }
  }

  @Test
  fun testUpdateFactoryName() {
    val file = """
      androidApp {
        dependenciesDcl {
          implementation("abc")
        }
      }
    """.trimIndent()
    val expected = """
      androidApp {
        dependenciesDcl {
          api("abc")
        }
      }
    """.trimIndent()
    doTest(file, expected) {
      val dependencies = (elements["androidApp"] as DeclarativeAndroidDslElement).elements["dependenciesDcl"]
      val call = (dependencies as DependenciesDslElement).elements.values.first() as GradleDslMethodCall
      call.rename("api")
    }
  }

  @Test
  fun testDeleteAssignment() {
    val file = """
      androidApp {
          namespace = "abc"
          compileSdk = 33
      }
    """.trimIndent()
    val expected = """
      androidApp {
          compileSdk = 33
      }
    """.trimIndent()
    doTest(file, expected) {
      val block = (elements.values.first() as GradleDslBlockElement)
      block.removeProperty("mNamespace")
    }
  }

  @Test
  fun testUpdatePluginVersion() {
    val file = """
      plugins {
          id("org.example").version("1.0")
      }
    """.trimIndent()
    val expected = """
      plugins {
          id("com.android").version("2.0")
      }
    """.trimIndent()
    doSettingsTest(file, expected) {
      val plugins = (elements.values.first() as PluginsDslElement)
      val pluginDeclaration = (plugins.elements.values.first() as GradleDslInfixExpression)
      val elements = pluginDeclaration.elements.values
      assertThat(elements).hasSize(2)
      val version = (elements.toList()[1] as? GradleDslLiteral)
      val id = (elements.toList()[0] as? GradleDslLiteral)
      assertThat(version).isNotNull()
      assertThat(id).isNotNull()
      version!!.setValue("2.0")
      id!!.setValue("com.android")
    }
  }

  @Test
  fun testRemoveLastFunctionArgument() {
    val file = """
     androidApp {
       dependenciesDcl {
         compile("org.example:1.0")
       }
     }
    """.trimIndent()
    val expected = """
      androidApp {
      }
    """.trimIndent()
    doTest(file, expected) {
      val android = (elements.values.first() as AndroidDslElement)
      val dependencies = (android.elements.values.first() as DependenciesDslElement)
      val compile =  (dependencies.elements.values.first() as GradleDslMethodCall)
      assertThat(compile.arguments).hasSize(1)
      compile.arguments[0].delete()
    }
  }


  @Test
  fun testAppendDependencyToBlock(){
    val file = """
      androidApp {
          dependenciesDcl {
              api("someDependency")
          }
      }
    """.trimIndent()
    val expected = """
      androidApp {
          dependenciesDcl {
              api("someDependency")
              implementation("newDependency")
          }
      }
    """.trimIndent()
    doTest(file, expected) {
      val dependencies = (elements["androidApp"] as DeclarativeAndroidDslElement).elements["dependenciesDcl"]
      val block = (dependencies as DependenciesDslElement)
      // new literal has externalSyntax = METHOD by default
      block.setNewLiteral("implementation", "newDependency")
    }
  }

  private fun doSettingsTest(text: String, expected: String, changer: GradleDslFile.() -> Unit) {
    val declarativeFile = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "settings.gradle.dcl",
      text
    )
    val dslFile = object : GradleSettingsFile(declarativeFile, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    handleChangeAndVerification(dslFile, changer, declarativeFile, expected)
    compareWithExpectedPsi(project, dslFile, expected)
  }

  private fun handleChangeAndVerification(
    dslFile: GradleScriptFile,
    changer: GradleDslFile.() -> Unit,
    declarativeFile: VirtualFile,
    expected: String
  ) {
    dslFile.parse()
    WriteCommandAction.runWriteCommandAction(project) {
      changer(dslFile)
      dslFile.applyChanges()
      dslFile.saveAllChanges()
    }
    val newText = VfsUtil.loadText(declarativeFile).replace("\r", "")
    assertEquals(expected, newText)
  }

  private fun doTest(text: String, expected: String, changer: GradleDslFile.() -> Unit) {
    val declarativeFile = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "build.gradle.dcl",
      text
    )
    val dslFile = object : GradleBuildFile(declarativeFile, project, ":", BuildModelContext.create(project, Mockito.mock())) {}
    handleChangeAndVerification(dslFile, changer, declarativeFile, expected)
    compareWithExpectedPsi(project, dslFile, expected)
  }
}
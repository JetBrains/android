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
package com.android.tools.idea.gradle.dsl.formatter.declarative

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Rule
import org.junit.Test

class DeclarativeFormatterTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  private val project: Project by lazy { myFixture.project }

  @Test
  fun testBlock() {
    doTest("""
      block{}""","""
      block {
      }
      """)
  }

  @Test
  fun testBlockWithAssignment() {
    doTest("""
      block{key="value" key2="value2"}
      ""","""
      block {
          key = "value"
          key2 = "value2"
      }
      """)
  }

  @Test
  fun testBlockWithFunction() {
    doTest("""
      block{function("parameter") function2("parameter2")}
      ""","""
      block {
          function("parameter")
          function2("parameter2")
      }
      """)
  }

  @Test
  fun testEmbeddedBlockWithContent() {
    doTest("""
      block{block{key="value"}}
      ""","""
      block {
          block {
              key = "value"
          }
      }
      """)
  }

  @Test
  fun testFactoryMultiArgument() {
    doTest("""
      factory(    "val1",123,
false    )
      ""","""
      factory("val1", 123, false)
      """)
  }

  @Test
  fun testFactoryMultiArgumentAndEmbeddedFunctions() {
    doTest("""
      factory(    factory2(   "val1"
      ),123,
false    )
      ""","""
      factory(factory2("val1"), 123, false)
      """)
  }

  @Test
  fun testComplexFile() {
    val before = """
        plugins{ id("org.gradle.experimental.android-application")  }      androidApplication
        {namespace = "com.example.myapplication"
       compileSdk=34}
             // changed a little to not interfere with non declarative plugin
       declarativeDependencies{
       implementation("com.google.guava:guava:32.1.2-jre") implementation("org.apache.commons:commons-lang3:3.13.0")
       }"""
    val after =
      """
      plugins {
          id("org.gradle.experimental.android-application")
      }
      androidApplication {
          namespace = "com.example.myapplication"
          compileSdk = 34
      }
      // changed a little to not interfere with non declarative plugin
      declarativeDependencies {
          implementation("com.google.guava:guava:32.1.2-jre")
          implementation("org.apache.commons:commons-lang3:3.13.0")
      }
      """
    doTest(before, after)
  }

  private fun doTest(before: String, after: String) {
    myFixture.loadNewFile(
      "build.gradle.dcl",
      before.trimIndent()
    )
    WriteCommandAction.writeCommandAction(project).run<RuntimeException> {
      CodeStyleManager.getInstance(project)
        .reformatText(myFixture.file, listOf(myFixture.file.textRange))
    }
    myFixture.checkResult(after.trimIndent())
  }
}
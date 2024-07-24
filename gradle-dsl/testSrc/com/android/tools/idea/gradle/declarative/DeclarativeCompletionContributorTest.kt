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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Test is based on generated schema files. Schema has declarations for
// androidApplication{compileSdk, namespace, jdkVersion, minSdk}, declarativeDependencies{api, implementation}
@RunsInEdt
class DeclarativeCompletionContributorTest : DeclarativeSchemaTestBase() {
  @get:Rule
  override val projectRule = AndroidProjectRule.onDisk().onEdt()
  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before() {
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true)
  }

  @After
  fun onAfter() = StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride()

  @Test
  fun testBasicRootCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doTest("and$caret") { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "androidApplication", "androidLibrary"
      )
    }
  }

  @Test
  fun testInsideBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doTest("""
      androidApplication{
        a$caret
      }
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "applicationId", "coreLibraryDesugaring", "namespace", "versionName"
      )
    }
  }

  @Test
  fun testPluginBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_SETTINGS_SCHEMAS)
    doTest("""
      pl$caret
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "pluginManagement", "plugins"
      )
    }
  }

  @Test
  fun testInsideApplicationBlockCompletion() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doTest("""
      androidApplication{
        a$caret
      }
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "applicationId", "coreLibraryDesugaring", "namespace", "versionName"
      )
    }
  }

  @Test
  fun testInsideApplicationBlockCompletionNoTyping() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doTest("""
      androidApplication {
        $caret
      }
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "applicationId", "buildTypes", "compileSdk", "coreLibraryDesugaring", "dependencies",
        "jdkVersion", "minSdk", "namespace", "versionCode", "versionName"
      )
    }
  }

  @Test
  fun testInsideFileCompletionNoTyping() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doTest("""
        $caret
      """) { suggestions ->
      assertThat(suggestions.toList()).containsExactly(
        "androidApplication", "androidLibrary", "javaApplication", "javaLibrary", "jvmApplication", "jvmLibrary", "kotlinApplication",
        "kotlinJvmApplication", "kotlinJvmLibrary", "kotlinLibrary"
      )
    }
  }

  @Test
  fun testCompletionStringProperty() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doCompletionTest("""
      androidApplication {
        applica$caret
      }
      """, """
      androidApplication {
        applicationId = "$caret"
      }
      """)
  }

  @Test
  fun testCompletionBlock() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doCompletionTest(
      "androidApp$caret",
      """
        androidApplication {
          $caret
        }
        """.trimIndent())
  }

  @Test
  fun testCompletionBlock2() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doCompletionTest(
      """
        androidLibrary {
          dependenci$caret
        }""".trimIndent(),
      """
        androidLibrary {
          dependencies {
            $caret
          }
        }""".trimIndent())
  }

  @Test
  fun testCompletionInt() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)

    doCompletionTest("""
      androidApplication {
           minS$caret
      }
    """, """
      androidApplication {
           minSdk = $caret
      }
    """)
  }

  @Test
  fun testCompletionBoolean() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    doCompletionTest("""
      androidLibrary{
        testing {
          testOptions {
              returnDefaultV$caret
          }
        }
      }
    """, """
      androidLibrary{
        testing {
          testOptions {
              returnDefaultValues = $caret
          }
        }
      }
    """)
  }

  @Test
  fun testCompletionFunction() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)

    doCompletionTest("""
      androidLibrary {
        dependencies {
          implemen$caret
        }
      }
    """, """
      androidLibrary {
        dependencies {
          implementation($caret)
        }
      }
    """)
  }

  private fun doTest(declarativeFile: String, check: (List<String>) -> Unit) {
    doTest(declarativeFile, "build.gradle.dcl", check)
  }

  private fun doTest(declarativeFile: String, fileName: String, check: (List<String>) -> Unit) {
    val buildFile = fixture.addFileToProject(
      fileName, declarativeFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()
    val list: List<String> = fixture.lookupElements!!.map {
      val presentation = LookupElementPresentation()
      it.renderElement(presentation)
      it.lookupString
    }
    check.invoke(list)
  }

  private fun doCompletionTest(declarativeFile: String, fileAfter: String) {
    val buildFile = fixture.addFileToProject(
      "build.gradle.dcl", declarativeFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()

    val caretOffset = fileAfter.indexOf(caret)
    val cleanFileAfter = fileAfter.replace(caret, "")

    assertThat(buildFile.text).isEqualTo(cleanFileAfter)
    assertThat(fixture.editor.caretModel.offset).isEqualTo(caretOffset)
  }
}
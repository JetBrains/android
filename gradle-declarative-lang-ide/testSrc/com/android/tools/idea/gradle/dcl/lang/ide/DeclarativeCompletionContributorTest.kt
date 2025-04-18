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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRef
import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.Entry
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// Test is based on generated schema files.
@RunWith(JUnit4::class)
@RunsInEdt
class DeclarativeCompletionContributorTest : UsefulTestCase() {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()
  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before() {
    DeclarativeIdeSupport.override(true)
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)
  }

  @After
  fun onAfter() = DeclarativeIdeSupport.clearOverride()

  @Test
  fun testBasicRootCompletion() {
    doTest("and$caret") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "androidApp", "androidLibrary"
      )
    }
    doTest("and$caret { }") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "androidApp", "androidLibrary"
      )
    }
  }

  @Test
  fun testInsideBlockCompletion() {
    doTest("""
      androidApp{
        a$caret
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildFeatures", "defaultConfig", "namespace", "productFlavors"
      )
    }
  }

  @Test
  fun testBeforeOpenBlock() {
    doNoSuggestionTest("""
      androidApp $caret{
        
      }
      """)
    doNoSuggestionTest("""
      androidApp$caret{
        
      }
      """)
  }

  @Test
  fun testPluginBlockCompletion() {
    doTest("""
      pl$caret
      """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "pluginManagement", "plugins"
      )
    }
  }

  @Test
  fun testInsideApplicationBlockCompletion() {
    doTest("""
      androidApp{
        a$caret
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildFeatures", "defaultConfig", "namespace", "productFlavors"
      )
    }
  }

  @Test
  fun testAfterPropertyCompletion() {
    doNoSuggestionTest("""
      androidLibrary {
          compileSdk = 1$caret
        }""".trimIndent()
    )

    doNoSuggestionTest("""
      androidLibrary {
          compileSdk = 1${caret}3
        }""".trimIndent()
    )
  }

  @Test
  fun testInsideApplicationBlockCompletionNoTyping() {
    doTest("""
      androidApp {
        $caret
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "buildFeatures", "buildOutputs", "buildTypes", "compileOptions",
        "compileSdk", "defaultConfig", "dependenciesDcl", "lint", "namespace",
        "productFlavors", "signingConfigs", "sourceSets"
      )
    }
  }

  @Test
  fun testInsideFileCompletionNoTyping() {
    doTest("""
        $caret
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "androidApp", "androidLibrary", "layout"
      )
    }
  }

  @Test
  fun testSuggestionOfFactoryBlock() {
    doTest("""
    androidApp {
      buildTypes{
        $caret
      }
    }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("buildType")
    }
  }

  @Test
  fun testSuggestionInFactoryBlock() {
    doTest("""
    androidApp {
      buildTypes{
        buildType("new"){
          $caret
        }
      }
    }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("isMinifyEnabled")
    }
  }

  @Test
  fun testEnumIsSuggestion() {
    doTest("""
    androidApp {
        compileOptions {
           $caret
        }
      }
    }
      """) { suggestions ->
      // sourceCompatibility and targetCompatibility are enums
      Truth.assertThat(suggestions.toList()).containsExactly(
        "encoding", "isCoreLibraryDesugaringEnabled", "sourceCompatibility", "targetCompatibility"
      )
    }
  }

  @Test
  fun testLayoutFileFactory() {
    // test first level
    doTest("""
      androidApp {
        bundle {
          deviceTargetingConfig = $caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "layout"
      )
    }

    // test second level
    doTest("""
      androidApp {
        bundle {
          deviceTargetingConfig = layout.$caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "projectDirectory", "settingsDirectory"
      )
    }
    // test last level
    doTest("""
      androidApp {
        bundle {
          deviceTargetingConfig = layout.projectDirectory.$caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly(
        "dir", "file"
      )
    }
  }

  @Test
  fun testCompletionStringProperty() {
    doCompletionTest("""
      androidApp {
        defaultConfig {
          versionNameS$caret
        }
      }
      """, """
      androidApp {
        defaultConfig {
          versionNameSuffix = "$caret"
        }
      }
      """)
  }

  @Test
  fun testCompletionBlock() {
    // inserting caret with indent position (default 4 whitespaces)
    doCompletionTest(
      "androidApp$caret",
      """
        androidApp {
            $caret
        }
        """.trimIndent())
  }

  @Test
  fun testCompletionBlockWithUpdatedIndent() {
    doCompletionTest(
      "androidApp$caret",
      """
        androidApp {
              $caret
        }
        """.trimIndent()) { psiFile ->
      CodeStyle.getIndentOptions(psiFile).INDENT_SIZE = 6
    }
  }

  @Test
  fun testCompletionBlock2() {
    doCompletionTest(
      """
        androidLibrary {
          dependenci$caret
        }""".trimIndent(),
      """
        androidLibrary {
          dependenciesDcl {
              $caret
          }
        }""".trimIndent())
  }

  @Test
  fun testCompletionInt() {
    doCompletionTest("""
     androidApp {
       defaultConfig {
         maxSd$caret
       }
     }
     """, """
     androidApp {
       defaultConfig {
         maxSdk = $caret
       }
     }
     """)
  }

  @Test
  fun testCompletionBoolean() {
    doCompletionTest("""
    androidLibrary {
      defaultConfig {
        multiDexEnab$caret
      }
    }
    """, """
    androidLibrary {
      defaultConfig {
        multiDexEnabled = $caret
      }
    }
    """)
  }

  @Test
  fun testCompletionFunction() {
    doCompletionTest("""
      androidLibrary {
        dependenciesDcl {
          androidTestIm$caret
        }
      }
    """.trimIndent(), """
    androidLibrary {
      dependenciesDcl {
        androidTestImplementation($caret)
      }
    }
    """.trimIndent())
  }

  @Test
  fun testCompletionEnum() {
    doCompletionTest("""
      androidApp {
        compileOptions {
          sourceCom$caret
        }
      }
    """.trimIndent(), """
    androidApp {
      compileOptions {
        sourceCompatibility = $caret
      }
    }
    """.trimIndent())
  }

  @Test
  fun testCompletionUriValues() {
    doCompletionTest("""
     dependencyResolutionManagement {
       repositories {
         maven {
            ur$caret
         }
       }
     }
     """,
     "settings.gradle.dcl", """
     dependencyResolutionManagement {
       repositories {
         maven {
            url = uri("$caret")
         }
       }
     }
     """)
  }

  @Test
  fun testCompletionUriValues2() {
    doCompletionTest("""
   dependencyResolutionManagement {
     repositories {
       maven {
          url = u$caret
       }
     }
   }
   """, "settings.gradle.dcl", """
   dependencyResolutionManagement {
     repositories {
       maven {
          url = uri($caret)
       }
     }
   }
   """)
  }

  @Test
  fun testCompletionEnumValues() {
    doTest("""
      androidApp {
        compileOptions {
          sourceCompatibility = $caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllIn(
        listOf("VERSION_1_1", "VERSION_1_2", "VERSION_26", "VERSION_27", "VERSION_HIGHER")
      )
    }
  }

  @Test
  fun testBooleanProperty() {
    doTest("""
      androidApp {
        buildFeatures {
          dataBinding = $caret
        }
      }
      """) { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("true", "false")
    }
  }

  @Test
  fun testSuggestionsUriFunction() {
    doTest("""
    dependencyResolutionManagement {
      repositories {
        maven {
          url = $caret
        }
      }
    }
    """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("rootProject", "uri")
    }
  }

  @Test
  fun testSuggestionsPlugins() {
    doTest("""
    plugins {
      id("some").$caret
    }
    """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsAllIn(listOf("version", "apply"))
    }
  }

  @Test
  fun testRootProjects() {
    doTest("""
    rootProject.$caret
    """, "settings.gradle.dcl") { suggestions ->
      Truth.assertThat(suggestions.toList()).containsExactly("name")
    }
  }

  @Test
  fun testRootProject() {
    doCompletionTest("""
    rootProje$caret
   """, "settings.gradle.dcl", """
    rootProject.name = "$caret"
   """)
  }

  @Test
  fun testRootProject2() {
    doCompletionTest("""
    rootProject$caret
   """, "settings.gradle.dcl", """
    rootProject.name = "$caret"
   """)
  }

  @Test
  // data property should not have another data property
  // except rootProject.name
  // once this test fail we need to add more completion logic
  fun testNoDataPropertyForDataProperty() {
    val knownPaths = listOf(listOf("rootProject", "name"))
    val schema = DeclarativeService.getInstance(fixture.project).getDeclarativeSchema() ?: return

    fun EntryWithContext.check() {
      val maybeDataProperty = entry
      if (maybeDataProperty is DataProperty) {
        val type = maybeDataProperty.valueType
        if (type is DataClassRef)
          getNextLevel().forEach { nextLevelElement ->
            (nextLevelElement.entry as? DataProperty)?.let{
              Truth.assertThat(listOf(maybeDataProperty.name, it.name)).isIn(knownPaths)
            }
          }
      }
    }

    fun EntryWithContext.iterate(seen: List<Entry>) {
      check()
      getNextLevel().forEach {
        if (!seen.contains(it.entry)) it.iterate(seen + it.entry)
      }
    }

    // looking for root properties that has simple props like rootProject.name
    schema.getTopLevelEntries("settings.gradle.dcl").forEach { it.iterate(listOf(it.entry)) }
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

  private fun doNoSuggestionTest(declarativeFile: String) {
    val buildFile = fixture.addFileToProject(
      "build.gradle.dcl", declarativeFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()
    Truth.assertThat(fixture.lookup).isNull()
  }

  private fun doCompletionTest(declarativeFile: String, fileAfter: String, update:(PsiFile) -> Unit = {}) =
    doCompletionTest(declarativeFile, "build.gradle.dcl", fileAfter, update )

  private fun doCompletionTest(declarativeFile: String, fileName: String, fileAfter: String, update:(PsiFile) -> Unit = {}) {
    val buildFile = fixture.addFileToProject(fileName, declarativeFile)
    update(buildFile)
    fixture.configureFromExistingVirtualFile(buildFile.virtualFile)
    fixture.completeBasic()

    val caretOffset = fileAfter.indexOf(caret)
    val cleanFileAfter = fileAfter.replace(caret, "")

    Truth.assertThat(buildFile.text).isEqualTo(cleanFileAfter)
    Truth.assertThat(fixture.editor.caretModel.offset).isEqualTo(caretOffset)
  }
}

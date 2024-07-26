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
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.modules
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@org.junit.Ignore("b/349894866")
@RunWith(JUnit4::class)
@RunsInEdt
class DeclarativeAnnotatorTest: DeclarativeSchemaTestBase() {

  @get:Rule
  override val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before() = StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true)

  @After
  fun onAfter() = StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride()

  @Test
  fun checkFunctionIdentifier() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()
    val file = addDeclarativeBuildFile("""
      androidApplication {
         ${"coreLibraryDesugarin" highlightedAs HighlightSeverity.ERROR}("something")
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }


  @Test
  fun dontCheckInFailedBlock() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
      ${"androidApplication1" highlightedAs HighlightSeverity.ERROR} {
        ${"versionUnknownCode" highlightedAs HighlightSeverity.ERROR} = 8
        ${"versionName" highlightedAs HighlightSeverity.ERROR} = "0.1.2"
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun dontCheckInFailedBlock2() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
      androidApplication {
        ${"myDeps2" highlightedAs HighlightSeverity.ERROR } {
             ${"debugImplementation" highlightedAs HighlightSeverity.ERROR }("com.google.guava:guava:30.1.1-jre")
             ${"wrongImplementation" highlightedAs HighlightSeverity.ERROR }("com.google.guava:guava:30.1.1-jre")
        }
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkRootBlockIdentifier() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
      ${"androidapplication" highlightedAs HighlightSeverity.ERROR}{
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkPropertyIdentifier() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
      androidApplication {
           minSdk = 33
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  @Ignore("New schema does not have NDO")
  fun stopCheckingWithUnknownNamedDomainObjects() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    val file = addDeclarativeBuildFile("""
      androidApplication {
        appBuildTypes {
          // not in schema
          staging {
            isMinifyEnabled = true
          }
        }
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  @Ignore("New schema does not have NDO")
  fun checkingWithKnownNamedDomainObjects() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
      androidApplication {
        appBuildTypes {
          debug {
            ${"isMinifyEnableddd" highlightedAs HighlightSeverity.ERROR} = false
          }
        }
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkAdvancedCase() {
    // DSL is different from AGP as we adopting to Declarative requirements.
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
    androidLibrary {
        namespace = "com.google.samples.apps.nowinandroid.feature.bookmarks"
        dependencies {
            implementation(project(":core:data"))
        }
        feature {
            description = "Calling the configure method enables this lib to be treated as a feature"
        }
        compose {
            description = "Calling the configure method enables compose support"
        }
        testing {
            dependencies {
                implementation(project(":core:testing"))
                androidImplementation(project(":core:testing"))
            }
        }
    }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkMultipleErrorAdvancedCase() {
    // DSL is different from AGP as we adopting to Declarative requirements.
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
    androidLibrary {
        ${"nameSpace" highlightedAs HighlightSeverity.ERROR} = "com.google.samples.apps.nowinandroid.feature.bookmarks"
        dependencies {
            implementation(project(":core:data"))
        }
        feature {
            description = "Calling the configure method enables this lib to be treated as a feature"
        }
        compose {
            ${"desc" highlightedAs HighlightSeverity.ERROR} = "Calling the configure method enables compose support"
        }
        testing {
            dependencies {
                implementation(project(":core:testing"))
                androidImplementation(project(":core:testing"))
            }
        }
    }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkCorrectSettingsSyntax(){
    writeToSchemaFile(TestFile.DECLARATIVE_SETTINGS_SCHEMAS)
    checkSchemaErrors()

    val file = fixture.addFileToProject("settings.gradle.dcl",
    """
      rootProject {
         name = "nowinandroid"
      }

      enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
      include(":app")
      include(":app-nia-catalog")
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkCorrectDemoSyntax(){
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = fixture.addFileToProject("build.gradle.dcl",
    """
    androidApplication {
       jdkVersion = 11
       compileSdk = 33
    }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongPropertyType() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
    androidLibrary {
        ${"namespace = 1" highlightedAs HighlightSeverity.ERROR}
   }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongBlockType() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
    ${"androidLibrary = 1" highlightedAs HighlightSeverity.ERROR}
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongFunctionType() {
    writeToSchemaFile(TestFile.DECLARATIVE_NEW_FORMAT_SCHEMAS)
    checkSchemaErrors()

    val file = addDeclarativeBuildFile("""
    androidLibrary {
        dependencies {
            ${"implementation = \"dependency\"" highlightedAs HighlightSeverity.ERROR}
         }
    }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  private fun checkSchemaErrors(){
    val project = projectRule.project

    val schema = DeclarativeService.getInstance(project).getSchema()
    Truth.assertThat(schema).isNotNull()
    Truth.assertThat(schema!!.failureHappened).isFalse()
  }

  private fun addDeclarativeBuildFile(text: String) = fixture.addFileToProject("build.gradle.dcl", text.trimIndent())
}

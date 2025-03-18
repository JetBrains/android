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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.onEdt
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class DeclarativeAnnotatorTest : UsefulTestCase() {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before() {
    DeclarativeIdeSupport.override(true)
  }

  @After
  fun onAfter() = DeclarativeIdeSupport.clearOverride()

  @Test
  fun checkFunctionIdentifier() {
    doBuildFileTest("""
      androidApp {
         ${"coreLibraryDesugarin" highlightedAs HighlightSeverity.ERROR}("something")
      }
    """)
  }


  @Test
  fun dontCheckInFailedBlock() {
    doBuildFileTest("""
      ${"androidApplication1" highlightedAs HighlightSeverity.ERROR} {
        ${"versionUnknownCode" highlightedAs HighlightSeverity.ERROR} = 8
        ${"versionName" highlightedAs HighlightSeverity.ERROR} = "0.1.2"
      }
    """)
  }

  @Test
  fun dontCheckInFailedBlock2() {
    doBuildFileTest("""
      androidApp{
        ${"myDeps2" highlightedAs HighlightSeverity.ERROR} {
             ${"debugImplementation" highlightedAs HighlightSeverity.ERROR}("com.google.guava:guava:30.1.1-jre")
             ${"wrongImplementation" highlightedAs HighlightSeverity.ERROR}("com.google.guava:guava:30.1.1-jre")
        }
      }
    """)
  }

  @Test
  fun checkRootBlockIdentifier() {
    doBuildFileTest("""
      ${"androidapplication" highlightedAs HighlightSeverity.ERROR}{
      }
    """)
  }

  @Test
  fun checkPropertyIdentifier() {
    doBuildFileTest("""
      androidApp {
         defaultConfig {
           minSdk = 33
         }
      }
    """)
  }

  @Test
  fun checkInvalidUnicodeString() {
    // Annotator should skip highlighting of this element
    // as strings are parsed/highlighted by highlighting lexer
    doBuildFileTest("""
      androidApp {
        namespace = "org.gradle.experim\uental.android.app"
      }
    """)
  }

  @Test
  @Ignore("New schema does not have NDO")
  fun stopCheckingWithUnknownNamedDomainObjects() {
    doBuildFileTest("""
      androidApplication {
        appBuildTypes {
          // not in schema
          staging {
            isMinifyEnabled = true
          }
        }
      }
    """)
  }

  @Test
  @Ignore("New schema does not have NDO")
  fun checkingWithKnownNamedDomainObjects() {
    doBuildFileTest("""
      androidApp {
        appBuildTypes {
          debug {
            ${"isMinifyEnableddd" highlightedAs HighlightSeverity.ERROR} = false
          }
        }
      }
    """)
  }

  @Test
  fun checkAdvancedCase() {
    // DSL is different from AGP as we adopting to Declarative requirements.
    doBuildFileTest("""
    androidLibrary {
        namespace = "com.google.samples.apps.nowinandroid.feature.bookmarks"
        dependenciesDcl {
            implementation(project(":core:data"))
        }
        buildFeatures {
            buildConfig = true
        }
        compileOptions {
           encoding = ""
        }
    }
    """)
  }

  @Test
  fun checkMultipleErrorAdvancedCase() {
    // DSL is different from AGP as we adopt to Declarative requirements.
    doBuildFileTest("""
    androidLibrary {
        ${"nameSpace" highlightedAs HighlightSeverity.ERROR} = "com.google.samples.apps.nowinandroid.feature.bookmarks"
        dependenciesDcl {
            implementation(project(":core:data"))
        }
         buildFeatures {
            buildConfig = true
        }
        compileOptions {
           ${"encode" highlightedAs HighlightSeverity.ERROR} = ""
        }
    }
    """)
  }

  @Test
  fun checkFactoryBlock() {
    doBuildFileTest("""
    androidApp {
      buildTypes {
       buildType("new"){ }
      }
    }
    """)
  }

  @Test
  fun checkFactoryBlockNegative() {
    doBuildFileTest("""
    androidApp {
      buildTypes {
        ${"buildTypes" highlightedAs HighlightSeverity.ERROR}("new"){ }
      }
    }
    """)
  }

  @Test
  fun checkCorrectSettingsSyntax() {
    doSettingsFileTest(
      """
      rootProject.name = "nowinandroid"

      enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
      include(":app")
      include(":app-nia-catalog")
    """)
  }

  @Test
  fun checkCorrectDemoSyntax() {
    doBuildFileTest("""
    androidApp {
       compileSdk = 33
    }
    """)
  }

  @Test
  fun checkWrongPropertyType() {
    doBuildFileTest("""
    androidLibrary {
        ${"namespace = 1" highlightedAs HighlightSeverity.ERROR}
   }
    """)
  }

  @Test
  fun checkWrongBlockType() {
    doBuildFileTest("""
    ${"androidLibrary = 1" highlightedAs HighlightSeverity.ERROR}
    """)
  }

  @Test
  fun checkWrongFunctionType() {
    doBuildFileTest("""
    androidLibrary {
        dependenciesDcl {
            ${"implementation = \"dependency\"" highlightedAs HighlightSeverity.ERROR}
         }
    }
    """)
  }

  @Test
  fun checkEnums() {
    doBuildFileTest("""
     androidApp {
       compileOptions {
         sourceCompatibility = VERSION_15
         ${"sourceCompatibility = 123" highlightedAs HighlightSeverity.ERROR}
       }
    }
    """)
  }

  @Test
  fun checkFunctionChain() {
    doSettingsFileTest("""
     plugins {
       id("some.plugin").version("1.0")
     }
    """)
  }

  @Test
  fun checkWrongTypeMessage() {
    doBuildFileTest("""
      androidApp {
        dependenciesDcl {
           ${"api = \"org.example:example:1.0\"".highlightedAs(HighlightSeverity.ERROR, "Element type should be of type: Factory")}
        }
      }
    """)
  }

  @Test
  fun checkRootProject() {
    doSettingsFileTest("""
      rootProject.name = "some"
      ${"rootProject.name = 1" highlightedAs HighlightSeverity.ERROR}
      rootProject.${"abc" highlightedAs HighlightSeverity.ERROR} = "some"
    """)
  }

  @Test
  fun layoutPositiveTest() {
    doBuildFileTest("""
       androidApp {
        bundle {
          deviceTargetingConfig = layout.projectDirectory.file("myfile")
        }
      }
    """)
  }

  @Test
  fun listProperty() {
    doPatchedBuildFileTest("""
       androidApp {
         buildTypes {
           buildType("debug") {
             matchingFallbacks = listOf("a", "b")
           }
         }
       }
    """)
  }

  @Test
  fun listOfRegularFilesProperty() {
    doPatchedBuildFileTest("""
       androidApp {
         fakeFileList = listOf(layout.projectDirectory.file("aaa"), layout.projectDirectory.file("bbb"))
       }
    """)
  }


  @Test
  fun listOfRegularFilesPropertyNegativeTest() {
    doPatchedBuildFileTest("""
       androidApp {
         fakeFileList = listOf(layout.${ "projectdirectory" highlightedAs HighlightSeverity.ERROR }.${ "file" highlightedAs HighlightSeverity.ERROR }("aaa"), layout.projectDirectory.file("bbb"))
       }
    """)
  }

  @Test
  fun listPropertyWrongValue() {
    doPatchedBuildFileTest("""
       androidApp {
         buildTypes {
           buildType("debug") {
            ${ "matchingFallbacks =\"a\"" highlightedAs HighlightSeverity.ERROR }
           }
         }
       }
    """)
  }

  @Test
  fun layoutNegativeTest() {
    doBuildFileTest("""
       androidApp {
        bundle {
          deviceTargetingConfig = ${"Layout" highlightedAs HighlightSeverity.ERROR}.${"projectDirectory" highlightedAs HighlightSeverity.ERROR}.${"file" highlightedAs HighlightSeverity.ERROR}("myfile")
          deviceTargetingConfig = layout.${"ProjectDirectory" highlightedAs HighlightSeverity.ERROR}.${"file" highlightedAs HighlightSeverity.ERROR}("myfile")
          deviceTargetingConfig = layout.projectDirectory.${"File" highlightedAs HighlightSeverity.ERROR}("myfile")
        }
      }
    """)
  }

  @Test
  fun checkMavenRepo() {
    doSettingsFileTest("""
      pluginManagement {
        repositories {
            maven {
              url = uri("/Users/alexgolubev/src/agp-main/prebuilts/tools/common/m2/repository")
            }
        }
      }
    """)
  }

  private fun doBuildFileTest(buildFileContent: String) {
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)

    val file = addDeclarativeBuildFile(buildFileContent)
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  private fun doPatchedBuildFileTest(buildFileContent: String) {
    registerTestDeclarativeServicePatchedSchema(projectRule.project, fixture.testRootDisposable)

    val file = addDeclarativeBuildFile(buildFileContent)
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  private fun doSettingsFileTest(settingsFileContent: String) {
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)

    val file = addDeclarativeSettingsFile(settingsFileContent)
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  private fun addDeclarativeBuildFile(text: String) = fixture.addFileToProject("build.gradle.dcl", text.trimIndent())
  private fun addDeclarativeSettingsFile(text: String) = fixture.addFileToProject("settings.gradle.dcl", text.trimIndent())
}
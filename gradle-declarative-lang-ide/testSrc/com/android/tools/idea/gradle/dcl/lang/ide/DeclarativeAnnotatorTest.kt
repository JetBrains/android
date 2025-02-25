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
class DeclarativeAnnotatorTest: UsefulTestCase() {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture }

  @Before
  fun before(){
    DeclarativeIdeSupport.override(true)
    registerTestDeclarativeService(projectRule.project, fixture.testRootDisposable)
  }

  @After
  fun onAfter() = DeclarativeIdeSupport.clearOverride()

  @Test
  fun checkFunctionIdentifier() {
    val file = addDeclarativeBuildFile("""
      androidApp {
         ${"coreLibraryDesugarin" highlightedAs HighlightSeverity.ERROR}("something")
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }


  @Test
  fun dontCheckInFailedBlock() {
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
    val file = addDeclarativeBuildFile("""
      androidApp{
        ${"myDeps2" highlightedAs HighlightSeverity.ERROR} {
             ${"debugImplementation" highlightedAs HighlightSeverity.ERROR}("com.google.guava:guava:30.1.1-jre")
             ${"wrongImplementation" highlightedAs HighlightSeverity.ERROR}("com.google.guava:guava:30.1.1-jre")
        }
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkRootBlockIdentifier() {
    val file = addDeclarativeBuildFile("""
      ${"androidapplication" highlightedAs HighlightSeverity.ERROR}{
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkPropertyIdentifier() {
    val file = addDeclarativeBuildFile("""
      androidApp {
         defaultConfig {
           minSdk = 33
         }
      }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  @Ignore("New schema does not have NDO")
  fun stopCheckingWithUnknownNamedDomainObjects() {
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
    val file = addDeclarativeBuildFile("""
      androidApp {
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
    val file = addDeclarativeBuildFile("""
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
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkMultipleErrorAdvancedCase() {
    // DSL is different from AGP as we adopt to Declarative requirements.
    val file = addDeclarativeBuildFile("""
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
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkFactoryBlock() {
    val file = addDeclarativeBuildFile("""
    androidApp {
      buildTypes {
       buildType("new"){ }
      }
    }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkFactoryBlockNegative() {
    val file = addDeclarativeBuildFile("""
    androidApp {
      buildTypes {
        ${"buildTypes" highlightedAs HighlightSeverity.ERROR}("new"){ }
      }
    }
    """)
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkCorrectSettingsSyntax(){
    val file = fixture.addFileToProject("settings.gradle.dcl",
    """
      rootProject.name = "nowinandroid"

      enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
      include(":app")
      include(":app-nia-catalog")
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkCorrectDemoSyntax(){
    val file = fixture.addFileToProject("build.gradle.dcl",
                                        """
    androidApp {
       compileSdk = 33
    }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongPropertyType() {
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
    val file = addDeclarativeBuildFile("""
    ${"androidLibrary = 1" highlightedAs HighlightSeverity.ERROR}
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongFunctionType() {
    val file = addDeclarativeBuildFile("""
    androidLibrary {
        dependenciesDcl {
            ${"implementation = \"dependency\"" highlightedAs HighlightSeverity.ERROR}
         }
    }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkEnums() {
    val file = addDeclarativeBuildFile("""
     androidApp {
       compileOptions {
         sourceCompatibility = VERSION_15
         ${"sourceCompatibility = 123" highlightedAs HighlightSeverity.ERROR }
       }
    }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkFunctionChain() {
    val file = addDeclarativeSettingsFile("""
     plugins {
       id("some.plugin").version("1.0")
    }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun checkWrongTypeMessage() {
    val file = addDeclarativeBuildFile("""
      androidApp {
        dependenciesDcl {
           ${"api = \"org.example:example:1.0\"".highlightedAs(HighlightSeverity.ERROR, "Element type should be of type: Factory")}
        }
      }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }
  @Test
  fun checkRootProject() {
    val file = addDeclarativeSettingsFile("""
      rootProject.name = "some"
      ${ "rootProject.name = 1" highlightedAs HighlightSeverity.ERROR }
      rootProject.${"abc" highlightedAs HighlightSeverity.ERROR } = "some"
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  @Test
  fun layoutPositiveTest() {
    val file = addDeclarativeBuildFile("""
       androidApp {
        bundle {
          deviceTargetingConfig = layout.projectDirectory.file("myfile")
        }
      }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun layoutNegativeTest() {
    val file = addDeclarativeBuildFile("""
       androidApp {
        bundle {
          deviceTargetingConfig = ${"Layout" highlightedAs HighlightSeverity.ERROR }.${"projectDirectory" highlightedAs HighlightSeverity.ERROR }.${"file" highlightedAs HighlightSeverity.ERROR }("myfile")
          deviceTargetingConfig = layout.${"ProjectDirectory" highlightedAs HighlightSeverity.ERROR }.${"file" highlightedAs HighlightSeverity.ERROR }("myfile")
          deviceTargetingConfig = layout.projectDirectory.${"File" highlightedAs HighlightSeverity.ERROR }("myfile")
        }
      }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun checkMavenRepo() {
    val file = addDeclarativeSettingsFile("""
      pluginManagement {
        repositories {
            maven {
              url = uri("/Users/alexgolubev/src/agp-main/prebuilts/tools/common/m2/repository")
            }
        }
      }
    """.trimIndent())
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    fixture.checkHighlighting()
  }

  private fun addDeclarativeBuildFile(text: String) = fixture.addFileToProject("build.gradle.dcl", text.trimIndent())
  private fun addDeclarativeSettingsFile(text: String) = fixture.addFileToProject("settings.gradle.dcl", text.trimIndent())
}
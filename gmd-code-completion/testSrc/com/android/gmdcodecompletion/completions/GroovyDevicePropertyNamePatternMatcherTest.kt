/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.gmdcodecompletion.completions

import com.android.gmdcodecompletion.BuildFileName
import com.android.gmdcodecompletion.ConfigurationParameterName
import com.android.gmdcodecompletion.GmdConfigurationInterfaceInfo.FTL_DEVICE
import com.android.gmdcodecompletion.GmdConfigurationInterfaceInfo.FTL_EXECUTION
import com.android.gmdcodecompletion.GmdConfigurationInterfaceInfo.FTL_FIXTURE
import com.android.gmdcodecompletion.GmdConfigurationInterfaceInfo.FTL_RESULTS
import com.android.gmdcodecompletion.GmdConfigurationInterfaceInfo.MANAGED_VIRTUAL_DEVICE
import com.android.gmdcodecompletion.freshFtlDeviceCatalogState
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogState
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.testing.caret
import com.intellij.openapi.module.Module
import com.intellij.testFramework.TestApplicationManager
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GroovyDevicePropertyNamePatternMatcherTest : GmdCodeCompletionTestBase() {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockProjectBuildModel: ProjectBuildModel

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockPluginModel: PluginModel

  override fun setUp() {
    super.setUp()
    MockitoAnnotations.openMocks(this)
    TestApplicationManager.getInstance()
  }

  private val myFtlConfigurationNames = FTL_DEVICE.availableConfigurations.map { it.propertyName }.sorted()
  private val myManagedVirtualConfigurationNames = MANAGED_VIRTUAL_DEVICE.availableConfigurations.map { it.propertyName }.sorted()
  private val myTestOptionsFixtureConfigurationNames = FTL_FIXTURE.availableConfigurations.map { it.propertyName }.sorted()
  private val myTestOptionsExecutionConfigurationNames = FTL_EXECUTION.availableConfigurations.map { it.propertyName }.sorted()
  private val myTestOptionsResultsConfigurationNames = FTL_RESULTS.availableConfigurations.map { it.propertyName }.sorted()

  fun testGroovyManagedVirtualDevicePropertyName_unfoldedBlockWithLongDsl() {
    managedVirtualDevicePropertyNameCompletionTestHelper(myManagedVirtualConfigurationNames, """
      android {
        testOptions {
          managedDevices {
            devices {
              testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
                $caret
              }
            }
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyManagedVirtualDevicePropertyName_foldedBlockWithLongDsl() {
    managedVirtualDevicePropertyNameCompletionTestHelper(myManagedVirtualConfigurationNames, """
      android.testOptions {
        managedDevices.devices {
          testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
            $caret
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyManagedVirtualDevicePropertyName_foldedBlockWithSimplifiedDsl() {
    managedVirtualDevicePropertyNameCompletionTestHelper(myManagedVirtualConfigurationNames, """
      android.testOptions {
        managedDevices.localDevices {
          testDevice {
            $caret
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyManagedVirtualDevicePropertyName_noRepeatedApiLevel() {
    val filteredDeviceProperties = myManagedVirtualConfigurationNames.filterNot {
      it.contains("apiLevel") || it.contains("apiPreview")
    }
    managedVirtualDevicePropertyNameCompletionTestHelper(filteredDeviceProperties, """
      android.testOptions {
        managedDevices.devices {
          testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
            apiLevel =  ""
            $caret
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyManagedVirtualDevicePropertyName_noRepeatedApiPreview() {
    val filteredDeviceProperties = myManagedVirtualConfigurationNames.filterNot {
      it.contains("apiLevel") || it.contains("apiPreview")
    }
    managedVirtualDevicePropertyNameCompletionTestHelper(filteredDeviceProperties, """
      android.testOptions {
        managedDevices.devices {
          testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
            apiPreview =  ""
            $caret
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlDevicePropertyName_unfoldedBlockWithSimplifiedDsl() {
    ftlDevicePropertyNameCompletionTestHelper(myFtlConfigurationNames, """
      firebaseTestLab {
        managedDevices {
          myFtlDevice {
            $caret
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlDevicePropertyName_foldedBlockWithSimplifiedDsl() {
    ftlDevicePropertyNameCompletionTestHelper(myFtlConfigurationNames, """
      firebaseTestLab.managedDevices {
        myFtlDevice {
          $caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlTestOptionsFixture_foldedBlockWithSimplifiedDsl() {
    ftlDevicePropertyNameCompletionTestHelper(myTestOptionsFixtureConfigurationNames, """
      firebaseTestLab.testOptions {
        fixture {
          $caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlTestOptionsExecution_foldedBlockWithSimplifiedDsl() {
    ftlDevicePropertyNameCompletionTestHelper(myTestOptionsExecutionConfigurationNames, """
      firebaseTestLab.testOptions {
        execution {
          $caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlTestOptionsResults_foldedBlockWithSimplifiedDsl() {
    ftlDevicePropertyNameCompletionTestHelper(myTestOptionsResultsConfigurationNames, """
      firebaseTestLab.testOptions {
        results {
          $caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlTestOptionsFixture_extraDeviceFilesField() {
    ftlDevicePropertyNameCustomSuffixCompletionTestHelper("""
      firebaseTestLab.testOptions {
        fixture {
          extraDeviceFiles[] = 
        }
      }""".trimIndent(), """
      firebaseTestLab.testOptions {
        fixture {
          extraD$caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlTestOptionsResults_directoriesToPullField() {
    ftlDevicePropertyNameCustomSuffixCompletionTestHelper("""
      firebaseTestLab.testOptions {
        results {
          directoriesToPull.addAll()
        }
      }""".trimIndent(), """
      firebaseTestLab.testOptions {
        results {
          director$caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlDevicePropertyName_noRepeatedField() {
    ftlDevicePropertyNameCompletionTestHelper(myFtlConfigurationNames.subList(1, myFtlConfigurationNames.size), """
      firebaseTestLab.managedDevices {
        myFtlDevice {
          ${myFtlConfigurationNames[0]} = ""
          $caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlDevicePropertyName_unfoldedBlockWithLongDsl() {
    ftlDevicePropertyNameCompletionTestHelper(myFtlConfigurationNames, """
      android {
        testOptions {
          managedDevices {
            devices {
              testDevice(com.google.firebase.testlab.gradle.ManagedDevice) {
                $caret
              }
            }
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlDevicePropertyName_foldedBlockWithLongDsl() {
    ftlDevicePropertyNameCompletionTestHelper(myFtlConfigurationNames, """
      android.testOptions {
        managedDevices.devices {
          testDevice(com.google.firebase.testlab.gradle.ManagedDevice) {
            $caret
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyNotMatch() {
    gmdCodeCompletionContributorTestHelper(BuildFileName.GROOVY_BUILD_FILE.fileName, """
      android {
        $caret
      }
    """.trimIndent()) {
      assertEmptyDevicePropertyNameSuggestion()
    }
  }

  fun testNotGradleBuildFile() {
    gmdCodeCompletionContributorTestHelper(BuildFileName.OTHER_FILE.fileName, """
      android.testOptions {
        managedDevices.devices {
          testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
            apiPreview =  ""
            $caret
          }
        }
      }
    """.trimIndent()) {
      assertEmptyDevicePropertyNameSuggestion()
    }
  }

  private fun assertEmptyDevicePropertyNameSuggestion() {
    val suggestionList = myFixture.lookupElementStrings!!
    val intersectedList = suggestionList.intersect(ConfigurationParameterName.values().map { it.propertyName }.toSet())
    assertTrue(intersectedList.isEmpty())
  }

  private fun ftlDevicePropertyNameCompletionTestSetup(deviceCatalogState: FtlDeviceCatalogState, callBack: () -> Unit) {
    createFakeFtlDeviceCatalogService().apply {
      whenever(this.state).thenReturn(deviceCatalogState)
    }
    createFakeGradleModelProvider().apply {
      whenever(this.getProjectModel(any())).thenReturn(mockProjectBuildModel)
    }
    val mockGradlePropertyModel = mock<GradlePropertyModel>().apply {
      whenever(this.name).thenReturn("android.experimental.testOptions.managedDevices.customDevice")
      whenever(this.valueAsString()).thenReturn("true")
    }
    whenever(mockProjectBuildModel.getModuleBuildModel(any<Module>())!!.plugins()).thenReturn(listOf(mockPluginModel))
    whenever(mockProjectBuildModel.projectBuildModel!!.propertiesModel!!.declaredProperties).thenReturn(listOf(mockGradlePropertyModel))
    whenever(mockProjectBuildModel.projectBuildModel!!.plugins()).thenReturn(listOf(mockPluginModel))
    whenever(mockPluginModel.psiElement!!.text).thenReturn("com.google.firebase.testlab")
    callBack()
  }

  private fun ftlDevicePropertyNameCompletionTestHelper(
    expectedProperties: List<String>, buildFileContent: String,
    deviceCatalogState: FtlDeviceCatalogState = freshFtlDeviceCatalogState()) {
    ftlDevicePropertyNameCompletionTestSetup(deviceCatalogState) {
      gmdCodeCompletionContributorTestHelper(BuildFileName.GROOVY_BUILD_FILE.fileName, buildFileContent) {
        val prioritizedLookupElements = myFixture.lookupElementStrings!!.subList(0, expectedProperties.size)
        assertTrue(prioritizedLookupElements == expectedProperties)
      }
    }
  }

  private fun ftlDevicePropertyNameCustomSuffixCompletionTestHelper(
    expectedBuildFileContent: String, buildFileContent: String,
    deviceCatalogState: FtlDeviceCatalogState = freshFtlDeviceCatalogState()) {
    ftlDevicePropertyNameCompletionTestSetup(deviceCatalogState) {
      gmdCodeCompletionContributorTestHelper(BuildFileName.GROOVY_BUILD_FILE.fileName, buildFileContent) {
        assertTrue(myFixture.editor.document.text == expectedBuildFileContent)
      }
    }
  }
}
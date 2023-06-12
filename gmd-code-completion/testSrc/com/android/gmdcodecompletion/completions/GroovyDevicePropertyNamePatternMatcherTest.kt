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
import com.android.gmdcodecompletion.DevicePropertyName
import com.android.gmdcodecompletion.freshFtlDeviceCatalogState
import com.android.gmdcodecompletion.ftl.FtlDeviceCatalogState
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.caret
import com.intellij.testFramework.TestApplicationManager
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.MockitoAnnotations

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

  private val ftlDevicePropertyNames = DevicePropertyName.FTL_DEVICE_PROPERTY.map { "${it.propertyName} = " }.sorted()
  private val managedVirtualDevicePropertyNames = DevicePropertyName.MANAGED_VIRTUAL_DEVICE_PROPERTY.map { "${it.propertyName} = " }.sorted()

  fun testGroovyManagedVirtualDevicePropertyName_unfoldedBlockWithLongDsl() {
    managedVirtualDevicePropertyNameCompletionTestHelper(managedVirtualDevicePropertyNames, """
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
    managedVirtualDevicePropertyNameCompletionTestHelper(managedVirtualDevicePropertyNames, """
      android.testOptions {
        managedDevices.devices {
          testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
            $caret
          }
        }
      }
    """.trimIndent())
  }

  fun testGroovyManagedVirtualDevicePropertyName_noRepeatedApiLevel() {
    val filteredDeviceProperties = managedVirtualDevicePropertyNames.filterNot {
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
    val filteredDeviceProperties = managedVirtualDevicePropertyNames.filterNot {
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
    ftlDevicePropertyNameCompletionTestHelper(ftlDevicePropertyNames, """
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
    ftlDevicePropertyNameCompletionTestHelper(ftlDevicePropertyNames, """
      firebaseTestLab.managedDevices {
        myFtlDevice {
          $caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlDevicePropertyName_noRepeatedField() {
    ftlDevicePropertyNameCompletionTestHelper(ftlDevicePropertyNames.subList(1, ftlDevicePropertyNames.size), """
      firebaseTestLab.managedDevices {
        myFtlDevice {
          ${ftlDevicePropertyNames[0]} ""
          $caret
        }
      }
    """.trimIndent())
  }

  fun testGroovyFtlDevicePropertyName_unfoldedBlockWithLongDsl() {
    ftlDevicePropertyNameCompletionTestHelper(ftlDevicePropertyNames, """
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
    ftlDevicePropertyNameCompletionTestHelper(ftlDevicePropertyNames, """
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
    val intersectedList = suggestionList.intersect(DevicePropertyName.values().map { it.propertyName }.toSet())
    assertTrue(intersectedList.isEmpty())
  }

  protected fun ftlDevicePropertyNameCompletionTestHelper(
    expectedProperties: List<String>, buildFileContent: String,
    deviceCatalogState: FtlDeviceCatalogState = freshFtlDeviceCatalogState()) {
    val mockFtlService = createFakeFtlDeviceCatalogService()
    whenever(mockFtlService.state).thenReturn(deviceCatalogState)
    val mockGradleModelProvider = createFakeGradleModelProvider()
    whenever(mockGradleModelProvider.getProjectModel(any())).thenReturn(mockProjectBuildModel)
    whenever(mockProjectBuildModel.projectBuildModel!!.plugins()).thenReturn(listOf(mockPluginModel))
    whenever(mockPluginModel.psiElement!!.text).thenReturn("com.google.firebase.testlab")

    gmdCodeCompletionContributorTestHelper(BuildFileName.GROOVY_BUILD_FILE.fileName, buildFileContent) {
      val prioritizedLookupElements = myFixture.lookupElementStrings!!.subList(0, expectedProperties.size)
      assertTrue(prioritizedLookupElements == expectedProperties)
    }
  }
}
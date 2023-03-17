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

import com.android.gmdcodecompletion.AndroidDeviceInfo
import com.android.gmdcodecompletion.managedvirtual.ManagedVirtualDeviceCatalogState
import com.android.gmdcodecompletion.testMinAndTargetApiLevel
import com.android.tools.idea.testing.caret
import java.util.Calendar

/**
 * Currently missing pattern matching test for Kotlin Gradle build file since
 * Kotlin side implementation relies on PSI element resolve() method. It is more
 * meaningful to do a full integration test to test this part.
 */
class GmdDevicePropertyValuePatternMatcherTest : GmdCodeCompletionTestBase() {

  fun testDevicePropertyPatternMatching_singleField() {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, 1)
    val testManagedVirtualDeviceCatalogState = ManagedVirtualDeviceCatalogState(expireDate = calendar.time).apply {
      this.myDeviceCatalog.devices.putAll(mapOf(
        "testDevice1" to AndroidDeviceInfo(supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)),
        "testDevice2" to AndroidDeviceInfo(supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)),
      ))
      this.myDeviceCatalog.checkEmptyFields()
    }
    managedVirtualDevicePropertyNameCompletionTestHelper(listOf("testDevice1", "testDevice2"), """
      android {
        testOptions {
          managedDevices {
            devices {
              testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
                device = $caret
              }
            }
          }
        }
      }
    """.trimIndent(), testManagedVirtualDeviceCatalogState)
  }

  fun testDevicePropertyPatternMatching_withOtherFields() {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, 1)
    val testApiLevel = testMinAndTargetApiLevel.targetSdk - 1
    val testManagedVirtualDeviceCatalogState = ManagedVirtualDeviceCatalogState(expireDate = calendar.time).apply {
      this.myDeviceCatalog.devices.putAll(mapOf(
        "testDevice1" to AndroidDeviceInfo(supportedApis = listOf(testApiLevel)),
        "testDevice2" to AndroidDeviceInfo(supportedApis = listOf(testMinAndTargetApiLevel.targetSdk)),
      ))
      this.myDeviceCatalog.checkEmptyFields()
    }
    managedVirtualDevicePropertyNameCompletionTestHelper(listOf("testDevice1"), """
      android {
        testOptions {
          managedDevices {
            devices {
              testDevice(com.android.build.api.dsl.ManagedVirtualDevice) {
                device = $caret
                apiLevel = $testApiLevel
              }
            }
          }
        }
      }
    """.trimIndent(), testManagedVirtualDeviceCatalogState)
  }
}
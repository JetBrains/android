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
package com.android.tools.idea

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidStudioResourceUrlsTest {

  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun setUp() {
    // These tests only apply to Android Studio, not to the Android plugin running in IntelliJ.
    assumeThat(IdeInfo.getInstance().isAndroidStudio).isTrue()
  }

  @Test
  fun androidResourceUrlsGetsRegisteredCorrectly() {
    val androidStudioResourceUrls = ExternalProductResourceUrls.getInstance()
    assertTrue(androidStudioResourceUrls is AndroidStudioResourceUrls)
  }

  @Test
  fun defaultUpdateUrl() {
    val androidStudioResourceUrls = ExternalProductResourceUrls.getInstance()

    // When accessing the update url
    val updateUrl = androidStudioResourceUrls.updateMetadataUrl!!

    // Then the update url is returned
    assertEquals(
      AndroidStudioResourceUrls.UPDATE_URL,
      updateUrl.toString()
    )
  }

  @Test
  fun defaultPatchUrl() {
    testPatchUrl(AndroidStudioResourceUrls.PATCH_URL)
  }

  // Tests for the patch url
  // Expected on a linux local host: http://localhost:42213/AI-213.7172.25.2113.31337-213.7172.25.2113.8473230-patch-unix.jar
  // Default expected on linux: https://dl.google.com/android/studio/patches/AI-213.7172.25.2113.31337-213.7172.25.2113.8473230-patch-unix.jar
  private fun testPatchUrl(urlToFindPatch: String) {
    // Given
    val productCode = ApplicationInfo.getInstance().build.productCode
    val fromNumber = "213.7172.25.2113.31337"
    val toNumber = "213.7172.25.2113.8473230"
    val from = BuildNumber.fromString("AI-${fromNumber}")!!
    val to = BuildNumber.fromString("AI-${toNumber}")!!

    // When
    val patchUrl = ExternalProductResourceUrls.getInstance().computePatchUrl(from, to)!!.toString()

    // Then
    val baseExpectedPath = "$urlToFindPatch$productCode-$fromNumber-$toNumber-patch-"
    // logic for different OS support and the different naming scheme for mac_arm
    assertTrue(patchUrl.startsWith(baseExpectedPath))
    assertTrue(patchUrl.endsWith(".jar"))
  }

  @Test
  fun mac_arm() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = true, isWindows = false, isUnix = false, isAarch = true)
    assertEquals("mac_arm.jar", suffix)
  }

  @Test
  fun mac() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = true, isWindows = false, isUnix = false, isAarch = false)
    assertEquals("mac.jar", suffix)
  }

  @Test
  fun windows() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = false, isWindows = true, isUnix = false, isAarch = false)
    assertEquals("win.jar", suffix)
  }

  @Test
  fun unix() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = false, isWindows = false, isUnix = true, isAarch = false)
    assertEquals("unix.jar", suffix)
  }

  @Test
  fun unknownOsAndArchitectureThrowsException() {
    val resourceUrls = AndroidStudioResourceUrls()
    assertThrows(IllegalStateException::class.java,
                 { resourceUrls.getPatchSuffix(isMac = false, isWindows = false, isUnix = false, isAarch = false) })
  }
}

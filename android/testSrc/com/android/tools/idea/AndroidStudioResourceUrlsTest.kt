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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.testFramework.ApplicationRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URLDecoder

class AndroidStudioResourceUrlsTest {

  @get:Rule
  val appRule = ApplicationRule()

  @Before
  fun setUp() {
    // These tests only apply to Android Studio, not to the Android plugin running in IntelliJ.
    assumeTrue(IdeInfo.getInstance().isAndroidStudio)
  }

  @Test
  fun androidResourceUrlsIsRegistered() {
    val resourceUrls = ExternalProductResourceUrls.getInstance()
    assertThat(resourceUrls).isInstanceOf(AndroidStudioResourceUrls::class.java)
  }

  @Test
  @Suppress("OverrideOnly")
  fun miscUrls() {
    val urls = ExternalProductResourceUrls.getInstance()
    assertThat(urls.downloadPageUrl.toString()).isEqualTo("https://developer.android.com/r/studio-ui/download-stable")
    assertThat(urls.youTubeChannelUrl.toString()).isEqualTo("https://www.youtube.com/c/AndroidDevelopers")
    assertThat(urls.whatIsNewPageUrl.toString()).isEqualTo("https://developer.android.com/r/studio-ui/menu-whats-new.html")
    assertThat(urls.gettingStartedPageUrl.toString()).isEqualTo("http://developer.android.com/r/studio-ui/menu-start.html")
    assertThat(urls.helpPageUrl!!("topic").toString()).contains("jetbrains.com")
    assertThat(urls.keyboardShortcutsPdfUrl.toString()).contains("jetbrains.com")
  }

  @Test
  @Suppress("OverrideOnly")
  fun updateMetadataUrl() {
    val resourceUrls = ExternalProductResourceUrls.getInstance()
    val updateMetadataUrl = resourceUrls.updateMetadataUrl.toString()
    assertThat(updateMetadataUrl).startsWith("https://dl.google.com/android/studio/patches/updates.xml")

    val params = updateMetadataUrl.substringAfter('?').split("&").associate {
      Pair(it.substringBefore('='), URLDecoder.decode(it.substringAfter('='), "UTF-8"))
    }
    assertThat(params.keys).containsAllOf("build", "uid", "os")
    assertThat(params["build"]).isEqualTo(ApplicationInfo.getInstance().build.asString())
  }

  // Tests for the patch url
  // Expected on a linux local host: http://localhost:42213/AI-111.2.3-444.5.6-patch-unix.jar
  // Default expected on linux: https://dl.google.com/android/studio/patches/AI-111.2.3-444.5.6-patch-unix.jar
  @Test
  @Suppress("OverrideOnly")
  fun updatePatchUrl() {
    val from = BuildNumber.fromString("AI-111.2.3")!!
    val to = BuildNumber.fromString("AI-444.5.6")!!
    val patchUrl = ExternalProductResourceUrls.getInstance().computePatchUrl(from, to).toString()

    // logic for different OS support and the different naming scheme for mac_arm
    assertThat(patchUrl).startsWith("https://dl.google.com/android/studio/patches/AI-111.2.3-444.5.6-patch-")
    assertThat(patchUrl).endsWith(".jar")
  }

  @Test
  fun mac_arm() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = true, isWindows = false, isUnix = false, isAarch64 = true)
    assertEquals("mac_arm.jar", suffix)
  }

  @Test
  fun mac() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = true, isWindows = false, isUnix = false, isAarch64 = false)
    assertEquals("mac.jar", suffix)
  }

  @Test
  fun windows() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = false, isWindows = true, isUnix = false, isAarch64 = false)
    assertEquals("win.jar", suffix)
  }

  @Test
  fun unix() {
    val resourceUrls = AndroidStudioResourceUrls()
    val suffix = resourceUrls.getPatchSuffix(isMac = false, isWindows = false, isUnix = true, isAarch64 = false)
    assertEquals("unix.jar", suffix)
  }

  @Test
  fun unknownOsAndArchitectureThrowsException() {
    val resourceUrls = AndroidStudioResourceUrls()
    assertThrows(IllegalStateException::class.java) {
      resourceUrls.getPatchSuffix(isMac = false, isWindows = false, isUnix = false, isAarch64 = false)
    }
  }
}

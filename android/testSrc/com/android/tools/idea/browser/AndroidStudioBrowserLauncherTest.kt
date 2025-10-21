/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.browser

import org.junit.Test
import com.google.common.truth.Truth.assertThat
import com.android.tools.idea.browser.AndroidStudioBrowserLauncher.Companion.addUtmParameters

class AndroidStudioBrowserLauncherTest {
  @Test
  fun addUrlParams_noParams_addsBothUtmParams() {
    val input = "https://developer.android.com/studio/releases"
    val expected = "https://developer.android.com/studio/releases?utm_source=android-studio-app&utm_medium=app"
    assertThat(addUtmParameters(input)).isEqualTo(expected)
  }

  @Test
  fun addUrlParams_utmSourceExists_noChange() {
    val input = "https://developer.android.com/jetpack?utm_source=existing-source"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_utmMediumExists_noChange() {
    val input = "https://developer.android.com/jetpack?utm_medium=existing-medium"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_utmContentExists_noChange() {
    val input = "https://developer.android.com/jetpack?utm_content=existing-content"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_allUtmExist_noChange() {
    val input = "https://developer.android.com/reference/kotlin/java/net/URL?utm_source=another&utm_medium=web&utm_content=banner"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_anyUtmExistsWithOtherParams_noChange() {
    val input = "https://developer.android.com/guide/topics?foo=bar&utm_source=existing"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_googleCom_noChange() {
    val input = "https://www.google.com"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_otherHost_noChange() {
    val input = "https://example.com/developer.android.com"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_notAUrl_returnsOriginal() {
    val input = "not-a-valid-url"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_missingScheme_returnsOriginal() {
    val input = "developer.android.com/studio"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_emptyString_returnsEmpty() {
    val input = ""
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_withFragment_paramsAddedBeforeFragment() {
    val input = "https://developer.android.com/guide/topics/manifest/manifest-intro#core-components"
    val expected = "https://developer.android.com/guide/topics/manifest/manifest-intro?utm_source=android-studio-app&utm_medium=app#core-components"
    assertThat(addUtmParameters(input)).isEqualTo(expected)
  }

  @Test
  fun addUrlParams_withExistingNonUtmParamAndFragment_paramsAddedCorrectly() {
    val input = "https://developer.android.com/guide/topics?foo=bar#section"
    val expected = "https://developer.android.com/guide/topics?foo=bar&utm_source=android-studio-app&utm_medium=app#section"
    assertThat(addUtmParameters(input)).isEqualTo(expected)
  }

  @Test
  fun addUrlParams_withHttp_addsBothUtmParams() {
    val input = "http://developer.android.com/studio/releases"
    val expected = "http://developer.android.com/studio/releases?utm_source=android-studio-app&utm_medium=app"
    assertThat(addUtmParameters(input)).isEqualTo(expected)
  }

  // New tests for scheme validation
  @Test
  fun addUrlParams_ftpScheme_noChange() {
    val input = "ftp://developer.android.com/studio/releases"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_fileScheme_noChange() {
    val input = "file:///home/user/file.html"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }

  @Test
  fun addUrlParams_mailtoScheme_noChange() {
    val input = "mailto:user@example.com"
    assertThat(addUtmParameters(input)).isEqualTo(input)
  }
}
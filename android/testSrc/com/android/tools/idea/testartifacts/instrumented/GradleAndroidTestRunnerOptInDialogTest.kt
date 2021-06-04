/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness

/**
 * Unit tests for GradleAndroidTestRunnerOptInDialog.kt.
 */
@RunWith(JUnit4::class)
class GradleAndroidTestRunnerOptInDialogTest {

  @get:Rule val mockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock private lateinit var mockProject: Project

  private val config: AndroidTestConfiguration = AndroidTestConfiguration()
  private var isDisplayed: Boolean = false

  private fun showDialog(acceptSuggestion: Boolean = true) {
    showGradleAndroidTestRunnerOptInDialog(mockProject, config) {
      isDisplayed = true
      acceptSuggestion
    }
  }

  @Test
  fun dialogIsDisplayedWithDefaultConfig() {
    showDialog()

    assertThat(isDisplayed).isTrue()
  }

  @Test
  fun dialogIsNotDisplayedWhenAUserHasOptedInAlready() {
    config.RUN_ANDROID_TEST_USING_GRADLE = true

    showDialog()

    assertThat(isDisplayed).isFalse()
  }

  @Test
  fun dialogIsNotDisplayedWhenItHasDisplayedBefore() {
    config.SHOW_RUN_ANDROID_TEST_USING_GRADLE_OPT_IN_DIALOG = false

    showDialog()

    assertThat(isDisplayed).isFalse()
  }

  @Test
  fun userAcceptSuggestion() {
    showDialog(acceptSuggestion = true)

    assertThat(config.RUN_ANDROID_TEST_USING_GRADLE).isTrue()
    assertThat(config.SHOW_RUN_ANDROID_TEST_USING_GRADLE_OPT_IN_DIALOG).isFalse()
  }

  @Test
  fun userDeclinedSuggestion() {
    showDialog(acceptSuggestion = false)

    assertThat(config.RUN_ANDROID_TEST_USING_GRADLE).isFalse()
    // We won't show the dialog again even if a user declines our suggestion.
    assertThat(config.SHOW_RUN_ANDROID_TEST_USING_GRADLE_OPT_IN_DIALOG).isFalse()
  }
}
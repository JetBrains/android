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
package com.android.tools.idea.logcat.settings

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtilRt.LARGE_FOR_CONTENT_LOADING
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify


/**
 * Tests for [LogcatApplicationSettingsConfigurable]
 */
class LogcatApplicationSettingsConfigurableTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  private val logcatSettings = LogcatSettings()

  @Test
  fun createComponent() {
    logcatSettings.bufferSize = 25 * 1024

    val configurable = LogcatApplicationSettingsConfigurable(logcatSettings).apply { createComponent() }

    assertThat(configurable.cycleBufferSizeTextField.text).isEqualTo("25")
    assertThat(configurable.cyclicBufferSizeWarningLabel.text).isEqualTo("")
  }

  @Test
  fun bufferSize_invalid() {
    val configurable = LogcatApplicationSettingsConfigurable(logcatSettings).apply { createComponent() }

    listOf("not-a-number", "-1", "0", "102401").forEach {
      configurable.cycleBufferSizeTextField.text = it

      assertThat(configurable.isModified).named(it).isFalse()
      assertThat(configurable.cyclicBufferSizeWarningLabel.text).named(it).isEqualTo(
        "Invalid. Please enter an integer between 1 and 102400 (1KB-100MB)")
    }
  }

  @Test
  fun bufferSize_valid() {
    val configurable = LogcatApplicationSettingsConfigurable(logcatSettings).apply { createComponent() }

    listOf("1", "100", "1000").forEach {
      configurable.cycleBufferSizeTextField.text = it

      assertThat(configurable.isModified).named(it).isTrue()
      assertThat(configurable.cyclicBufferSizeWarningLabel.text).named(it).isEmpty()
    }
  }

  @Test
  fun bufferSize_large() {
    val configurable = LogcatApplicationSettingsConfigurable(logcatSettings).apply { createComponent() }

    configurable.cycleBufferSizeTextField.text = (LARGE_FOR_CONTENT_LOADING / 1024 + 1).toString()

    assertThat(configurable.isModified).isTrue()
    assertThat(configurable.cyclicBufferSizeWarningLabel.text).isEqualTo("Warning: large buffer size can cause performance degradation")
  }

  @Test
  fun bufferSize_unchanged() {
    logcatSettings.bufferSize = 100 * 1024
    val configurable = LogcatApplicationSettingsConfigurable(logcatSettings).apply { createComponent() }

    configurable.cycleBufferSizeTextField.text = "100"

    assertThat(configurable.isModified).isFalse()
    assertThat(configurable.cyclicBufferSizeWarningLabel.text).isEmpty()
  }

  @Test
  fun apply() {
    val configurable = LogcatApplicationSettingsConfigurable(logcatSettings).apply { createComponent() }
    val mockLogcatPresenter = mock<LogcatPresenter>()
    LogcatToolWindowFactory.logcatPresenters.add(mockLogcatPresenter)

    configurable.apply()

    verify(mockLogcatPresenter).applyLogcatSettings(logcatSettings)
    LogcatToolWindowFactory.logcatPresenters.remove(mockLogcatPresenter)
  }
}

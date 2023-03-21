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
import com.android.tools.idea.logcat.FakeLogcatPresenter
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtilRt.LARGE_FOR_CONTENT_LOADING
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify


/**
 * Tests for [LogcatApplicationSettingsConfigurable]
 */
class LogcatApplicationSettingsConfigurableTest {
  @get:Rule
  val rule = RuleChain(ApplicationRule(), EdtRule())

  private val logcatSettings = AndroidLogcatSettings()

  @After
  fun tearDown() {
    LogcatToolWindowFactory.logcatPresenters.clear()
  }

  @Test
  fun init_doesNotRequireEdt() {
    // This will throw if constructor requires EDT
    LogcatApplicationSettingsConfigurable(logcatSettings)
  }

  @Test
  fun createComponent() {
    logcatSettings.bufferSize = 25 * 1024

    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    assertThat(configurable.cycleBufferSizeTextField.text).isEqualTo("25")
    assertThat(configurable.cyclicBufferSizeWarningLabel.text).isEqualTo("")
  }

  @Test
  fun bufferSize_invalid() {
    val configurable = logcatApplicationSettingsConfigurable()

    listOf("not-a-number", "-1", "0", "102401").forEach {
      configurable.cycleBufferSizeTextField.text = it

      assertThat(configurable.isModified).named(it).isFalse()
      assertThat(configurable.cyclicBufferSizeWarningLabel.text).named(it).isEqualTo(
        "Invalid. Please enter an integer between 1 and 102400 (1KB-100MB)")
    }
  }

  @Test
  fun bufferSize_valid() {
    val configurable = logcatApplicationSettingsConfigurable()

    listOf("1", "100", "1000").forEach {
      configurable.cycleBufferSizeTextField.text = it

      assertThat(configurable.isModified).named(it).isTrue()
      assertThat(configurable.cyclicBufferSizeWarningLabel.text).named(it).isEmpty()
    }
  }

  @Test
  fun bufferSize_large() {
    val configurable = logcatApplicationSettingsConfigurable()

    configurable.cycleBufferSizeTextField.text = (LARGE_FOR_CONTENT_LOADING / 1024 + 1).toString()

    assertThat(configurable.isModified).isTrue()
    assertThat(configurable.cyclicBufferSizeWarningLabel.text).isEqualTo("Warning: large buffer size can cause performance degradation")
  }

  @Test
  fun bufferSize_unchanged() {
    logcatSettings.bufferSize = 100 * 1024
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    configurable.cycleBufferSizeTextField.text = "100"

    assertThat(configurable.isModified).isFalse()
    assertThat(configurable.cyclicBufferSizeWarningLabel.text).isEmpty()
  }

  @Test
  fun ignoredTags_initialValue() {
    logcatSettings.ignoredTags = setOf("foo", "bar")
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    assertThat(configurable.ignoreTagsTextField.component.text).isEqualTo("foo bar")
  }

  @Test
  fun ignoredTagsNote_noPanels() {
    LogcatToolWindowFactory.logcatPresenters.clear()
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    assertThat(configurable.ignoreTagsNote.isVisible).isTrue()
  }

  @Test
  fun ignoredTagsNote_withPanel() {
    LogcatToolWindowFactory.logcatPresenters.add(FakeLogcatPresenter().apply { tagSet.add("foo") })
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    assertThat(configurable.ignoreTagsNote.isVisible).isFalse()
  }

  @Test
  fun apply_appliesToLogcatPresenters() {
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)
    val mockLogcatPresenter = mock<LogcatPresenter>()
    LogcatToolWindowFactory.logcatPresenters.add(mockLogcatPresenter)

    configurable.apply()

    verify(mockLogcatPresenter).applyLogcatSettings(logcatSettings)
    LogcatToolWindowFactory.logcatPresenters.remove(mockLogcatPresenter)
  }

  @Test
  @RunsInEdt
  fun apply() {
    val logcatSettings = AndroidLogcatSettings(
      bufferSize = 100 * 1024,
      defaultFilter = "foo",
      mostRecentlyUsedFilterIsDefault = false,
      ignoredTags = emptySet())
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)
    configurable.cycleBufferSizeTextField.text = "200"
    configurable.defaultFilterTextField.text = "bar"
    configurable.mostRecentlyUsedFilterIsDefaultCheckbox.isSelected = true
    configurable.ignoreTagsTextField.component.text = " foo  bar "

    configurable.apply()

    assertThat(logcatSettings).isEqualTo(AndroidLogcatSettings(
      bufferSize = 200 * 1024,
      defaultFilter = "bar",
      mostRecentlyUsedFilterIsDefault = true,
      ignoredTags = setOf("foo", "bar")))
  }

  @Test
  fun isModified_bufferSize() {
    logcatSettings.bufferSize = 100 * 1024
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)
    assertThat(configurable.isModified).isFalse()

    configurable.cycleBufferSizeTextField.text = "200"

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  @RunsInEdt
  fun isModified_defaultFilter() {
    logcatSettings.defaultFilter = "foo"
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)
    assertThat(configurable.isModified).isFalse()

    configurable.defaultFilterTextField.text = "bar"

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun isModified_mostRecentlyUsedFilterIsDefault() {
    logcatSettings.mostRecentlyUsedFilterIsDefault = false
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)
    assertThat(configurable.isModified).isFalse()

    configurable.mostRecentlyUsedFilterIsDefaultCheckbox.isSelected = true

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun isModified_ignoredTags() {
    logcatSettings.ignoredTags = setOf("foo", "bar")
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)
    assertThat(configurable.isModified).isFalse()

    runInEdtAndWait {
      configurable.ignoreTagsTextField.component.text = "foobar"
    }

    assertThat(configurable.isModified).isTrue()
  }

  @Test
  fun mostRecentlyUsedFilterIsDefaultIsTrue_defaultFilterTextFieldIsDisabled() {
    logcatSettings.mostRecentlyUsedFilterIsDefault = true

    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    assertThat(configurable.defaultFilterTextField.isEnabled).isFalse()
  }

  @Test
  fun mostRecentlyUsedFilterIsDefaultIsFalse_defaultFilterTextFieldIsEnabled() {
    logcatSettings.mostRecentlyUsedFilterIsDefault = false

    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    assertThat(configurable.defaultFilterTextField.isEnabled).isTrue()
  }

  @Test
  fun mostRecentlyUsedFilterIsDefaultIsToggle_defaultFilterTextFieldIsEnabledToggled() {
    logcatSettings.mostRecentlyUsedFilterIsDefault = false
    val configurable = logcatApplicationSettingsConfigurable(logcatSettings)

    configurable.mostRecentlyUsedFilterIsDefaultCheckbox.doClick()
    assertThat(configurable.defaultFilterTextField.isEnabled).isFalse()

    configurable.mostRecentlyUsedFilterIsDefaultCheckbox.doClick()
    assertThat(configurable.defaultFilterTextField.isEnabled).isTrue()
  }

  private fun logcatApplicationSettingsConfigurable(logcatSettings: AndroidLogcatSettings = AndroidLogcatSettings())
    : LogcatApplicationSettingsConfigurable =
    LogcatApplicationSettingsConfigurable(logcatSettings).apply {
      runInEdtAndWait {
        createComponent()
      }
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding.config

import com.android.tools.idea.databinding.DataBindingCodeGenService
import com.android.tools.idea.databinding.config.DataBindingConfiguration.CodeGenMode
import com.google.common.truth.Truth.assertThat
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DataBindingConfigurableTest {
  private lateinit var testDisposable: Disposable

  @Before
  fun setUp() {
    testDisposable = Disposable { }
    val mockApplication = MockApplication(testDisposable)
    ApplicationManager.setApplication(mockApplication, testDisposable)

    mockApplication.registerService(DataBindingConfiguration::class.java)
    mockApplication.registerService(DataBindingCodeGenService::class.java)
  }

  @After
  fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  @Test
  fun codeGenModeRadioButtons() {
    assertThat(DataBindingConfiguration.getInstance().CODE_GEN_MODE).isEqualTo(CodeGenMode.IN_MEMORY)

    // Assert expected state of a new configurable
    val configurable = DataBindingConfigurable()
    assertThat(configurable.isModified).isFalse()
    assertThat(configurable.generatedCodeRadioButton.isSelected).isFalse()
    assertThat(configurable.liveCodeRadioButton.isSelected).isTrue()

    // Switch to "generate code to disk" setting and apply it
    configurable.generatedCodeRadioButton.isSelected = true
    assertThat(configurable.liveCodeRadioButton.isSelected).isFalse()
    assertThat(configurable.isModified).isTrue()
    assertThat(DataBindingConfiguration.getInstance().CODE_GEN_MODE).isEqualTo(CodeGenMode.IN_MEMORY)
    configurable.apply()
    assertThat(DataBindingConfiguration.getInstance().CODE_GEN_MODE).isEqualTo(CodeGenMode.ON_DISK)
    assertThat(configurable.isModified).isFalse()

    // Switch back to "generate code in memory" setting and apply it
    configurable.liveCodeRadioButton.isSelected = true
    assertThat(configurable.generatedCodeRadioButton.isSelected).isFalse()
    configurable.apply()
    assertThat(DataBindingConfiguration.getInstance().CODE_GEN_MODE).isEqualTo(CodeGenMode.IN_MEMORY)
    assertThat(configurable.isModified).isFalse()

    // Make sure that "reset" can cancel any editing in progress
    // Make sure we cancel when in "on disk" mode, as it adds a bit more code coverage
    configurable.generatedCodeRadioButton.isSelected = true
    configurable.apply()
    configurable.liveCodeRadioButton.isSelected = true
    configurable.reset()
    assertThat(configurable.generatedCodeRadioButton.isSelected).isTrue()
    assertThat(configurable.liveCodeRadioButton.isSelected).isFalse()
    assertThat(configurable.isModified).isFalse()
  }
}
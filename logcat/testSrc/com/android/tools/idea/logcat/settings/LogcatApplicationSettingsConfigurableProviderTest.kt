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

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [LogcatApplicationSettingsConfigurableProvider]
 */
@RunsInEdt
class LogcatApplicationSettingsConfigurableProviderTest {
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(ApplicationRule(), disposableRule, EdtRule())

  private val provider = LogcatApplicationSettingsConfigurableProvider()

  @Test
  fun createConfigurable() {
    assertThat(provider.createConfigurable()).isInstanceOf(LogcatApplicationSettingsConfigurable::class.java)
  }

  @Test
  fun canCreateConfigurable() {
    assertThat(provider.canCreateConfigurable()).isTrue()
  }
}
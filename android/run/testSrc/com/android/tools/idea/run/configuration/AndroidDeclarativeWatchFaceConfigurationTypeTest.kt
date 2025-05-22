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
package com.android.tools.idea.run.configuration

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.flags.overrideForTest
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test

class AndroidDeclarativeWatchFaceConfigurationTypeTest {
  private val flagRule = FlagRule(StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_RUN_CONFIGURATION, true)
  private val appRule = ApplicationRule()
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(flagRule, appRule, disposableRule)

  @Test
  fun `the declarative watch face run configuration is available when the flag is enabled`() {
    assertThat(ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.map { it.id })
      .contains(AndroidDeclarativeWatchFaceConfigurationType.ID)
  }

  @Test(expected = ExtensionNotApplicableException::class)
  fun `AndroidDeclarativeWatchFaceConfigurationType throws an ExtensionNotApplicableException when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_RUN_CONFIGURATION.overrideForTest(false, disposableRule.disposable)

    AndroidDeclarativeWatchFaceConfigurationType()
  }
}

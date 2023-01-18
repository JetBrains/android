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
package com.android.tools.idea.layoutinspector.settings

import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test

class LayoutInspectorSettingsTest {

  @get:Rule
  val applicationRule = ApplicationRule()

  @Test
  fun testAutoConnectEnabledFlag() {
    val layoutInspectorSettings = LayoutInspectorSettings.getInstance()
    assertThat(layoutInspectorSettings.autoConnectEnabled).isTrue()

    layoutInspectorSettings.setAutoConnectEnabledInSettings(false)
    assertThat(layoutInspectorSettings.autoConnectEnabled).isFalse()

    runWithFlagState(false) {
      assertThat(layoutInspectorSettings.autoConnectEnabled).isFalse()
      layoutInspectorSettings.setAutoConnectEnabledInSettings(true)
      assertThat(layoutInspectorSettings.autoConnectEnabled).isFalse()
    }

    runWithFlagState(true) {
      layoutInspectorSettings.setAutoConnectEnabledInSettings(true)
      assertThat(layoutInspectorSettings.autoConnectEnabled).isTrue()
    }
  }

  private fun runWithFlagState(desiredFlagState: Boolean, task: () -> Unit): Unit {
    val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
    val flagPreviousState = flag.get()
    flag.override(desiredFlagState)

    task()

    // restore flag state
    flag.override(flagPreviousState)
  }
}
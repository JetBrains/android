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

import com.android.flags.Flag
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test

private val EMBEDDED_LAYOUT_INSPECTOR_FLAG =
  StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED

class LayoutInspectorSettingsTest {

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun testEmbeddedLayoutInspectorEnabledFlag() = withEmbeddedLayoutInspector {
    assertThat(enableEmbeddedLayoutInspector).isTrue()

    enableEmbeddedLayoutInspector = false
    assertThat(enableEmbeddedLayoutInspector).isFalse()

    runWithFlagState(EMBEDDED_LAYOUT_INSPECTOR_FLAG, false) {
      assertThat(enableEmbeddedLayoutInspector).isFalse()
      enableEmbeddedLayoutInspector = true
      assertThat(enableEmbeddedLayoutInspector).isFalse()
    }

    runWithFlagState(EMBEDDED_LAYOUT_INSPECTOR_FLAG, true) {
      enableEmbeddedLayoutInspector = true
      assertThat(enableEmbeddedLayoutInspector).isTrue()
    }
  }

  private fun runWithFlagState(flag: Flag<Boolean>, desiredFlagState: Boolean, task: () -> Unit) {
    val flagPreviousState = flag.get()
    flag.override(desiredFlagState)

    task()

    // restore flag state
    flag.override(flagPreviousState)
  }
}

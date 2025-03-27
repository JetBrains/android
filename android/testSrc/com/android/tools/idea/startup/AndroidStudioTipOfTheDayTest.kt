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
package com.android.tools.idea.startup

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.testFramework.ApplicationRule
import com.intellij.util.application
import org.junit.ClassRule
import org.junit.Test

/** Ensures tip-of-the-day is disabled; it does not work correctly in Android Studio (b/302571384). */
class AndroidStudioTipOfTheDayTest {
  companion object {
    @JvmStatic
    @get:ClassRule
    val appRule = ApplicationRule()
  }

  @Test
  fun popupDisabled() {
    @Suppress("UnstableApiUsage")
    assertThat(application.serviceOrNull<TipAndTrickManager>()).isNull()
  }

  @Test
  fun actionDisabled() {
    assertThat(ActionManager.getInstance().getAction("ShowTips")).isNull()
  }
}

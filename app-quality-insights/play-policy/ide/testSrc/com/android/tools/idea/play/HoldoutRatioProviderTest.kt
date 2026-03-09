/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.play

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import java.util.function.Supplier
import org.junit.Rule
import org.junit.Test

const val HOLDOUT_RATIO = "0.31415926"

class HoldoutRatioProviderTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val flagRUle = FlagRule(StudioFlags.PLAY_POLICY_INSIGHTS_HOLDOUT_RATIO, HOLDOUT_RATIO)

  @Test
  fun getHoldoutRatio() {
    val ea = ApplicationManager.getApplication().extensionArea
    val ep = ea.getExtensionPoint<Supplier<Double>>("com.android.tools.idea.play.holdoutRatioProvider")
    assertThat(ep.extensionList.firstOrNull()?.get()).isEqualTo(HOLDOUT_RATIO.toDouble())
  }
}

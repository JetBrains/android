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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.extensions.ExtensionPointName
import java.util.function.Supplier

interface HoldoutRatioProvider : Supplier<Double> {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<HoldoutRatioProvider> = ExtensionPointName.create("com.android.tools.idea.play.holdoutRatioProvider")
  }
}

class HoldoutRatioProviderImpl : HoldoutRatioProvider {
  override fun get(): Double = StudioFlags.PLAY_POLICY_INSIGHTS_HOLDOUT_RATIO.get()?.toDoubleOrNull() ?: 0.0
}

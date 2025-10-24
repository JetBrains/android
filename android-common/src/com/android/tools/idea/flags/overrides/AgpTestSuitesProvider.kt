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
package com.android.tools.idea.flags.overrides

import com.android.flags.BooleanFlag
import com.android.flags.Flag
import com.android.flags.FlagValueProvider
import com.android.tools.idea.flags.StudioFlags

class AgpTestSuitesProvider : FlagValueProvider {
  override fun get(flag: Flag<*>): String? {
    if (flag == StudioFlags.AGP_TEST_SUITES_ENABLED && StudioFlags.JOURNEYS_WITH_GEMINI_EXECUTION.get()) {
      // Journeys with Gemini is enabled, and is dependent on AGP Test Suites, so force enable it.
      return BooleanFlag.Converter.serialize(true)
    }
    return null
  }
}
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

import com.android.flags.Flag
import com.android.flags.FlagValueProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.Version
import com.android.tools.idea.flags.FeatureConfiguration

class AgpReleaseBranchProvider : FlagValueProvider {
  override fun get(flag: Flag<*>): String? {
    if (flag == StudioFlags.USE_ALONGSIDE_AGP) {
      return (Version.IS_AGP_RELEASE_BRANCH || FeatureConfiguration.current < FeatureConfiguration.COMPLETE).toString()
    }
    return null;
  }
}
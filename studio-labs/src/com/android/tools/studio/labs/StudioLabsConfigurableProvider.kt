/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.studio.labs

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider

// In Android plugin, "Studio Labs" is a top-level configurable
class StudioLabsConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable? {
    return StudioLabsSettingsConfigurable()
  }

  override fun canCreateConfigurable(): Boolean {
    if (!IdeInfo.getInstance().isAndroidStudio) {
      return false
    }
    return StudioFlags.STUDIO_LABS_SETTINGS_ENABLED.get() &&
      StudioLabsSettingsConfigurable.isThereAnyFeatureInLabs()
  }
}

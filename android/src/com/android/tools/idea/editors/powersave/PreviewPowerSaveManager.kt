/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.powersave

import com.android.tools.idea.flags.StudioFlags.DESIGN_TOOLS_POWER_SAVE_MODE_SUPPORT
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.util.registry.RegistryManager

object PreviewPowerSaveManager {
  /**
   * Same as [PowerSaveMode] but obeys to the [DESIGN_TOOLS_POWER_SAVE_MODE_SUPPORT] to allow disabling the functionality.
   */
  val isInPowerSaveMode: Boolean
    get() = DESIGN_TOOLS_POWER_SAVE_MODE_SUPPORT.get() && (PowerSaveMode.isEnabled() || isGlobalEssentialHighlightingModeEnabled )

  private val isGlobalEssentialHighlightingModeEnabled: Boolean
    get() = RegistryManager.getInstance().`is`("ide.highlighting.mode.essential")
}
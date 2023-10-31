/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.tools.idea.streaming.emulator.EMULATOR_MAIN_TOOLBAR_ID
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider

class EmulatorCustomizableActionGroupProvider : CustomizableActionGroupProvider() {

  override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
    registrar.addCustomizableActionGroup(EMULATOR_MAIN_TOOLBAR_ID, "Emulator Toolbar")
  }
}

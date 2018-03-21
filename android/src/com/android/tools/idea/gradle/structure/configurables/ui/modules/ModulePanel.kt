/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.modules

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.modules.SigningConfigsTreeModel
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractTabbedMainPanel
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule

class ModulePanel(
    context: PsContext,
    module: PsAndroidModule,
    signingConfigsTreeModel: SigningConfigsTreeModel
) : AbstractTabbedMainPanel(context, placeName = "android.psd.module") {

  private val modulePropertiesConfigPanel = ModulePropertiesConfigPanel(module)
  private val moduleDefaultConfigConfigPanel = ModuleDefaultConfigConfigPanel(module)
  private val moduleSigningConfigsPanel = SigningConfigsPanel(signingConfigsTreeModel)

  init {
    addTab(modulePropertiesConfigPanel)
    addTab(moduleDefaultConfigConfigPanel)
    addTab(moduleSigningConfigsPanel)
  }
}
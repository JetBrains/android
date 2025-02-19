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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractMainPanel
import com.android.tools.idea.gradle.structure.model.PsModule
import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.components.JBLabel
import com.intellij.ui.navigation.Place
import javax.swing.SwingConstants

class KmpModuleConfigurable(
  context: PsContext,
  perspectiveConfigurable: BasePerspectiveConfigurable,
  module: PsModule,
  val detailedMessage: String = "KMP modules are not currently supported in the Project Structure dialog. Please select another module."
) :
  AbstractModuleConfigurable<PsModule, AbstractMainPanel>(context, perspectiveConfigurable, module) {

  override fun createPanel(): AbstractMainPanel = object : AbstractMainPanel(context) {
    init {
      add(JBLabel("<HTML><div style='text-align: center;'>This is a shared KMP Module.<br>$detailedMessage</div></HTML>").apply { horizontalAlignment = SwingConstants.CENTER })
    }

    override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = ActionCallback.DONE
    override fun restoreUiState() = Unit
    override fun dispose() = Unit
  }

  override fun getId(): String = "android.psd.kmp_module." + module.name
}
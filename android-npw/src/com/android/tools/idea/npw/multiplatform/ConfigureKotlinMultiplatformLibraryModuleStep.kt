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
package com.android.tools.idea.npw.multiplatform

import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.contextLabel
import com.android.tools.idea.npw.module.ConfigureModuleStep
import com.android.tools.idea.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.validator.MultiplatformKgpMinVersionValidator
import com.android.tools.idea.wizard.template.Language
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI.Borders.empty
import org.jetbrains.android.util.AndroidBundle
import java.util.Optional

class ConfigureKotlinMultiplatformLibraryModuleStep(
  model: NewKotlinMultiplatformLibraryModuleModel,
  title: String,
) : ConfigureModuleStep<NewKotlinMultiplatformLibraryModuleModel>(
  model, FormFactor.MOBILE, SdkVersionInfo.LOWEST_ACTIVE_API, title = title
) {

  init {
    model.language.set(Optional.of(Language.Kotlin))
    validatorPanel.registerValidator(model.kgpVersion, MultiplatformKgpMinVersionValidator())
  }

  override fun createMainPanel(): DialogPanel = panel {
    row(contextLabel("Module name", AndroidBundle.message("android.wizard.module.help.name"))) {
      cell(moduleName).align(AlignX.FILL)
    }
    row("Package name") {
      cell(packageName).align(AlignX.FILL)
    }

  }.withBorder(empty(6))

  override fun onProceeding() {
    super.onProceeding()
    model.template.set(GradleAndroidModuleTemplate.createMultiplatformModuleTemplate(model.project, model.moduleName.get()))
  }

  override fun getPreferredFocusComponent() = moduleName
}
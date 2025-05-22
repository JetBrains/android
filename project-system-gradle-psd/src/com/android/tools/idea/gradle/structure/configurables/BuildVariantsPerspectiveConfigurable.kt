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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.AndroidGradlePsdBundle
import com.android.tools.idea.gradle.structure.configurables.android.buildvariants.AndroidModuleBuildVariantsConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.structure.dialog.TrackedConfigurable
import com.google.wireless.android.sdk.stats.PSDEvent
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

const val BUILD_VARIANTS_VIEW = "BuildVariantsView"
@Nls val buildVariantsPerspectiveDisplayName = AndroidGradlePsdBundle.message("android.build.variants.perspective.configurable.display.name")

class BuildVariantsPerspectiveConfigurable(context: PsContext)
  : BasePerspectiveConfigurable(context, extraModules = listOf()), TrackedConfigurable {

  override val leftConfigurable = PSDEvent.PSDLeftConfigurable.PROJECT_STRUCTURE_DIALOG_LEFT_CONFIGURABLE_BUILD_VARIANTS

  override fun getId() = "android.psd.build_variants"

  override fun createConfigurableFor(module: PsModule): AbstractModuleConfigurable<out PsModule, *> =
    when {
      module is PsAndroidModule && module.isKmpModule.not() -> createConfigurable(module)
      else -> ModuleUnsupportedConfigurable(context, this, module)
    }

  @Nls
  override fun getDisplayName() = buildVariantsPerspectiveDisplayName

  private fun createConfigurable(module: PsAndroidModule): AndroidModuleBuildVariantsConfigurable =
      AndroidModuleBuildVariantsConfigurable(context, this, module).apply { history = myHistory }

  override fun createComponent(): JComponent = super.createComponent().also { it.name = BUILD_VARIANTS_VIEW }
}
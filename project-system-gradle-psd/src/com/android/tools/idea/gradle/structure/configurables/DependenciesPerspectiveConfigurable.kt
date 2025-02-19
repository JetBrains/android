/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.AndroidModuleDependenciesConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.ProjectDependenciesConfigurable
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.configurables.java.dependencies.JavaModuleDependenciesConfigurable
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.empty.PsEmptyModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.structure.dialog.TrackedConfigurable
import com.google.wireless.android.sdk.stats.PSDEvent
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

const val DEPENDENCIES_VIEW = "DependenciesView"
const val DEPENDENCIES_PERSPECTIVE_DISPLAY_NAME = "Dependencies"

class DependenciesPerspectiveConfigurable(context: PsContext)
  : BasePerspectiveConfigurable(context, extraModules = listOf(PsAllModulesFakeModule(context.project))), TrackedConfigurable {

  override val leftConfigurable = PSDEvent.PSDLeftConfigurable.PROJECT_STRUCTURE_DIALOG_LEFT_CONFIGURABLE_DEPENDENCIES
  override fun getId(): String = "android.psd.dependencies"

  @Nls
  override fun getDisplayName(): String = DEPENDENCIES_PERSPECTIVE_DISPLAY_NAME

  override fun createConfigurableFor(module: PsModule): AbstractModuleConfigurable<out PsModule, *> =
    when {
      module is PsAllModulesFakeModule -> ProjectDependenciesConfigurable(module, context, this)
      module is PsAndroidModule && module.isKmpModule.not() -> AndroidModuleDependenciesConfigurable(module, context, this)
      module is PsAndroidModule && module.isKmpModule -> KmpModuleConfigurable(context, this, module, "Viewing and managing dependencies of a KMP shared module is not currently supported in the Project Structure dialog.")
      module is PsJavaModule -> JavaModuleDependenciesConfigurable(module, context, this)
      module is PsEmptyModule -> ModuleUnsupportedConfigurable(context, this, module, message = "Nothing to show. Please select another module.")
      else -> throw IllegalStateException()
    }

  override fun createComponent(): JComponent = super.createComponent().also { it.name = DEPENDENCIES_VIEW }
}
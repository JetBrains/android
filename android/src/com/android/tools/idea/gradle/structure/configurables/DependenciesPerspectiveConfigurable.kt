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
import com.android.tools.idea.gradle.structure.configurables.java.dependencies.JavaModuleDependenciesConfigurable
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.intellij.openapi.ui.NamedConfigurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

const val DEPENDENCIES_VIEW = "DependenciesView"
const val DEPENDENCIES_PERSPECTIVE_DISPLAY_NAME = "Dependencies"
const val DEPENDENCIES_PERSPECTIVE_PLACE_NAME = "dependencies.place"

class DependenciesPerspectiveConfigurable(context: PsContext) : BasePerspectiveConfigurable(context) {
  private val myConfigurablesByGradlePath = mutableMapOf<String, AbstractDependenciesConfigurable<out PsModule>>()

  private val myExtraTopModules = mutableListOf<PsModule>()
  private val myExtraTopConfigurables = mutableMapOf<PsModule, AbstractDependenciesConfigurable<out PsModule>>()

  override fun getId(): String = "android.psd.dependencies"

  @Nls
  override fun getDisplayName(): String = DEPENDENCIES_PERSPECTIVE_DISPLAY_NAME

  override fun getNavigationPathName(): String = DEPENDENCIES_PERSPECTIVE_PLACE_NAME

  override fun getConfigurable(module: PsModule): NamedConfigurable<out PsModule>? =
    when (module) {
      is PsAllModulesFakeModule -> myExtraTopConfigurables.getOrPut(module) {
        ProjectDependenciesConfigurable(module, context, extraTopModules).also { it.history = myHistory }
      }
      is PsAndroidModule -> myConfigurablesByGradlePath.getOrPut(module.gradlePath) {
        AndroidModuleDependenciesConfigurable(module, context, extraTopModules).also { it.history = myHistory }
      }
      is PsJavaModule -> myConfigurablesByGradlePath.getOrPut(module.gradlePath) {
        JavaModuleDependenciesConfigurable(module, context, extraTopModules).also { it.history = myHistory }
      }
      else -> null
    }

  override fun createComponent(): JComponent = super.createComponent().also { it.name = DEPENDENCIES_VIEW }

  override fun getExtraTopModules(): List<PsModule> =
    myExtraTopModules.also {
      if (it.isEmpty()) {
        it.add(PsAllModulesFakeModule(context.project))
      }
    }

  override fun dispose() {
    super.dispose()
    myConfigurablesByGradlePath.clear()
  }
}

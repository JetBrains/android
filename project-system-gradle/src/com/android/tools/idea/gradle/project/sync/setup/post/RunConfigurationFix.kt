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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

internal fun Project.fixRunConfigurations() {
  val modules = ModuleManager.getInstance(this).modules.toList()
  RunManagerEx.getInstanceEx(this).allConfigurationsList.forEach { validateAndFixRunConfiguration(it, modules) }
}

private fun validateAndFixRunConfiguration(runConfiguration: RunConfiguration, modules: List<Module>) {
  when (runConfiguration) {
    is ModuleBasedConfiguration<*, *> -> {
      if (runConfiguration.configurationModule.module == null && !runConfiguration.configurationModule.moduleName.contains(".")) {
        val replacementModule = findReplacementModule(modules, runConfiguration.configurationModule.moduleName)
        if (replacementModule != null) {
          runConfiguration.setModule(replacementModule)
        }
      }
    }
  }
}

private fun findReplacementModule(modules: Collection<Module>, originalModuleName: String): Module? {
  return modules.singleOrNull {
    it.name.substringAfterLast('.', it.name) == originalModuleName
  }
}

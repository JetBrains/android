/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.benchmark

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.benchmarkModule.generateBenchmarkModule
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.templates.TemplateAttributes.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.intellij.openapi.project.Project
import com.intellij.util.lang.JavaVersion

class NewBenchmarkModuleModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker
) : ModuleModel(project, templateHandle, projectSyncInvoker, "benchmark", "New Benchmark Module") {
  override val renderer = object : ModuleTemplateRenderer() {
    override val recipe: Recipe = { td: TemplateData -> generateBenchmarkModule(td as ModuleTemplateData) }

    override fun init() {
      super.init()

      val newValues = mutableMapOf(
        ATTR_APP_TITLE to moduleName.get(),
        ATTR_IS_NEW_MODULE to true,
        ATTR_IS_LIBRARY_MODULE to true
      )

      TemplateValueInjector(newValues)
        .setBuildVersion(androidSdkInfo.value, project, false)

      templateValues.putAll(newValues)

      if (StudioFlags.NPW_NEW_MODULE_TEMPLATES.get()) {
        moduleTemplateDataBuilder.apply {
          projectTemplateDataBuilder.apply {
            javaVersion = JavaVersion.parse("1.8")
          }
          isLibrary = true
        }
      }
    }
  }
}

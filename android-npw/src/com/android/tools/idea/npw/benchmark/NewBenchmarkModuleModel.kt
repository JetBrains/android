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

import com.android.tools.idea.npw.benchmark.BenchmarkModuleType.MACROBENCHMARK
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType.MICROBENCHMARK
import com.android.tools.idea.npw.model.ExistingProjectModelData
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.module.recipes.benchmarkModule.generateBenchmarkModule
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.generateMacrobenchmarkModule
import com.android.tools.idea.observable.core.ObjectValueProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.TemplateData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer as RenderLoggingEvent

enum class BenchmarkModuleType(val title: String) {
  MACROBENCHMARK("Macrobenchmark"),
  MICROBENCHMARK("Microbenchmark"),
}

class NewBenchmarkModuleModel(
  project: Project,
  moduleParent: String,
  projectSyncInvoker: ProjectSyncInvoker,
) : ModuleModel(
  name = "benchmark",
  commandName = "New Benchmark Module",
  isLibrary = true,
  projectModelData = ExistingProjectModelData(project, projectSyncInvoker),
  moduleParent = moduleParent,
  wizardContext = NEW_MODULE
) {
  val benchmarkModuleType = ObjectValueProperty<BenchmarkModuleType>(MACROBENCHMARK)
  val targetModule = OptionalValueProperty<Module>()

  override val renderer = object : ModuleTemplateRenderer() {
    override val recipe: Recipe
      get() = { td: TemplateData ->
        when (benchmarkModuleType.get()) {
          MICROBENCHMARK -> generateBenchmarkModule(
            moduleData = td as ModuleTemplateData,
            useGradleKts = useGradleKts.get()
          )
          MACROBENCHMARK -> generateMacrobenchmarkModule(
            newModule = td as ModuleTemplateData,
            useGradleKts = useGradleKts.get(),
            targetModule = targetModule.value,
          )
        }
      }
  }

  override val loggingEvent: AndroidStudioEvent.TemplateRenderer
    get() = when (benchmarkModuleType.get()) {
      MICROBENCHMARK -> RenderLoggingEvent.BENCHMARK_LIBRARY_MODULE
      MACROBENCHMARK -> RenderLoggingEvent.MACROBENCHMARK_LIBRARY_MODULE
    }
}


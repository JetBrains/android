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

import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.ui.ActivityGallery.getTemplateIcon
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle.message
import javax.swing.Icon

class NewBenchmarkModuleDescriptionProvider : ModuleDescriptionProvider {
  override fun getDescriptions(project: Project?): Collection<ModuleGalleryEntry> = listOf(BenchmarkModuleTemplateGalleryEntry())

  private class BenchmarkModuleTemplateGalleryEntry : ModuleGalleryEntry {
    private val templateHandle: TemplateHandle = TemplateHandle(
      TemplateManager.getInstance().getTemplateFile(Template.CATEGORY_APPLICATION, "Benchmark Module")!!)

    override val icon: Icon? = getTemplateIcon(templateHandle, false)
    override val name: String = message("android.wizard.module.new.benchmark.module.app")
    override val description: String? = templateHandle.metadata.description
    override fun toString(): String = name
    override fun createStep(model: NewModuleModel): SkippableWizardStep<*> {
      val benchmarkModuleModel = NewBenchmarkModuleModel(model.project.value, templateHandle, model.projectSyncInvoker)
      return ConfigureBenchmarkModuleStep(benchmarkModuleModel, name, templateHandle.metadata.minSdk)
    }
  }
}
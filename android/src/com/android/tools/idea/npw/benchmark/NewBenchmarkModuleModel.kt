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

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.getInitialSourceLanguage
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateMetadata.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_MODULE
import com.intellij.openapi.project.Project

class NewBenchmarkModuleModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker
) : ModuleModel(project, templateHandle, projectSyncInvoker, "benchmark") {
  @JvmField val packageName = StringValueProperty()
  @JvmField val language = OptionalValueProperty(getInitialSourceLanguage(project))
  @JvmField val minSdk = OptionalValueProperty<VersionItem>()

  override val renderer = object : ModuleTemplateRenderer() {
    override fun init() {
      super.init()
      val modulePaths = createDefaultTemplateAt(project.basePath!!, moduleName.get()).paths

      val newValues = mutableMapOf<String, Any>(
          ATTR_APP_TITLE to moduleName.get(),
          ATTR_IS_NEW_MODULE to true,
          ATTR_IS_LIBRARY_MODULE to true
      )

      TemplateValueInjector(newValues)
        .setBuildVersion(minSdk.value, project, false)
        .setProjectDefaults(project, false)
        .setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), packageName.get())
        .setJavaVersion(project)
        .setLanguage(language.value)

      templateValues.putAll(newValues)
    }
  }
}
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
package com.android.tools.idea.npw.java

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.module.ModuleModel
import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.templates.TemplateAttributes.ATTR_CLASS_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateAttributes.ATTR_IS_NEW_MODULE
import com.intellij.openapi.project.Project

class NewLibraryModuleModel(
  project: Project, templateHandle: TemplateHandle, projectSyncInvoker: ProjectSyncInvoker
) : ModuleModel(project, templateHandle, projectSyncInvoker, "lib") {
  @JvmField
  val packageName = StringValueProperty()
  @JvmField
  val className = StringValueProperty("MyClass")
  @JvmField
  val language = OptionalValueProperty(RenderTemplateModel.getInitialSourceLanguage(project))

  override val renderer = object : ModuleTemplateRenderer() {
    override fun init() {
      super.init()
      val modulePaths = createDefaultTemplateAt(project.basePath!!, moduleName.get()).paths

      val newValues = mutableMapOf<String, Any>(
        ATTR_CLASS_NAME to className.get(),
        ATTR_IS_NEW_MODULE to true,
        ATTR_IS_LIBRARY_MODULE to true
      )

      TemplateValueInjector(newValues)
        .setModuleRoots(modulePaths, project.basePath!!, moduleName.get(), packageName.get())
        .setJavaVersion(project)
        .setLanguage(language.value)

      templateValues.putAll(newValues)
    }
  }
}
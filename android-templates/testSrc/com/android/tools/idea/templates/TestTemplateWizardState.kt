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
package com.android.tools.idea.templates

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.templates.Parameter.Type
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MODULE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PACKAGE_NAME
import com.android.tools.idea.templates.TemplateAttributes.ATTR_TOP_OUT
import com.intellij.openapi.diagnostic.logger
import java.io.File

/**
 * Helper class that tracks the Wizard Template and the Template Values
 */
class TestTemplateWizardState {
  val templateValues = mutableMapOf<String, Any>()
  internal lateinit var template: Template

  fun setParameterDefaults() {
    val metadata = template.metadata ?: return logger<TestTemplateWizardState>().warn("Null metadata")

    metadata.parameters
      .filterNot { templateValues.containsKey(it.id!!) || it.initial == null }
      .forEach { param ->
        when (param.type) {
          Type.BOOLEAN -> put(param.id!!, param.initial!!.toBoolean())
          Type.ENUM, Type.STRING -> put(param.id!!, param.initial!!)
          else -> {
          }
        }
      }
  }

  operator fun get(key: String): Any? = templateValues[key]
  fun getBoolean(attr: String): Boolean = templateValues[attr] as Boolean
  fun getInt(key: String): Int = templateValues[key] as Int
  fun getString(key: String): String = templateValues[key] as String

  fun setTemplateLocation(file: File) {
    if (::template.isInitialized && template.rootPath.absolutePath == file.absolutePath) {
      return
    }
    if (::template.isInitialized) {
      // Clear out any parameters from the old template and bring in the defaults for the new template.
      templateValues.keys.removeAll(template.metadata?.parameters.orEmpty().map(Parameter::id))
    }
    template = Template.createFromPath(file)
    setParameterDefaults()
  }

  fun put(key: String, value: Any) {
    templateValues[key] = value
  }

  fun putAll(map: Map<String, Any>) = templateValues.putAll(map)

  fun populateDirectoryParameters() {
    val projectPath = getString(ATTR_TOP_OUT)
    val moduleName = getString(ATTR_MODULE_NAME)
    val packageName = getString(ATTR_PACKAGE_NAME)
    TemplateValueInjector(templateValues)
      .setModuleRoots(createDefaultTemplateAt(projectPath, moduleName).paths, projectPath, moduleName, packageName)
  }
}
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

import com.android.SdkConstants
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.templates.TemplateAttributes.ATTR_GRADLE_PLUGIN_VERSION
import freemarker.template.SimpleScalar
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException

/**
 * Method invoked by FreeMarker to compute the right dependency string to use in the current module.
 * The right string to use depends on the version of Gradle used in the module.
 *
 * Arguments:
 *  1. The configuration (if left out, defaults to "compile")
 *
 * Example usage: `espresso=getDependency('androidTestCompile')`, which (for Gradle 3.0) will return "androidTestImplementation"
 */
class FmGetConfigurationNameMethod(private val myParamMap: Map<String, Any>) : TemplateMethodModelEx {
  @Suppress("DEPRECATION")
  override fun exec(args: List<*>): TemplateModel {
    if (args.size > 1) {
      throw TemplateModelException("Wrong arguments")
    }
    val configuration = args.getOrNull(0)?.toString() ?: SdkConstants.GRADLE_COMPILE_CONFIGURATION
    return SimpleScalar(convertConfiguration(myParamMap, configuration))
  }

  companion object {
    @JvmStatic
    fun convertConfiguration(myParamMap: Map<String, Any>, configuration: String): String {
      val gradlePluginVersion: String? = myParamMap[ATTR_GRADLE_PLUGIN_VERSION] as? String
      return GradleUtil.mapConfigurationName(configuration, gradlePluginVersion, false)
    }
  }
}
/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.SdkConstants.GRADLE_ANDROID_TEST_API_CONFIGURATION
import com.android.SdkConstants.GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION
import com.android.SdkConstants.GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION
import com.android.SdkConstants.GRADLE_API_CONFIGURATION
import com.android.SdkConstants.GRADLE_COMPILE_CONFIGURATION
import com.android.SdkConstants.GRADLE_IMPLEMENTATION_CONFIGURATION
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.templates.TemplateAttributes.ATTR_BUILD_API
import com.android.tools.idea.templates.TemplateAttributes.ATTR_MIN_API_LEVEL
import com.android.tools.idea.templates.TemplateAttributes.ATTR_PROJECT_OUT
import com.google.common.collect.SetMultimap
import freemarker.template.TemplateBooleanModel
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException
import org.jetbrains.android.facet.AndroidFacet

/**
 * Method invoked by FreeMarker to check whether a given dependency is available in this module.
 *
 *
 * Arguments:
 *
 *  1. Maven group and artifact IDs, e.g. `"com.android.support:appcompat-v7"`
 *  1. (Optional) Name of the Gradle configuration to check. Defaults to `"compile"`, `"api"` or `"implementation"`.
 *
 *
 * Example usage: `espresso=hasDependency('com.android.support.test.espresso:espresso-core', 'androidTestCompile')`.
 */
class FmHasDependencyMethod(private val myParamMap: Map<String, Any>) : TemplateMethodModelEx {
  @Suppress("DEPRECATION")
  override fun exec(args: List<*>): TemplateModel {
    if (args.size !in 1..2) {
      throw TemplateModelException("Wrong arguments")
    }

    fun Boolean.toTemplateBooleanModel(): TemplateBooleanModel = if (this) TemplateBooleanModel.TRUE else TemplateBooleanModel.FALSE

    val artifact = args[0].toString()
    if (artifact.isEmpty()) {
      return TemplateBooleanModel.FALSE
    }

    val defaultConfigurations = arrayOf(GRADLE_COMPILE_CONFIGURATION,
                                        GRADLE_IMPLEMENTATION_CONFIGURATION,
                                        GRADLE_API_CONFIGURATION)
    val defaultTestConfigurations = arrayOf(GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION,
                                            GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION,
                                            GRADLE_ANDROID_TEST_API_CONFIGURATION)

    // Determine the configuration to check, based on the second argument passed to the function.
    // Defaults to "compile" and "implementation and "api".
    val configurations: Array<String> = if (args.size > 1) {
      arrayOf(args[1].toString())
    }
    else {
      defaultConfigurations
    }

    @Suppress("UNCHECKED_CAST")
    fun findInMultiMap(): Boolean {
      val dependencies = myParamMap[TemplateMetadata.ATTR_DEPENDENCIES_MULTIMAP] as? SetMultimap<String, String> ?: return false
      return configurations.any { c ->
        dependencies.get(c).any { it.contains(artifact) }
      }
    }

    if (findInMultiMap()) {
      return TemplateBooleanModel.TRUE
    }

    fun findCorrespondingModule(): Boolean? {
      val modulePath = myParamMap[ATTR_PROJECT_OUT] as? String ?: return null
      val module = findModule(modulePath) ?: return null
      val facet = AndroidFacet.getInstance(module) ?: return null
      // TODO: b/23032990
      val androidModel = AndroidModuleModel.get(facet) ?: return null
      return when (configurations[0]) {
        in defaultConfigurations ->
          GradleUtil.dependsOn(androidModel, artifact) || GradleUtil.dependsOnJavaLibrary(androidModel, artifact) // For Kotlin dependencies
        in defaultTestConfigurations -> GradleUtil.dependsOnAndroidTest(androidModel, artifact)
        else -> throw TemplateModelException("Unknown dependency configuration " + configurations[0])
      }
    }

    val isCorrespondingDefaultModuleFound = findCorrespondingModule()
    if (isCorrespondingDefaultModuleFound != null) {
      return isCorrespondingDefaultModuleFound.toTemplateBooleanModel()
    }

    // Creating a new module, so no existing dependencies: provide some defaults. This is really intended for appcompat-v7,
    // but since it depends on support-v4, we include it here (such that a query to see if support-v4 is installed in a newly
    // created project will return true since it will be by virtue of appcompat also being installed.)
    if (artifact.contains(SdkConstants.APPCOMPAT_LIB_ARTIFACT) || artifact.contains(SdkConstants.SUPPORT_LIB_ARTIFACT)) {
      // No dependencies: Base it off of the minApi and buildApi versions:
      // If building with Lollipop, and targeting anything earlier than Lollipop, use appcompat.
      // (Also use it if minApi is less than ICS.)
      val buildApiObject = myParamMap[ATTR_BUILD_API]
      val minApiObject = myParamMap[ATTR_MIN_API_LEVEL]
      return (buildApiObject is Int && minApiObject is Int &&
              minApiObject >= 8 && (buildApiObject >= 21 && minApiObject < 21 || minApiObject < 14)).toTemplateBooleanModel()
    }

    return TemplateBooleanModel.FALSE
  }
}
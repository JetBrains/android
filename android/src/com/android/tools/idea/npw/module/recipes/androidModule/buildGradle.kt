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
package com.android.tools.idea.npw.module.recipes.androidModule

import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision.parseRevision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.npw.module.recipes.androidConfig
import com.android.tools.idea.npw.module.recipes.getConfigurationName
import com.android.tools.idea.npw.module.recipes.supportsImprovedTestDeps
import com.android.tools.idea.templates.RepositoryUrlManager
import com.android.tools.idea.templates.resolveDependency
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.has
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  isKts: Boolean,
  isLibraryProject: Boolean,
  isDynamicFeature: Boolean,
  packageName: String,
  buildApiString: String,
  buildToolsVersion: String,
  minApi: Int,
  targetApi: Int,
  useAndroidX: Boolean,
  gradlePluginVersion: GradlePluginVersion,
  includeCppSupport: Boolean = false,
  // TODO(qumeric): do something better
  cppFlags: String = "",
  isCompose: Boolean = false,
  baseFeatureName: String = "base",
  wearProjectName: String = "wear",
  formFactorNames: Map<FormFactor, List<String>>,
  hasTests: Boolean = true,
  addLintOptions: Boolean = false
): String {
  val explicitBuildToolsVersion = needsExplicitBuildToolsVersion(GradleVersion.parse(gradlePluginVersion), parseRevision(buildToolsVersion))
  val supportsImprovedTestDeps = supportsImprovedTestDeps(gradlePluginVersion)
  val isApplicationProject = !isLibraryProject

  // Note: Not doing this programmatically (applyPlugin) for groovy because if there is no plugin it uses syntax plugins { id(...) }
  val pluginsBlock = "    " + when {
    isLibraryProject -> "apply plugin: 'com.android.library'"
    isDynamicFeature -> "apply plugin: 'com.android.dynamic-feature'"
    else -> "apply plugin: 'com.android.application'"
  }

  val androidConfigBlock = androidConfig(
    buildApiString,
    explicitBuildToolsVersion,
    buildToolsVersion,
    minApi,
    targetApi,
    useAndroidX,
    cppFlags,
    isLibraryProject,
    includeCppSupport,
    isApplicationProject,
    packageName,
    hasTests = hasTests,
    canUseProguard = true,
    addLintOptions = addLintOptions
  )

  val composeDependenciesBlock = renderIf(isCompose) { "kotlinPlugin \"androidx.compose:compose-compiler:+\"" }

  val oldTestDependenciesBlock = renderIf(!supportsImprovedTestDeps) {
    """
    ${getConfigurationName("androidTestCompile", gradlePluginVersion)}("${
    resolveDependency(RepositoryUrlManager.get(), "com.android.support.test.espresso:espresso-core:+")
    }", {
      exclude group : "com.android.support", module: "support-annotations"
    })
    """
  }

  val dynamicFeatureBlock = when {
    isDynamicFeature -> """implementation project (":${baseFeatureName}")"""
    !wearProjectName.isBlank() && formFactorNames.has(FormFactor.Mobile) && formFactorNames.has(FormFactor.Wear) ->
      """wearApp project (":${wearProjectName}")"""
    else -> ""
  }

  val dependenciesBlock = """
  dependencies {
    $composeDependenciesBlock
    $oldTestDependenciesBlock
    $dynamicFeatureBlock
  }
  """

  return if (isKts) {
    """

    $androidConfigBlock
    $dependenciesBlock
    """
      .split("\n").joinToString("\n") {
        it.replace("'", "\"")
          .toKtsFunction("compileSdkVersion")
          .toKtsProperty("applicationId")
          .toKtsFunction("minSdkVersion")
          .toKtsFunction("targetSdkVersion")
          .toKtsProperty("versionCode")
          .toKtsProperty("versionName")
          .toKtsProperty("testInstrumentationRunner")
          .toKtsProperty("minifyEnabled")
          .toKtsFunction("proguardFiles")
          .toKtsFunction("wearApp")
          .replace("minifyEnabled", "isMinifyEnabled")
          .replace("release {", "getByName(\"release\") {")
      }
  }
  else {
    """
    $pluginsBlock
    $androidConfigBlock
    $dependenciesBlock
    """
  }
}

private fun String.toKtsFunction(funcName: String): String = if (this.contains("$funcName ")) {
  this.replace("$funcName ", "$funcName(") + ")"
}
else {
  this
}

private fun String.toKtsProperty(funcName: String): String = this.replace("$funcName ", "$funcName = ")


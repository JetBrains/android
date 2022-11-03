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
package com.android.tools.idea.npw.module.recipes.dynamicFeatureModule

import com.android.SdkConstants
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.tools.idea.npw.dynamicapp.DeviceFeatureModel
import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.module.recipes.addInstrumentedTests
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.addTestDependencies
import com.android.tools.idea.npw.module.recipes.addLocalTests
import com.android.tools.idea.npw.module.recipes.androidModule.buildGradle
import com.android.tools.idea.npw.module.recipes.createDefaultDirectories
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.res.values.stringsXml
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

fun RecipeExecutor.generateDynamicFeatureModule(
  moduleData: ModuleTemplateData,
  isInstantModule: Boolean,
  dynamicFeatureTitle: String,
  fusing: Boolean,
  downloadInstallKind: DownloadInstallKind,
  deviceFeatures: Collection<DeviceFeatureModel>,
  useGradleKts: Boolean
) {
  val (projectData, srcOut, _, manifestOut, instrumentedTestOut, localTestOut, _, moduleOut) = moduleData
  val apis = moduleData.apis
  val (buildApi, targetApi,  minApi, appCompatVersion) = apis
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val language = projectData.language
  val name = moduleData.name
  val projectSimpleName = NewProjectModel.nameToJavaPackage(name)
  val packageName = moduleData.packageName
  val baseFeature = moduleData.baseFeature!!

  val manifestXml = androidManifestXml(
    fusing.toString(), isInstantModule, projectSimpleName, downloadInstallKind, deviceFeatures
  )

  createDefaultDirectories(moduleOut, srcOut)
  addIncludeToSettings(name)

  val buildFile = if (useGradleKts) SdkConstants.FN_BUILD_GRADLE_KTS else FN_BUILD_GRADLE
  save(
    buildGradle(
      projectData.gradlePluginVersion,
      useGradleKts,
      false,
      true,
      applicationId = moduleData.namespace,
      buildApi.apiString,
      minApi.apiString,
      targetApi.apiString,
      useAndroidX,
      baseFeatureName = baseFeature.name,
      formFactorNames = projectData.includedFormFactorNames
    ), moduleOut.resolve(buildFile)
  )

  applyPlugin("com.android.dynamic-feature", projectData.gradlePluginVersion)
  addKotlinIfNeeded(projectData, targetApi = targetApi.api)

  save(manifestXml, manifestOut.resolve(FN_ANDROID_MANIFEST_XML))
  save(gitignore(), moduleOut.resolve(".gitignore"))
  addLocalTests(packageName, localTestOut, language)
  addInstrumentedTests(packageName, useAndroidX, false, instrumentedTestOut, language)
  addTestDependencies()
  addDependency("com.android.support:support-annotations:${appCompatVersion}.+", "androidTestCompile")

  addDynamicFeature(moduleData.name, baseFeature.dir)
  if (isInstantModule) {
    mergeXml(baseAndroidManifestXml(), baseFeature.dir.resolve("src/main/$FN_ANDROID_MANIFEST_XML"))
  }
  mergeXml(stringsXml(dynamicFeatureTitle, projectSimpleName), baseFeature.resDir.resolve("values/strings.xml"))
}

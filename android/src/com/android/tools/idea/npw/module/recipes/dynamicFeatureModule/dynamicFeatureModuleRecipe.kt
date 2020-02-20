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

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision.parseRevision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.npw.dynamicapp.DeviceFeatureModel
import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.addTestDependencies
import com.android.tools.idea.npw.module.recipes.addTests
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestKt
import com.android.tools.idea.npw.module.recipes.createDefaultDirectories
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.res.values.stringsXml
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.test.exampleInstrumentedTestJava
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.test.exampleInstrumentedTestKt
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

fun RecipeExecutor.generateDynamicFeatureModule(
  moduleData: ModuleTemplateData,
  isInstantModule: Boolean,
  dynamicFeatureTitle: String,
  fusing: Boolean,
  downloadInstallKind: DownloadInstallKind,
  deviceFeatures: Collection<DeviceFeatureModel>
) {
  val (projectData, srcOut, resOut, manifestOut, testOut, unitTestOut, _, moduleOut) = moduleData
  val apis = moduleData.apis
  val (buildApi, targetApi,  minApi, appCompatVersion) = apis
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val language = projectData.language
  val name = moduleData.name
  val projectSimpleName = NewProjectModel.nameToJavaPackage(name)
  val packageName = moduleData.packageName
  val agpVersion = projectData.gradlePluginVersion
  val baseFeature = moduleData.baseFeature!!

  val manifestXml = androidManifestXml(
    fusing.toString(), isInstantModule, packageName, projectSimpleName, downloadInstallKind, deviceFeatures
  )

  createDefaultDirectories(moduleOut, srcOut)
  addIncludeToSettings(name)

  val buildToolsVersion = projectData.buildToolsVersion
  save(buildGradle(
    baseFeature.name,
    agpVersion,
    false,
    buildApi.apiString,
    needsExplicitBuildToolsVersion(GradleVersion.parse(projectData.gradlePluginVersion), parseRevision(buildToolsVersion)),
    buildToolsVersion,
    minApi.apiString,
    targetApi.apiString,
    useAndroidX
    ), moduleOut.resolve(FN_BUILD_GRADLE))

  applyPlugin("com.android.dynamic-feature")
  addKotlinIfNeeded(projectData)

  save(manifestXml, manifestOut.resolve(FN_ANDROID_MANIFEST_XML))
  save(gitignore(), moduleOut.resolve(".gitignore"))
  addTests(packageName, useAndroidX, false, testOut, unitTestOut, language)
  addTestDependencies(agpVersion)

  val supportsImprovedTestDeps = GradleVersion.parse(agpVersion).compareIgnoringQualifiers("3.0.0") >= 0
  if (supportsImprovedTestDeps) {
    // TODO(qumeric): check if we still need it
    /*The following addDependency is added to pass UI tests in AddDynamicFeatureTest. b/123781255*/
    addDependency("com.android.support:support-annotations:${appCompatVersion}.+", "androidTestCompile")
  }

  addDynamicFeature(moduleData.name, baseFeature.dir)
  if (isInstantModule) {
    mergeXml(baseAndroidManifestXml(), baseFeature.dir.resolve("src/main/$FN_ANDROID_MANIFEST_XML"))
  }
  mergeXml(stringsXml(dynamicFeatureTitle, projectSimpleName), baseFeature.resDir.resolve("values/strings.xml"))
}

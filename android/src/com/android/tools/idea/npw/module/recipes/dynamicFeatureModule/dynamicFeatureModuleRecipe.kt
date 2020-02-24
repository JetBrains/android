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

import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision.parseRevision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.npw.dynamicapp.DeviceFeatureModel
import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestKt
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.androidManifestXml
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.baseAndroidManifestXml
import com.android.tools.idea.npw.module.recipes.dynamicFeatureModule.buildGradle
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
  val (projectData, srcOut, resOut, manifestOut) = moduleData
  val apis = moduleData.apis
  val (minApi, _, buildApi, targetApi, targetApiString, buildApiString) = apis
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val language = projectData.language
  val ktOrJavaExt = language.extension
  val moduleOut = moduleData.rootDir
  val name = moduleData.name
  val projectSimpleName = NewProjectModel.nameToJavaPackage(name)
  val packageName = moduleData.packageName
  val unitTestOut = moduleData.unitTestDir
  val testOut = moduleData.testDir
  val agpVersion = projectData.gradlePluginVersion
  val baseFeature = moduleData.baseFeature!!

  createDirectory(moduleOut)
  createDirectory(srcOut)
  createDirectory(resOut.resolve("drawable"))
  addIncludeToSettings(name)

  val buildToolsVersion = projectData.buildToolsVersion
  save(buildGradle(
    baseFeature.name,
    agpVersion,
    false,
    packageName,
    buildApiString!!,
    needsExplicitBuildToolsVersion(GradleVersion.parse(projectData.gradlePluginVersion), parseRevision(buildToolsVersion)),
    buildToolsVersion,
    minApi,
    targetApiString ?: targetApi.toString(),
    useAndroidX,
    language
    ), moduleOut.resolve("build.gradle"))
  save(
    androidManifestXml(fusing.toString(), isInstantModule, packageName, projectSimpleName, downloadInstallKind, deviceFeatures),
    manifestOut.resolve("AndroidManifest.xml")
  )
  save(gitignore(), moduleOut.resolve(".gitignore"))

  val exampleInstrumentedTest = when (projectData.language) {
    Language.Java -> exampleInstrumentedTestJava(packageName, useAndroidX)
    Language.Kotlin -> exampleInstrumentedTestKt(packageName, useAndroidX)
  }
  save(exampleInstrumentedTest, testOut.resolve("ExampleInstrumentedTest.${ktOrJavaExt}"))

  val exampleUnitTest = when (projectData.language) {
    Language.Java -> exampleUnitTestJava(packageName)
    Language.Kotlin -> exampleUnitTestKt(packageName)
  }
  save(exampleUnitTest, unitTestOut.resolve("ExampleUnitTest.${ktOrJavaExt}"))

  addDependency("junit:junit:4.12", "testCompile")

  val supportsImprovedTestDeps = GradleVersion.parse(agpVersion).compareIgnoringQualifiers("3.0.0") >= 0
  if (supportsImprovedTestDeps) {
    addDependency("com.android.support.test:runner:+","androidTestCompile")
    addDependency("com.android.support.test.espresso:espresso-core:+" ,"androidTestCompile")
    /*The following addDependency is added to pass UI tests in AddDynamicFeatureTest. b/123781255*/
    addDependency("com.android.support:support-annotations:${buildApi}.+", "androidTestCompile")
  }

  if (language == Language.Kotlin && useAndroidX) {
    addDependency("androidx.core:core-ktx:+")
  }

  addDynamicFeature(moduleData.name, baseFeature.dir)
  if (isInstantModule) {
    mergeXml(baseAndroidManifestXml(), baseFeature.dir.resolve("src/main/AndroidManifest.xml"))
  }
  mergeXml(stringsXml(dynamicFeatureTitle, projectSimpleName), baseFeature.resDir.resolve("values/strings.xml"))

  addKotlinIfNeeded(projectData)
}

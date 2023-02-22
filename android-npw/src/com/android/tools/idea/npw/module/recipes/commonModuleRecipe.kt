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
package com.android.tools.idea.npw.module.recipes

import com.android.SdkConstants
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.tools.idea.npw.module.recipes.androidModule.buildGradle
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleColors
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStrings
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleThemes
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

enum class IconsGenerationStyle {
  ALL,
  MIPMAP_ONLY,
  MIPMAP_SQUARE_ONLY,
  NONE;
}

fun RecipeExecutor.generateCommonModule(
  data: ModuleTemplateData,
  appTitle: String?, // may be null only for libraries
  useKts: Boolean,
  manifestXml: String,
  generateGenericLocalTests: Boolean = false,
  generateGenericInstrumentedTests: Boolean = false,
  iconsGenerationStyle: IconsGenerationStyle = IconsGenerationStyle.ALL,
  themesXml: String? = androidModuleThemes(data.projectTemplateData.androidXSupport, data.apis.minApi, data.themesData.main.name),
  themesXmlNight: String? = null,
  colorsXml: String? = androidModuleColors(),
  addLintOptions: Boolean = false,
  enableCpp: Boolean = false,
  cppStandard: CppStandardType = CppStandardType.`Toolchain Default`,
  bytecodeLevel: BytecodeLevel = BytecodeLevel.default,
  ) {
  val (projectData, srcOut, resOut, manifestOut, instrumentedTestOut, localTestOut, _, moduleOut) = data
  val (useAndroidX, agpVersion) = projectData
  val language = projectData.language
  val isLibraryProject = data.isLibrary
  val packageName = data.packageName
  val apis = data.apis
  val minApi = apis.minApi

  createDefaultDirectories(moduleOut, srcOut)
  addIncludeToSettings(data.name)

  val buildFile = if (useKts) FN_BUILD_GRADLE_KTS else FN_BUILD_GRADLE

  save(
    buildGradle(
      agpVersion,
      useKts,
      isLibraryProject,
      data.isDynamic,
      applicationId = data.namespace,
      apis.buildApi.apiString,
      minApi.apiString,
      apis.targetApi.apiString,
      useAndroidX,
      formFactorNames = projectData.includedFormFactorNames,
      hasTests = generateGenericLocalTests,
      addLintOptions = addLintOptions,
      enableCpp = enableCpp,
      cppStandard = cppStandard
    ),
    moduleOut.resolve(buildFile)
  )

  // Note: com.android.* needs to be applied before kotlin
  when {
    isLibraryProject -> applyPlugin("com.android.library", projectData.gradlePluginVersion)
    data.isDynamic -> applyPlugin("com.android.dynamic-feature", projectData.gradlePluginVersion)
    else -> applyPlugin("com.android.application", projectData.gradlePluginVersion)
  }
  addKotlinIfNeeded(projectData, targetApi = apis.targetApi.api)
  requireJavaVersion(bytecodeLevel.versionString, data.projectTemplateData.language == Language.Kotlin)

  save(manifestXml, manifestOut.resolve(FN_ANDROID_MANIFEST_XML))
  save(gitignore(), moduleOut.resolve(".gitignore"))
  if (generateGenericLocalTests) {
    addLocalTests(packageName, localTestOut, language)
    addTestDependencies()
  }
  if (generateGenericInstrumentedTests) {
    addInstrumentedTests(packageName, useAndroidX, isLibraryProject, instrumentedTestOut, language)
    addTestDependencies()
  }
  proguardRecipe(moduleOut, data.isLibrary)

  if (!isLibraryProject) {
    when(iconsGenerationStyle) {
      IconsGenerationStyle.ALL -> copyIcons(resOut, minApi.api)
      IconsGenerationStyle.MIPMAP_ONLY -> copyMipmapFolder(resOut)
      IconsGenerationStyle.MIPMAP_SQUARE_ONLY -> copyMipmapFile(resOut, "ic_launcher.webp")
      IconsGenerationStyle.NONE -> Unit
    }
    with(resOut.resolve(SdkConstants.FD_RES_VALUES)) {
      save(androidModuleStrings(appTitle!!), resolve("strings.xml"))
      // Common themes.xml isn't needed for Compose because theme is created in Composable.
      if (themesXml != null && data.category != Category.Compose) {
        save(themesXml, resolve("themes.xml"))
      }
      if (colorsXml != null) {
        save(colorsXml, resolve("colors.xml"))
      }
    }
    themesXmlNight?.let {
      // Common themes.xml isn't needed for Compose because theme is created in Composable.
      if (data.category != Category.Compose) {
        save(it, resOut.resolve(SdkConstants.FD_RES_VALUES_NIGHT).resolve("themes.xml"))
      }
    }
  }
}
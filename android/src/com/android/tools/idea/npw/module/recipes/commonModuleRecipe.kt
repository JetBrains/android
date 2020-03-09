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
import com.android.tools.idea.npw.module.recipes.androidModule.cMakeListsTxt
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleColors
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStrings
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStyles

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
  generateTests: Boolean = false,
  includeCppSupport: Boolean = false,
  iconsGenerationStyle: IconsGenerationStyle = IconsGenerationStyle.ALL,
  stylesXml: String? = androidModuleStyles(),
  colorsXml: String? = androidModuleColors(),
  cppFlags: String = "",
  addLintOptions: Boolean = false
  ) {
  val (projectData, srcOut, resOut, manifestOut, testOut, unitTestOut, _, moduleOut) = data
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
      useKts,
      isLibraryProject,
      data.isDynamic,
      packageName,
      apis.buildApi.apiString,
      projectData.buildToolsVersion,
      minApi.apiString,
      apis.targetApi.apiString,
      useAndroidX,
      agpVersion,
      includeCppSupport,
      cppFlags,
      hasTests = generateTests,
      formFactorNames = projectData.includedFormFactorNames,
      addLintOptions = addLintOptions
    ),
    moduleOut.resolve(buildFile)
  )

  // Note: com.android.* needs to be applied before kotlin
  when {
    isLibraryProject -> applyPlugin("com.android.library")
    data.isDynamic -> applyPlugin("com.android.dynamic-feature")
    else -> applyPlugin("com.android.application")
  }
  addKotlinIfNeeded(projectData)

  save(manifestXml, manifestOut.resolve(FN_ANDROID_MANIFEST_XML))
  save(gitignore(), moduleOut.resolve(".gitignore"))
  if (generateTests) {
    addTests(packageName, useAndroidX, isLibraryProject, testOut, unitTestOut, language)
    addTestDependencies(agpVersion)
  }
  proguardRecipe(moduleOut, data.isLibrary)

  if (!isLibraryProject) {
    when(iconsGenerationStyle) {
      IconsGenerationStyle.ALL -> copyIcons(resOut)
      IconsGenerationStyle.MIPMAP_ONLY -> copyMipmapFolder(resOut)
      IconsGenerationStyle.MIPMAP_SQUARE_ONLY -> copyMipmapFile(resOut, "ic_launcher.png")
      IconsGenerationStyle.NONE -> Unit
    }
    with(resOut.resolve(SdkConstants.FD_RES_VALUES)) {
      save(androidModuleStrings(appTitle!!), resolve("strings.xml"))
      if (stylesXml != null) {
        save(stylesXml, resolve("styles.xml"))
      }
      if (colorsXml != null) {
        save(colorsXml, resolve("colors.xml"))
      }
    }
  }

  if (includeCppSupport) {
    with(moduleOut.resolve("src/main/cpp")) {
      createDirectory(this)
      save(cMakeListsTxt(), resolve("CMakeLists.txt"))
    }
  }
}
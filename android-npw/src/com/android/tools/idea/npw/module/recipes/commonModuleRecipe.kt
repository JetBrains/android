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

import com.android.tools.idea.npw.module.recipes.androidModule.buildGradle
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleColors
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStrings
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleThemes
import com.android.tools.idea.templates.recipe.IconsGenerationStyle
import com.android.tools.idea.templates.recipe.RecipeUtils
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

fun RecipeExecutor.generateCommonModule(
  data: ModuleTemplateData,
  appTitle: String?, // may be null only for libraries
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
  noKtx: Boolean = false,
  appTitleResName: String = "app_name",
  hasCode: Boolean = true,
  hasCustomRenderer: Boolean = false,
  generateStandardFiles: Boolean = true,
) {
  val (projectData, _, _, _, _, _, _, moduleOut) = data
  val (useAndroidX, agpVersion) = projectData
  val dslLanguage = projectData.dslLanguage
  val isLibraryProject = data.isLibrary
  val apis = data.apis
  val minApi = apis.minApi

  if (generateStandardFiles) {
    RecipeUtils.generateCommonModuleFiles(
      executor = this,
      data = data,
      appTitle = appTitle,
      manifestXml = manifestXml,
      generateGenericLocalTests = generateGenericLocalTests,
      generateGenericInstrumentedTests = generateGenericInstrumentedTests,
      iconsGenerationStyle = iconsGenerationStyle,
      themesXml = themesXml,
      themesXmlNight = themesXmlNight,
      colorsXml = colorsXml,
      appTitleResName = appTitleResName,
      addLocalTests = { pkg, out, lang -> addLocalTests(pkg, out, lang) },
      addInstrumentedTests = { pkg, useAndroidX, isLib, out, lang -> addInstrumentedTests(pkg, useAndroidX, isLib, out, lang) },
      copyIcons = { out, minApi -> copyIcons(out, minApi) },
      copyMipmapFolder = { out -> copyMipmapFolder(out) },
      copyMipmapFile = { out, file -> copyMipmapFile(out, file) },
      gitignore = { gitignore() },
      androidModuleStrings = { name, title -> androidModuleStrings(name, title) },
    )
  }

  addIncludeToSettings(data.name)

  if (!hasCustomRenderer) {
    save(
      buildGradle(
        agpVersion,
        dslLanguage,
        isLibraryProject,
        data.isDynamic,
        applicationId = data.namespace,
        apis.buildApi,
        minApi,
        apis.targetApi,
        useAndroidX,
        hasTests = generateGenericLocalTests,
        addLintOptions = addLintOptions,
        enableCpp = enableCpp,
        cppStandard = cppStandard,
        hasCode = hasCode,
        kotlinSupport = projectData.kotlinSupport,
      ),
      moduleOut.resolve(dslLanguage.buildFileName),
    )
  }
  addCompileSdk(apis.buildApi, isDeclarative = dslLanguage.isDcl)

  // Note: com.android.* needs to be applied before kotlin
  val classpathModule = "com.android.tools.build:gradle"
  val version = projectData.agpVersion.toString()

  if (!dslLanguage.isDcl) {
    when {
      isLibraryProject -> addPlugin("com.android.library", classpathModule, version)
      data.isDynamic -> addPlugin("com.android.dynamic-feature", classpathModule, version)
      else -> addPlugin("com.android.application", classpathModule, version)
    }
    if (hasCode) {
      addKotlinIfNeeded(projectData, targetApi = apis.targetApi.apiLevel, noKtx = noKtx)
      setJavaKotlinCompileOptions(data.projectTemplateData.language == Language.Kotlin)
    }
  }

  if (generateGenericLocalTests) {
    addTestDependencies()
  }
  if (generateGenericInstrumentedTests) {
    addTestDependencies()
  }
  proguardRecipe(moduleOut, agpVersion, data.isLibrary)
}

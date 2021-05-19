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

import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleThemes
import com.android.tools.idea.npw.module.recipes.androidModule.res.values_night.androidModuleThemes as androidModuleThemesNight
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.npw.module.recipes.generateCommonModule
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.has

fun RecipeExecutor.generateAndroidModule(
  data: ModuleTemplateData,
  appTitle: String?, // may be null only for libraries
  useKts: Boolean,
  bytecodeLevel: BytecodeLevel,
  enableCpp: Boolean = false,
  cppStandard: CppStandardType = CppStandardType.`Toolchain Default`
) {
  val useAndroidX = data.projectTemplateData.androidXSupport
  generateCommonModule(
    data = data,
    appTitle = appTitle,
    useKts = useKts,
    manifestXml = generateManifest(
      packageName = data.packageName,
      hasApplicationBlock = !data.isLibrary,
      theme = "@style/${data.themesData.main.name}"
    ),
    generateTests= true,
    themesXml = androidModuleThemes(useAndroidX, data.themesData.main.name),
    themesXmlNight = androidModuleThemesNight(useAndroidX, data.themesData.main.name),
    enableCpp = enableCpp,
    cppStandard = cppStandard
  )
  val projectData = data.projectTemplateData
  val formFactorNames = projectData.includedFormFactorNames
  requireJavaVersion(bytecodeLevel.versionString, data.projectTemplateData.language == Language.Kotlin)
  addDependency("com.android.support:appcompat-v7:${data.apis.appCompatVersion}.+")

  if (data.projectTemplateData.androidXSupport) {
    // Though addDependency should not be called from a module recipe, adding this library because it's used for the default theme
    // (Theme.MaterialComponents.DayNight)
    addDependency("com.google.android.material:material:+")
  }
  // TODO(qumeric): currently only works for a new project
  if (formFactorNames.has(FormFactor.Mobile) && formFactorNames.has(FormFactor.Wear)) {
    addDependency("com.google.android.gms:play-services-wearable:+", "compile")
  }
}

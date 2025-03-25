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
package com.android.tools.idea.npw.module.recipes.xrModule

import com.android.tools.idea.npw.module.recipes.androidModule.getBackupRules
import com.android.tools.idea.npw.module.recipes.androidModule.getDataExtractionRules
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleThemesMaterial3
import com.android.tools.idea.npw.module.recipes.androidModule.res.values_night.androidModuleThemesMaterial3 as androidModuleThemesNightMaterial3
import com.android.tools.idea.npw.module.recipes.generateCommonModule
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

fun RecipeExecutor.generateXRModule(
  data: ModuleTemplateData,
  appTitle: String,
  useKts: Boolean,
  enableCpp: Boolean = false,
  cppStandard: CppStandardType = CppStandardType.`Toolchain Default`,
  useVersionCatalog: Boolean = true,
) {
  val addBackupRules = data.projectTemplateData.isNewProject && data.apis.targetApi.api >= 31
  check(data.category != Category.Compose || data.isCompose) {
    "Template in Compose category must have isCompose set"
  }
  generateCommonModule(
    data = data,
    appTitle = appTitle,
    useKts = useKts,
    manifestXml =
      generateManifest(
        hasApplicationBlock = !data.isLibrary,
        theme = "@style/${data.themesData.main.name}",
        addBackupRules = addBackupRules,
      ),
    generateGenericLocalTests = data.useGenericLocalTests,
    generateGenericInstrumentedTests = data.useGenericInstrumentedTests,
    themesXml = androidModuleThemesMaterial3(data.themesData.main.name),
    themesXmlNight = androidModuleThemesNightMaterial3(data.themesData.main.name),
    colorsXml = null,
    enableCpp = enableCpp,
    cppStandard = cppStandard,
    useVersionCatalog = useVersionCatalog,
  )

  if (addBackupRules) {
    save(getBackupRules(), data.resDir.resolve("xml/backup_rules.xml"))
    save(getDataExtractionRules(), data.resDir.resolve("xml/data_extraction_rules.xml"))
  }
}

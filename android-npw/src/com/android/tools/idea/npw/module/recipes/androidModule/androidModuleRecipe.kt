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

import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleColors
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleColorsMaterial3
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleThemes
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleThemesMaterial3
import com.android.tools.idea.npw.module.recipes.generateCommonModule
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.wizard.template.BytecodeLevel
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CppStandardType
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.has
import com.android.tools.idea.npw.module.recipes.androidModule.res.values_night.androidModuleThemes as androidModuleThemesNight
import com.android.tools.idea.npw.module.recipes.androidModule.res.values_night.androidModuleThemesMaterial3 as androidModuleThemesNightMaterial3

fun RecipeExecutor.generateAndroidModule(
  data: ModuleTemplateData,
  appTitle: String?, // may be null only for libraries
  useKts: Boolean,
  bytecodeLevel: BytecodeLevel,
  enableCpp: Boolean = false,
  cppStandard: CppStandardType = CppStandardType.`Toolchain Default`
) {
  val useAndroidX = data.projectTemplateData.androidXSupport
  val addBackupRules = data.projectTemplateData.isNewProject && data.apis.targetApi.api >= 31
  val isMaterial3 = data.isMaterial3
  generateCommonModule(
    data = data,
    appTitle = appTitle,
    useKts = useKts,
    manifestXml = generateManifest(
      hasApplicationBlock = !data.isLibrary,
      theme = "@style/${data.themesData.main.name}",
      addBackupRules = addBackupRules
    ),
    generateGenericLocalTests = data.useGenericLocalTests,
    generateGenericInstrumentedTests = data.useGenericInstrumentedTests,
    themesXml = if (isMaterial3)
      androidModuleThemesMaterial3(data.themesData.main.name)
    else
      androidModuleThemes(useAndroidX, data.apis.minApi, data.themesData.main.name),
    themesXmlNight = if (isMaterial3)
      androidModuleThemesNightMaterial3(data.themesData.main.name)
    else
      androidModuleThemesNight(useAndroidX, data.apis.minApi, data.themesData.main.name),
    colorsXml = if (isMaterial3 && data.category != Category.Compose) androidModuleColorsMaterial3() else androidModuleColors(),
    enableCpp = enableCpp,
    cppStandard = cppStandard,
    bytecodeLevel = bytecodeLevel,
  )
  val projectData = data.projectTemplateData
  val formFactorNames = projectData.includedFormFactorNames
  if (data.category != Category.Compose) {
    addDependency("com.android.support:appcompat-v7:${data.apis.appCompatVersion}.+")
  }

  if (data.projectTemplateData.androidXSupport && data.category != Category.Compose && !isMaterial3) {
    // Though addDependency should not be called from a module recipe, adding this library because it's used for the default theme
    // (Theme.MaterialComponents.DayNight)
    addDependency("com.google.android.material:material:+")
  }
  // TODO(qumeric): currently only works for a new project
  if (formFactorNames.has(FormFactor.Mobile) && formFactorNames.has(FormFactor.Wear)) {
    addDependency("com.google.android.gms:play-services-wearable:+", "compile")
  }

  if (addBackupRules) {
    save(getBackupRules(), data.resDir.resolve("xml/backup_rules.xml"))
    save(getDataExtractionRules(), data.resDir.resolve("xml/data_extraction_rules.xml"))
  }
}

private fun getBackupRules() = """
<?xml version="1.0" encoding="utf-8"?>
<!--
   Sample backup rules file; uncomment and customize as necessary.
   See https://developer.android.com/guide/topics/data/autobackup
   for details.
   Note: This file is ignored for devices older that API 31
   See https://developer.android.com/about/versions/12/backup-restore
-->
<full-backup-content>
<!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>
"""

private fun getDataExtractionRules() = """
<?xml version="1.0" encoding="utf-8"?>
<!--
   Sample data extraction rules file; uncomment and customize as necessary.
   See https://developer.android.com/about/versions/12/backup-restore#xml-changes
   for details.
-->
<data-extraction-rules>
    <cloud-backup>
        <!-- TODO: Use <include> and <exclude> to control what is backed up.
        <include .../>
        <exclude .../>
        -->
    </cloud-backup>
    <!--
    <device-transfer>
        <include .../>
        <exclude .../>
    </device-transfer>
    -->
</data-extraction-rules>
"""
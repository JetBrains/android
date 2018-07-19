/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.modules

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.*
import com.android.tools.idea.gradle.structure.configurables.ui.modules.ModulePanel
import com.android.tools.idea.gradle.structure.model.android.AndroidModuleDescriptors
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModuleDefaultConfigDescriptors

class AndroidModuleRootConfigurable(context: PsContext, module: PsAndroidModule) : AbstractModuleConfigurable<ModulePanel>(context, module) {

  private val signingConfigsModel = createSigningConfigsModel(module)

  override fun getId() = "android.psd.modules." + displayName
  override fun createPanel(module: PsAndroidModule) =
      ModulePanel(context, module, signingConfigsModel)
}

fun androidModulePropertiesModel() =
  PropertiesUiModel(
    listOf(
      uiProperty(AndroidModuleDescriptors.compileSdkVersion, ::simplePropertyEditor),
      uiProperty(AndroidModuleDescriptors.buildToolsVersion, ::simplePropertyEditor),
      uiProperty(AndroidModuleDescriptors.sourceCompatibility, ::simplePropertyEditor),
      uiProperty(AndroidModuleDescriptors.targetCompatibility, ::simplePropertyEditor)))

fun defaultConfigPropertiesModel(isLibrary: Boolean) =
  PropertiesUiModel(
    listOfNotNull(
      if (!isLibrary) uiProperty(PsAndroidModuleDefaultConfigDescriptors.applicationId, ::simplePropertyEditor) else null,
      if (!isLibrary) uiProperty(PsAndroidModuleDefaultConfigDescriptors.applicationIdSuffix, ::simplePropertyEditor) else null,
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.targetSdkVersion, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.minSdkVersion, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.maxSdkVersion, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.signingConfig, ::simplePropertyEditor),
      if (isLibrary) uiProperty(PsAndroidModuleDefaultConfigDescriptors.consumerProGuardFiles, ::listPropertyEditor) else null,
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.proGuardFiles, ::listPropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.manifestPlaceholders, ::mapPropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.multiDexEnabled, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunner, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testInstrumentationRunnerArguments, ::mapPropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testFunctionalTest, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testHandleProfiling, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.testApplicationId, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionCode, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionName, ::simplePropertyEditor),
      uiProperty(PsAndroidModuleDefaultConfigDescriptors.versionNameSuffix, ::simplePropertyEditor)))


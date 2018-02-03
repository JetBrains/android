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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.ProductFlavor
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.model.helpers.*
import com.android.tools.idea.gradle.structure.model.meta.*
import java.io.File

object PsAndroidModuleDefaultConfigDescriptors : ModelDescriptor<PsAndroidModuleDefaultConfig, ProductFlavor, ProductFlavorModel> {
  override fun getResolved(model: PsAndroidModuleDefaultConfig): ProductFlavor? =
      model.module.gradleModel.androidProject.defaultConfig.productFlavor

  override fun getParsed(model: PsAndroidModuleDefaultConfig): ProductFlavorModel? =
      model.module.parsedModel?.android()?.defaultConfig()

  override fun setModified(model: PsAndroidModuleDefaultConfig) {
    model.module.isModified = true
  }

  val applicationId: ModelSimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
      "Application ID",
      getResolvedValue = { applicationId },
      getParsedValue = { applicationId().asString() },
      getParsedRawValue = { applicationId().dslText() },
      setParsedValue = { applicationId().setValue(it) },
      clearParsedValue = { applicationId().clear() },
      setParsedRawValue = { applicationId().setDslText(it) },
      parse = { parseString(it) }
  )

  val maxSdkVersion: ModelSimpleProperty<PsAndroidModuleDefaultConfig, Int> = property(
      "Max SDK Version",
      getResolvedValue = { maxSdkVersion },
      getParsedValue = { maxSdkVersion().asInt() },
      getParsedRawValue = { maxSdkVersion().dslText() },
      setParsedValue = { maxSdkVersion().setValue(it) },
      clearParsedValue = { maxSdkVersion().clear() },
      setParsedRawValue = { maxSdkVersion().setDslText(it) },
      parse = { parseInt(it) },
      getKnownValues = { installedSdksAsInts() }
  )

  val minSdkVersion: ModelSimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
      "Min SDK Version",
      getResolvedValue = { minSdkVersion?.apiLevel?.toString() },
      getParsedValue = { minSdkVersion().asString() },
      getParsedRawValue = { minSdkVersion().dslText() },
      setParsedValue = { minSdkVersion().setValue(it) },
      clearParsedValue = { minSdkVersion().clear() },
      setParsedRawValue = { minSdkVersion().setDslText(it) },
      parse = { parseString(it) },
      getKnownValues = { installedSdksAsStrings() }
  )

  val multiDexEnabled: ModelSimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
      "Multi Dex Enabled",
      getResolvedValue = { multiDexEnabled },
      getParsedValue = { multiDexEnabled().asBoolean() },
      getParsedRawValue = { multiDexEnabled().dslText() },
      setParsedValue = { multiDexEnabled().setValue(it) },
      clearParsedValue = { multiDexEnabled().clear() },
      setParsedRawValue = { multiDexEnabled().setDslText(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
  )

  val targetSdkVersion: ModelSimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
      "Target SDK Version",
      getResolvedValue = { targetSdkVersion?.apiLevel?.toString() },
      getParsedValue = { targetSdkVersion().asString() },
      getParsedRawValue = { targetSdkVersion().dslText() },
      setParsedValue = { targetSdkVersion().setValue(it) },
      clearParsedValue = { targetSdkVersion().clear() },
      setParsedRawValue = { targetSdkVersion().setDslText(it) },
      parse = { parseString(it) },
      getKnownValues = { installedSdksAsStrings() }

  )

  val testApplicationId: ModelSimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
      "Test Application ID",
      getResolvedValue = { testApplicationId },
      getParsedValue = { testApplicationId().asString() },
      getParsedRawValue = { testApplicationId().dslText() },
      setParsedValue = { testApplicationId().setValue(it) },
      clearParsedValue = { testApplicationId().clear() },
      setParsedRawValue = { testApplicationId().setDslText(it) },
      parse = { parseString(it) }
  )

  val testFunctionalTest: ModelSimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
      "Test Functional Test",
      getResolvedValue = { testFunctionalTest },
      getParsedValue = { testFunctionalTest().asBoolean() },
      getParsedRawValue = { testFunctionalTest().dslText() },
      setParsedValue = { testFunctionalTest().setValue(it) },
      clearParsedValue = { testFunctionalTest().clear() },
      setParsedRawValue = { testFunctionalTest().setDslText(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
  )

  val testHandleProfiling: ModelSimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
      "Test Handle Profiling",
      getResolvedValue = { testHandleProfiling },
      getParsedValue = { testHandleProfiling().asBoolean() },
      getParsedRawValue = { testHandleProfiling().dslText() },
      setParsedValue = { testHandleProfiling().setValue(it) },
      clearParsedValue = { testHandleProfiling().clear() },
      setParsedRawValue = { testHandleProfiling().setDslText(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
  )

  val testInstrumentationRunner: ModelSimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
      "Test instrumentation runner class name",
      getResolvedValue = { testInstrumentationRunner },
      getParsedValue = { testInstrumentationRunner().asString() },
      getParsedRawValue = { testInstrumentationRunner().dslText() },
      setParsedValue = { testInstrumentationRunner().setValue(it) },
      clearParsedValue = { testInstrumentationRunner().clear() },
      setParsedRawValue = { testInstrumentationRunner().setDslText(it) },
      parse = { parseString(it) }
  )

  val versionCode: ModelSimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
      "Version Code",
      getResolvedValue = { versionCode?.toString() },
      getParsedValue = { versionCode().asString() },
      getParsedRawValue = { versionCode().dslText() },
      setParsedValue = { versionCode().setValue(it) },
      clearParsedValue = { versionCode().clear() },
      setParsedRawValue = { versionCode().setDslText(it) },
      parse = { parseString(it) }
  )

  val versionName: ModelSimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
      "Version Name",
      getResolvedValue = { versionName },
      getParsedValue = { versionName().asString() },
      getParsedRawValue = { versionName().dslText() },
      setParsedValue = { versionName().setValue(it) },
      clearParsedValue = { versionName().clear() },
      setParsedRawValue = { versionName().setDslText(it) },
      parse = { parseString(it) }
  )

  val proGuardFiles: ModelListProperty<PsAndroidModuleDefaultConfig, File> = listProperty(
    "Proguard Files",
    getResolvedValue = { proguardFiles.toList() },
    getParsedCollection = { proguardFiles().asParsedListValue(ResolvedPropertyModel::asFile, { setValue(it.toString()) }) },
    getParsedRawValue = { proguardFiles().dslText() },
    clearParsedValue = { proguardFiles().delete() },
    setParsedRawValue = { proguardFiles().setDslText(it) },
    parse = { parseFile(it) }
  )
}

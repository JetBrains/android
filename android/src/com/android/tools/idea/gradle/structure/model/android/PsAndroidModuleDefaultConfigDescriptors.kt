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

  val applicationId: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Application ID",
    getResolvedValue = { applicationId },
    getParsedProperty = { applicationId() },
    getter = { asString() },
    setter = { setValue(it) },
    parse = ::parseString
  )

  val maxSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, Int> = property(
    "Max SDK Version",
    getResolvedValue = { maxSdkVersion },
    getParsedProperty = { maxSdkVersion() },
    getter = { asInt() },
    setter = { setValue(it) },
    parse = ::parseInt,
    getKnownValues = ::installedSdksAsInts
  )

  val minSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Min SDK Version",
    getResolvedValue = { minSdkVersion?.apiLevel?.toString() },
    getParsedProperty = { minSdkVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parse = ::parseString,
    getKnownValues = ::installedSdksAsStrings
  )

  val multiDexEnabled: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Multi Dex Enabled",
    getResolvedValue = { multiDexEnabled },
    getParsedProperty = { multiDexEnabled() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parse = ::parseBoolean,
    getKnownValues = ::booleanValues
  )

  val targetSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Target SDK Version",
    getResolvedValue = { targetSdkVersion?.apiLevel?.toString() },
    getParsedProperty = { targetSdkVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parse = ::parseString,
    getKnownValues = ::installedSdksAsStrings

  )

  val testApplicationId: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Test Application ID",
    getResolvedValue = { testApplicationId },
    getParsedProperty = { testApplicationId() },
    getter = { asString() },
    setter = { setValue(it) },
    parse = ::parseString
  )

  val testFunctionalTest: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Test Functional Test",
    getResolvedValue = { testFunctionalTest },
    getParsedProperty = { testFunctionalTest() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parse = ::parseBoolean,
    getKnownValues = ::booleanValues
  )

  val testHandleProfiling: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Test Handle Profiling",
    getResolvedValue = { testHandleProfiling },
    getParsedProperty = { testHandleProfiling() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parse = ::parseBoolean,
    getKnownValues = ::booleanValues
  )

  val testInstrumentationRunner: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Test instrumentation runner class name",
    getResolvedValue = { testInstrumentationRunner },
    getParsedProperty = { testInstrumentationRunner() },
    getter = { asString() },
    setter = { setValue(it) },
    parse = ::parseString
  )

  val versionCode: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Version Code",
    getResolvedValue = { versionCode?.toString() },
    getParsedProperty = { versionCode() },
    getter = { asString() },
    setter = { setValue(it) },
    parse = ::parseString
  )

  val versionName: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Version Name",
    getResolvedValue = { versionName },
    getParsedProperty = { versionName() },
    getter = { asString() },
    setter = { setValue(it) },
    parse = ::parseString
  )

  val proGuardFiles: ListProperty<PsAndroidModuleDefaultConfig, File> = listProperty(
    "Proguard Files",
    getResolvedValue = { proguardFiles.toList() },
    getParsedProperty = { proguardFiles() },
    itemValueGetter = { asFile() },
    itemValueSetter = { setValue(it.toString()) },
    parse = ::parseFile
  )

  val manifestPlaceholders: MapProperty<PsAndroidModuleDefaultConfig, String> = mapProperty(
    "Manifest Placeholders",
    getResolvedValue = { manifestPlaceholders.mapValues { it.value.toString() } },
    getParsedProperty = { manifestPlaceholders() },
    itemValueGetter = { asString() },
    itemValueSetter = { setValue(it) },
    parse = ::parseString
  )

  val testInstrumentationRunnerArguments: MapProperty<PsAndroidModuleDefaultConfig, String> = mapProperty(
    "Test Instrumentation Runner Arguments",
    getResolvedValue = { testInstrumentationRunnerArguments },
    getParsedProperty = { testInstrumentationRunnerArguments() },
    itemValueGetter = { asString() },
    itemValueSetter = { setValue(it) },
    parse = ::parseString
  )
}

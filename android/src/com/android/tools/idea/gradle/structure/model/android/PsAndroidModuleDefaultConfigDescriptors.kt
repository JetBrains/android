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
    model.module.resolvedModel?.androidProject?.defaultConfig?.productFlavor

  override fun getParsed(model: PsAndroidModuleDefaultConfig): ProductFlavorModel? =
    model.module.parsedModel?.android()?.defaultConfig()

  override fun setModified(model: PsAndroidModuleDefaultConfig) {
    model.module.isModified = true
  }

  val applicationId: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Application ID",
    resolvedValueGetter = { applicationId },
    parsedPropertyGetter = { applicationId() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val applicationIdSuffix: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Application Id Suffix",
    resolvedValueGetter = { applicationIdSuffix },
    parsedPropertyGetter = { applicationIdSuffix() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val maxSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, Int> = property(
    "Max SDK Version",
    resolvedValueGetter = { maxSdkVersion },
    parsedPropertyGetter = { maxSdkVersion() },
    getter = { asInt() },
    setter = { setValue(it) },
    parser = ::parseInt,
    knownValuesGetter = ::installedSdksAsInts
  )

  val minSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Min SDK Version",
    resolvedValueGetter = { minSdkVersion?.apiLevel?.toString() },
    parsedPropertyGetter = { minSdkVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString,
    knownValuesGetter = ::installedSdksAsStrings
  )

  val multiDexEnabled: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Multi Dex Enabled",
    resolvedValueGetter = { multiDexEnabled },
    parsedPropertyGetter = { multiDexEnabled() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val signingConfig: SimpleProperty<PsAndroidModuleDefaultConfig, Unit> = property(
    "Signing Config",
    resolvedValueGetter = { null },
    parsedPropertyGetter = { signingConfig() },
    getter = { asUnit() },
    setter = {},
    parser = ::parseReferenceOnly,
    formatter = ::formatUnit,
    knownValuesGetter = { model -> signingConfigs(model.module) }
  )

  val targetSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Target SDK Version",
    resolvedValueGetter = { targetSdkVersion?.apiLevel?.toString() },
    parsedPropertyGetter = { targetSdkVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString,
    knownValuesGetter = ::installedSdksAsStrings

  )

  val testApplicationId: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Test Application ID",
    resolvedValueGetter = { testApplicationId },
    parsedPropertyGetter = { testApplicationId() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val testFunctionalTest: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Test Functional Test",
    // TODO(b/111630584): Replace with the resolved value.
    resolvedValueGetter = { null },
    parsedPropertyGetter = { testFunctionalTest() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val testHandleProfiling: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Test Handle Profiling",
    // TODO(b/111630584): Replace with the resolved value.
    resolvedValueGetter = { null },
    parsedPropertyGetter = { testHandleProfiling() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val testInstrumentationRunner: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Test instrumentation runner class name",
    resolvedValueGetter = { testInstrumentationRunner },
    parsedPropertyGetter = { testInstrumentationRunner() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val versionCode: SimpleProperty<PsAndroidModuleDefaultConfig, Int> = property(
    "Version Code",
    resolvedValueGetter = { versionCode },
    parsedPropertyGetter = { versionCode() },
    getter = { asInt() },
    setter = { setValue(it) },
    parser = ::parseInt
  )

  val versionName: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Version Name",
    resolvedValueGetter = { versionName },
    parsedPropertyGetter = { versionName() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val versionNameSuffix: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Version Name Suffix",
    resolvedValueGetter = { versionNameSuffix },
    parsedPropertyGetter = { versionNameSuffix() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val consumerProGuardFiles: ListProperty<PsAndroidModuleDefaultConfig, File> = listProperty(
    "Consumer ProGuard Files",
    resolvedValueGetter = { consumerProguardFiles.toList() },
    parsedPropertyGetter = { consumerProguardFiles() },
    getter = { asFile() },
    setter = { setValue(it.toString()) },
    parser = ::parseFile,
    knownValuesGetter = { model -> proGuardFileValues(model.module) }
  )

  val proGuardFiles: ListProperty<PsAndroidModuleDefaultConfig, File> = listProperty(
    "ProGuard Files",
    resolvedValueGetter = { proguardFiles.toList() },
    parsedPropertyGetter = { proguardFiles() },
    getter = { asFile() },
    setter = { setValue(it.toString()) },
    parser = ::parseFile,
    knownValuesGetter = { model -> proGuardFileValues(model.module) }
  )

  val resConfigs: ListProperty<PsAndroidModuleDefaultConfig, String> = listProperty(
    "Resource Configs",
    resolvedValueGetter = { resourceConfigurations.toList() },
    parsedPropertyGetter = { resConfigs() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val manifestPlaceholders: MapProperty<PsAndroidModuleDefaultConfig, String> = mapProperty(
    "Manifest Placeholders",
    resolvedValueGetter = { manifestPlaceholders.mapValues { it.value.toString() } },
    parsedPropertyGetter = { manifestPlaceholders() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val testInstrumentationRunnerArguments: MapProperty<PsAndroidModuleDefaultConfig, String> = mapProperty(
    "Test Instrumentation Runner Arguments",
    resolvedValueGetter = { testInstrumentationRunnerArguments },
    parsedPropertyGetter = { testInstrumentationRunnerArguments() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )
}

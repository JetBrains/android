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

import com.android.tools.idea.gradle.model.IdeBaseConfig
import com.android.tools.idea.gradle.model.IdeProductFlavor
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.model.helpers.booleanValues
import com.android.tools.idea.gradle.structure.model.helpers.formatUnit
import com.android.tools.idea.gradle.structure.model.helpers.installedSdksAsInts
import com.android.tools.idea.gradle.structure.model.helpers.installedSdksAsStrings
import com.android.tools.idea.gradle.structure.model.helpers.parseAny
import com.android.tools.idea.gradle.structure.model.helpers.parseBoolean
import com.android.tools.idea.gradle.structure.model.helpers.parseFile
import com.android.tools.idea.gradle.structure.model.helpers.parseInt
import com.android.tools.idea.gradle.structure.model.helpers.parseReferenceOnly
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.helpers.proGuardFileValues
import com.android.tools.idea.gradle.structure.model.helpers.signingConfigs
import com.android.tools.idea.gradle.structure.model.helpers.toIntOrString
import com.android.tools.idea.gradle.structure.model.helpers.withProFileSelector
import com.android.tools.idea.gradle.structure.model.meta.ListProperty
import com.android.tools.idea.gradle.structure.model.meta.MapProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.SimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.asAny
import com.android.tools.idea.gradle.structure.model.meta.asBoolean
import com.android.tools.idea.gradle.structure.model.meta.asFile
import com.android.tools.idea.gradle.structure.model.meta.asInt
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.meta.asUnit
import com.android.tools.idea.gradle.structure.model.meta.listProperty
import com.android.tools.idea.gradle.structure.model.meta.mapProperty
import com.android.tools.idea.gradle.structure.model.meta.property
import java.io.File

object PsAndroidModuleDefaultConfigDescriptors : ModelDescriptor<PsAndroidModuleDefaultConfig, IdeProductFlavor, ProductFlavorModel> {
  override fun getResolved(model: PsAndroidModuleDefaultConfig): IdeProductFlavor? =
    model.module.resolvedModel?.androidProject?.multiVariantData?.defaultConfig

  override fun getParsed(model: PsAndroidModuleDefaultConfig): ProductFlavorModel? =
    model.module.parsedModel?.android()?.defaultConfig()

  override fun prepareForModification(model: PsAndroidModuleDefaultConfig) = Unit

  override fun setModified(model: PsAndroidModuleDefaultConfig) {
    model.module.isModified = true
  }

  val applicationId: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Application ID",
    preferredVariableName = { "defaultApplicationId" },
    resolvedValueGetter = { applicationId },
    parsedPropertyGetter = { applicationId() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val applicationIdSuffix: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Application ID Suffix",
    preferredVariableName = { "defaultApplicationIdSuffix" },
    resolvedValueGetter = { applicationIdSuffix },
    parsedPropertyGetter = { applicationIdSuffix() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val maxSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, Int> = property(
    "Max SDK Version",
    preferredVariableName = { "defaultMaxSdkVersion" },
    resolvedValueGetter = { maxSdkVersion },
    parsedPropertyGetter = { maxSdkVersion() },
    getter = { asInt() },
    setter = { setValue(it) },
    parser = ::parseInt,
    knownValuesGetter = ::installedSdksAsInts
  )

  val minSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Min SDK Version",
    preferredVariableName = { "defaultMinSdkVersion" },
    resolvedValueGetter = { minSdkVersion?.apiLevel?.toString() },
    parsedPropertyGetter = { minSdkVersion() },
    getter = { asString() },
    setter = { setValue(it.toIntOrString()) },
    parser = ::parseString,
    knownValuesGetter = ::installedSdksAsStrings
  )

  val multiDexEnabled: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Multi Dex Enabled",
    preferredVariableName = { "defaultMultiDexEnabled" },
    resolvedValueGetter = { multiDexEnabled },
    parsedPropertyGetter = { multiDexEnabled() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val signingConfig: SimpleProperty<PsAndroidModuleDefaultConfig, Unit> = property(
    "Signing Config",
    resolvedValueGetter = IdeBaseConfig::kotlinUnitWorkAround,
    parsedPropertyGetter = { signingConfig() },
    getter = ResolvedPropertyModel::asUnit,
    setter = {},
    parser = ::parseReferenceOnly,
    formatter = ::formatUnit,
    knownValuesGetter = { model -> signingConfigs(model.module) }
  )

  val targetSdkVersion: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Target SDK Version",
    preferredVariableName = { "defaultTargetSdkVersion" },
    resolvedValueGetter = { targetSdkVersion?.apiLevel?.toString() },
    parsedPropertyGetter = { targetSdkVersion() },
    getter = { asString() },
    setter = { setValue(it.toIntOrString()) },
    parser = ::parseString,
    knownValuesGetter = ::installedSdksAsStrings

  )

  val testApplicationId: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Test Application ID",
    preferredVariableName = { "defaultTestApplicationId" },
    resolvedValueGetter = { testApplicationId },
    parsedPropertyGetter = { testApplicationId() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val testFunctionalTest: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Test Functional Test",
    preferredVariableName = { "defaultTestFunctionalTest" },
    resolvedValueGetter = { testFunctionalTest },
    parsedPropertyGetter = { testFunctionalTest() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val testHandleProfiling: SimpleProperty<PsAndroidModuleDefaultConfig, Boolean> = property(
    "Test Handle Profiling",
    preferredVariableName = { "defaultTestHandleProfiling" },
    resolvedValueGetter = { testHandleProfiling },
    parsedPropertyGetter = { testHandleProfiling() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val testInstrumentationRunner: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Test instrumentation runner class name",
    preferredVariableName = { "defaultTestInstrumentationRunner" },
    resolvedValueGetter = { testInstrumentationRunner },
    parsedPropertyGetter = { testInstrumentationRunner() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val versionCode: SimpleProperty<PsAndroidModuleDefaultConfig, Int> = property(
    "Version Code",
    preferredVariableName = { "defaultVersionCode" },
    resolvedValueGetter = { versionCode },
    parsedPropertyGetter = { versionCode() },
    getter = { asInt() },
    setter = { setValue(it) },
    parser = ::parseInt
  )

  val versionName: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Version Name",
    preferredVariableName = { "defaultVersionName" },
    resolvedValueGetter = { versionName },
    parsedPropertyGetter = { versionName() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val versionNameSuffix: SimpleProperty<PsAndroidModuleDefaultConfig, String> = property(
    "Version Name Suffix",
    preferredVariableName = { "defaultVersionNameSuffix" },
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
    .withProFileSelector(module = { module })

  val proGuardFiles: ListProperty<PsAndroidModuleDefaultConfig, File> = listProperty(
    "ProGuard Files",
    resolvedValueGetter = { proguardFiles.toList() },
    parsedPropertyGetter = { proguardFiles() },
    getter = { asFile() },
    setter = { setValue(it.toString()) },
    parser = ::parseFile,
    knownValuesGetter = { model -> proGuardFileValues(model.module) }
  )
    .withProFileSelector(module = { module })

  val resConfigs: ListProperty<PsAndroidModuleDefaultConfig, String> = listProperty(
    "Resource Configs",
    resolvedValueGetter = { resourceConfigurations.toList() },
    parsedPropertyGetter = { resConfigs() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  val manifestPlaceholders: MapProperty<PsAndroidModuleDefaultConfig, Any> = mapProperty(
    "Manifest Placeholders",
    resolvedValueGetter = { manifestPlaceholders },
    parsedPropertyGetter = { manifestPlaceholders() },
    getter = { asAny() },
    setter = { setValue(it) },
    parser = ::parseAny
  )

  val testInstrumentationRunnerArguments: MapProperty<PsAndroidModuleDefaultConfig, String> = mapProperty(
    "Test Instrumentation Runner Arguments",
    resolvedValueGetter = { testInstrumentationRunnerArguments },
    parsedPropertyGetter = { testInstrumentationRunnerArguments() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString
  )

  override val properties: Collection<ModelProperty<PsAndroidModuleDefaultConfig, *, *, *>> =
    listOf(applicationId, applicationIdSuffix, maxSdkVersion, minSdkVersion, multiDexEnabled, signingConfig, targetSdkVersion,
           testApplicationId, testFunctionalTest, testHandleProfiling, testInstrumentationRunner, versionCode, versionName,
           versionNameSuffix, consumerProGuardFiles, proGuardFiles, resConfigs, manifestPlaceholders, testInstrumentationRunnerArguments)
}

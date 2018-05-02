// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.ProductFlavor
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.helpers.*
import com.android.tools.idea.gradle.structure.model.meta.*
import com.google.common.util.concurrent.Futures.immediateFuture
import java.io.File

open class PsProductFlavor(
  final override val parent: PsAndroidModule,
  final override val resolvedModel: ProductFlavor?,
  private val parsedModel: ProductFlavorModel?
) : PsChildModel(parent), PsAndroidModel {

  override val name = when {
    resolvedModel != null -> resolvedModel.name
    parsedModel != null -> parsedModel.name()
    else -> ""
  }

  var applicationId by ProductFlavorDescriptors.applicationId
  var dimension by ProductFlavorDescriptors.dimension
  var maxSdkVersion by ProductFlavorDescriptors.maxSdkVersion
  var minSdkVersion by ProductFlavorDescriptors.minSdkVersion
  var multiDexEnabled by ProductFlavorDescriptors.multiDexEnabled
  var targetSdkVersion by ProductFlavorDescriptors.targetSdkVersion
  var testApplicationId by ProductFlavorDescriptors.testApplicationId
  var testFunctionalTest by ProductFlavorDescriptors.testFunctionalTest
  var testHandleProfiling by ProductFlavorDescriptors.testHandleProfiling
  var testInstrumentationRunner by ProductFlavorDescriptors.testInstrumentationRunner
  var versionCode by ProductFlavorDescriptors.versionCode
  var versionName by ProductFlavorDescriptors.versionName
  var manifestPlaceholders by ProductFlavorDescriptors.manifestPlaceholders
  var testInstrumentationRunnerArguments by ProductFlavorDescriptors.testInstrumentationRunnerArguments

  override val isDeclared: Boolean get() = parsedModel != null
  override val gradleModel: AndroidModuleModel = parent.gradleModel

  object ProductFlavorDescriptors : ModelDescriptor<PsProductFlavor, ProductFlavor, ProductFlavorModel> {
    override fun getResolved(model: PsProductFlavor): ProductFlavor? = model.resolvedModel

    override fun getParsed(model: PsProductFlavor): ProductFlavorModel? = model.parsedModel

    override fun setModified(model: PsProductFlavor) {
      model.isModified = true
    }

    val applicationId: SimpleProperty<PsProductFlavor, String> = property(
      "Application ID",
      getResolvedValue = { applicationId },
      getParsedProperty = { applicationId() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val dimension: SimpleProperty<PsProductFlavor, String> = property(
      "Dimension",
      getResolvedValue = { dimension },
      getParsedProperty = { dimension() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString,
      getKnownValues = { _, model -> immediateFuture(model.parent.flavorDimensions.map { ValueDescriptor(it, it) }) }
    )

    val maxSdkVersion: SimpleProperty<PsProductFlavor, Int> = property(
      "Max SDK Version",
      getResolvedValue = { maxSdkVersion },
      getParsedProperty = { maxSdkVersion() },
      getter = { asInt() },
      setter = { setValue(it) },
      parse = ::parseInt,
      getKnownValues = ::installedSdksAsInts
    )

    val minSdkVersion: SimpleProperty<PsProductFlavor, String> = property(
      "Min SDK Version",
      getResolvedValue = { minSdkVersion?.apiLevel?.toString() },
      getParsedProperty = { minSdkVersion() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString,
      getKnownValues = ::installedSdksAsStrings
    )

    val multiDexEnabled: SimpleProperty<PsProductFlavor, Boolean> = property(
      "Multi Dex Enabled",
      getResolvedValue = { multiDexEnabled },
      getParsedProperty = { multiDexEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = ::parseBoolean,
      getKnownValues = ::booleanValues
    )

    val signingConfig: SimpleProperty<PsProductFlavor, Unit> = property(
      "Signing Config",
      getResolvedValue = { null },
      getParsedProperty = { signingConfig() },
      getter = { asUnit() },
      setter = {},
      parse = ::parseReferenceOnly,
      format = ::formatUnit,
      getKnownValues = { _, model -> signingConfigs(model.parent) }
    )

    val targetSdkVersion: SimpleProperty<PsProductFlavor, String> = property(
      "Target SDK Version",
      getResolvedValue = { targetSdkVersion?.apiLevel?.toString() },
      getParsedProperty = { targetSdkVersion() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString,
      getKnownValues = ::installedSdksAsStrings

    )

    val testApplicationId: SimpleProperty<PsProductFlavor, String> = property(
      "Test Application ID",
      getResolvedValue = { testApplicationId },
      getParsedProperty = { testApplicationId() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val testFunctionalTest: SimpleProperty<PsProductFlavor, Boolean> = property(
      "Test Functional Test",
      getResolvedValue = { testFunctionalTest },
      getParsedProperty = { testFunctionalTest() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = ::parseBoolean,
      getKnownValues = ::booleanValues
    )

    val testHandleProfiling: SimpleProperty<PsProductFlavor, Boolean> = property(
      "Test Handle Profiling",
      getResolvedValue = { testHandleProfiling },
      getParsedProperty = { testHandleProfiling() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = ::parseBoolean,
      getKnownValues = ::booleanValues
    )

    val testInstrumentationRunner: SimpleProperty<PsProductFlavor, String> = property(
      "Test instrumentation runner class name",
      getResolvedValue = { testInstrumentationRunner },
      getParsedProperty = { testInstrumentationRunner() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val versionCode: SimpleProperty<PsProductFlavor, String> = property(
      "Version Code",
      getResolvedValue = { versionCode?.toString() },
      getParsedProperty = { versionCode() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val versionName: SimpleProperty<PsProductFlavor, String> = property(
      "Version Name",
      getResolvedValue = { versionName },
      getParsedProperty = { versionName() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = ::parseString
    )

    val proGuardFiles: ListProperty<PsProductFlavor, File> = listProperty(
      "Proguard Files",
      getResolvedValue = { proguardFiles.toList() },
      getParsedProperty = { proguardFiles() },
      itemValueGetter = { asFile() },
      itemValueSetter = { setValue(it.toString()) },
      parse = ::parseFile
    )

    val manifestPlaceholders: MapProperty<PsProductFlavor, String> = mapProperty(
      "Manifest Placeholders",
      getResolvedValue = { manifestPlaceholders.mapValues { it.value.toString() } },
      getParsedProperty = { manifestPlaceholders() },
      itemValueGetter = { asString() },
      itemValueSetter = { setValue(it) },
      parse = ::parseString
    )

    val testInstrumentationRunnerArguments: MapProperty<PsProductFlavor, String> = mapProperty(
      "Test Instrumentation Runner Arguments",
      getResolvedValue = { testInstrumentationRunnerArguments },
      getParsedProperty = { testInstrumentationRunnerArguments() },
      itemValueGetter = { asString() },
      itemValueSetter = { setValue(it) },
      parse = ::parseString
    )
  }
}

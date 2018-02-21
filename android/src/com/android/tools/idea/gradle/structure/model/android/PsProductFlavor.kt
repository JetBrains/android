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
import java.io.File

open class PsProductFlavor(
  parent: PsAndroidModule,
  private val resolvedModel: ProductFlavor?,
  private val parsedModel: ProductFlavorModel?
) : PsChildModel(parent), PsAndroidModel {

  private var name = when {
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

  override fun getName(): String = name
  override fun getParent(): PsAndroidModule = super.getParent() as PsAndroidModule
  override fun isDeclared(): Boolean = parsedModel != null
  override fun getResolvedModel(): ProductFlavor? = resolvedModel
  override fun getGradleModel(): AndroidModuleModel = parent.gradleModel

  object ProductFlavorDescriptors : ModelDescriptor<PsProductFlavor, ProductFlavor, ProductFlavorModel> {
    override fun getResolved(model: PsProductFlavor): ProductFlavor? = model.resolvedModel

    override fun getParsed(model: PsProductFlavor): ProductFlavorModel? = model.parsedModel

    override fun setModified(model: PsProductFlavor) {
      model.isModified = true
    }

    val applicationId: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Application ID",
      getResolvedValue = { applicationId },
      getParsedProperty = { applicationId() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) }
    )

    val dimension: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Dimension",
      getResolvedValue = { dimension },
      getParsedProperty = { dimension() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) },
      getKnownValues = { it.parent.flavorDimensions.map { ValueDescriptor(it, it) } }
    )

    val maxSdkVersion: ModelSimpleProperty<PsProductFlavor, Int> = property(
      "Max SDK Version",
      getResolvedValue = { maxSdkVersion },
      getParsedProperty = { maxSdkVersion() },
      getter = { asInt() },
      setter = { setValue(it) },
      parse = { parseInt(it) },
      getKnownValues = { installedSdksAsInts() }
    )

    val minSdkVersion: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Min SDK Version",
      getResolvedValue = { minSdkVersion?.apiLevel?.toString() },
      getParsedProperty = { minSdkVersion() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) },
      getKnownValues = { installedSdksAsStrings() }
    )

    val multiDexEnabled: ModelSimpleProperty<PsProductFlavor, Boolean> = property(
      "Multi Dex Enabled",
      getResolvedValue = { multiDexEnabled },
      getParsedProperty = { multiDexEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val targetSdkVersion: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Target SDK Version",
      getResolvedValue = { targetSdkVersion?.apiLevel?.toString() },
      getParsedProperty = { targetSdkVersion() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) },
      getKnownValues = { installedSdksAsStrings() }

    )

    val testApplicationId: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Test Application ID",
      getResolvedValue = { testApplicationId },
      getParsedProperty = { testApplicationId() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) }
    )

    val testFunctionalTest: ModelSimpleProperty<PsProductFlavor, Boolean> = property(
      "Test Functional Test",
      getResolvedValue = { testFunctionalTest },
      getParsedProperty = { testFunctionalTest() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val testHandleProfiling: ModelSimpleProperty<PsProductFlavor, Boolean> = property(
      "Test Handle Profiling",
      getResolvedValue = { testHandleProfiling },
      getParsedProperty = { testHandleProfiling() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val testInstrumentationRunner: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Test instrumentation runner class name",
      getResolvedValue = { testInstrumentationRunner },
      getParsedProperty = { testInstrumentationRunner() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) }
    )

    val versionCode: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Version Code",
      getResolvedValue = { versionCode?.toString() },
      getParsedProperty = { versionCode() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) }
    )

    val versionName: ModelSimpleProperty<PsProductFlavor, String> = property(
      "Version Name",
      getResolvedValue = { versionName },
      getParsedProperty = { versionName() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) }
    )

    val proGuardFiles: ModelListProperty<PsProductFlavor, File> = listProperty(
      "Proguard Files",
      getResolvedValue = { proguardFiles.toList() },
      getParsedProperty = { proguardFiles() },
      itemValueGetter = { asFile() },
      itemValueSetter = { setValue(it.toString()) },
      parse = { parseFile(it) }
    )

    val manifestPlaceholders: ModelMapProperty<PsProductFlavor, String> = mapProperty(
      "Manifest Placeholders",
      getResolvedValue = { manifestPlaceholders.mapValues { it.value.toString() } },
      getParsedProperty = { manifestPlaceholders() },
      itemValueGetter = { asString() },
      itemValueSetter = { setValue(it) },
      parse = { parseString(it) }
    )

    val testInstrumentationRunnerArguments: ModelMapProperty<PsProductFlavor, String> = mapProperty(
      "Test Instrumentation Runner Arguments",
      getResolvedValue = { testInstrumentationRunnerArguments },
      getParsedProperty = { testInstrumentationRunnerArguments() },
      itemValueGetter = { asString() },
      itemValueSetter = { setValue(it) },
      parse = { parseString(it) }
    )
  }
}

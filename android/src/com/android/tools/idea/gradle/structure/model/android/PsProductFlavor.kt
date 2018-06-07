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
        getParsedValue = { applicationId().asString() },
        getParsedRawValue = { applicationId().dslText() },
        setParsedValue = { applicationId().setValue(it) },
        clearParsedValue = { applicationId().clear() },
        parse = { parseString(it) }
    )

    val dimension: ModelSimpleProperty<PsProductFlavor, String> = property(
        "Dimension",
        getResolvedValue = { dimension },
        getParsedValue = { dimension().asString() },
        getParsedRawValue = { dimension().dslText() },
        setParsedValue = { dimension().setValue(it) },
        clearParsedValue = { dimension().clear() },
        parse = { parseString(it) }
    )

    val maxSdkVersion: ModelSimpleProperty<PsProductFlavor, Int> = property(
        "Max SDK Version",
        getResolvedValue = { maxSdkVersion },
        getParsedValue = { maxSdkVersion().asInt() },
        getParsedRawValue = { maxSdkVersion().dslText() },
        setParsedValue = { maxSdkVersion().setValue(it) },
        clearParsedValue = { maxSdkVersion().clear() },
        parse = { parseInt(it) },
        getKnownValues = { installedSdksAsInts() }
    )

    val minSdkVersion: ModelSimpleProperty<PsProductFlavor, String> = property(
        "Min SDK Version",
        getResolvedValue = { minSdkVersion?.apiLevel?.toString() },
        getParsedValue = { minSdkVersion().asString() },
        getParsedRawValue = { minSdkVersion().dslText() },
        setParsedValue = { minSdkVersion().setValue(it) },
        clearParsedValue = { minSdkVersion().clear() },
        parse = { parseString(it) },
        getKnownValues = { installedSdksAsStrings() }
    )

    val multiDexEnabled: ModelSimpleProperty<PsProductFlavor, Boolean> = property(
        "Multi Dex Enabled",
        getResolvedValue = { multiDexEnabled },
        getParsedValue = { multiDexEnabled().asBoolean() },
        getParsedRawValue = { multiDexEnabled().dslText() },
        setParsedValue = { multiDexEnabled().setValue(it) },
        clearParsedValue = { multiDexEnabled().clear() },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )

    val targetSdkVersion: ModelSimpleProperty<PsProductFlavor, String> = property(
        "Target SDK Version",
        getResolvedValue = { targetSdkVersion?.apiLevel?.toString() },
        getParsedValue = { targetSdkVersion().asString() },
        getParsedRawValue = { targetSdkVersion().dslText() },
        setParsedValue = { targetSdkVersion().setValue(it) },
        clearParsedValue = { targetSdkVersion().clear() },
        parse = { parseString(it) },
        getKnownValues = { installedSdksAsStrings() }

    )

    val testApplicationId: ModelSimpleProperty<PsProductFlavor, String> = property(
        "Test Application ID",
        getResolvedValue = { testApplicationId },
        getParsedValue = { testApplicationId().asString() },
        getParsedRawValue = { testApplicationId().dslText() },
        setParsedValue = { testApplicationId().setValue(it) },
        clearParsedValue = { testApplicationId().clear() },
        parse = { parseString(it) }
    )

    val testFunctionalTest: ModelSimpleProperty<PsProductFlavor, Boolean> = property(
        "Test Functional Test",
        getResolvedValue = { testFunctionalTest },
        getParsedValue = { testFunctionalTest().asBoolean() },
        getParsedRawValue = { testFunctionalTest().dslText() },
        setParsedValue = { testFunctionalTest().setValue(it) },
        clearParsedValue = { testFunctionalTest().clear() },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )

    val testHandleProfiling: ModelSimpleProperty<PsProductFlavor, Boolean> = property(
        "Test Handle Profiling",
        getResolvedValue = { testHandleProfiling },
        getParsedValue = { testHandleProfiling().asBoolean() },
        getParsedRawValue = { testHandleProfiling().dslText() },
        setParsedValue = { testHandleProfiling().setValue(it) },
        clearParsedValue = { testHandleProfiling().clear() },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )

    val testInstrumentationRunner: ModelSimpleProperty<PsProductFlavor, String> = property(
        "Test instrumentation runner class name",
        getResolvedValue = { testInstrumentationRunner },
        getParsedValue = { testInstrumentationRunner().asString() },
        getParsedRawValue = { testInstrumentationRunner().dslText() },
        setParsedValue = { testInstrumentationRunner().setValue(it) },
        clearParsedValue = { testInstrumentationRunner().clear() },
        parse = { parseString(it) }
    )

    val versionCode: ModelSimpleProperty<PsProductFlavor, String> = property(
        "Version Code",
        getResolvedValue = { versionCode?.toString() },
        getParsedValue = { versionCode().asString() },
        getParsedRawValue = { versionCode().dslText() },
        setParsedValue = { versionCode().setValue(it) },
        clearParsedValue = { versionCode().clear() },
        parse = { parseString(it) }
    )

    val versionName: ModelSimpleProperty<PsProductFlavor, String> = property(
        "Version Name",
        getResolvedValue = { versionName },
        getParsedValue = { versionName().asString() },
        getParsedRawValue = { versionName().dslText() },
        setParsedValue = { versionName().setValue(it) },
        clearParsedValue = { versionName().clear() },
        parse = { parseString(it) }
    )
  }
}


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

import com.android.tools.idea.gradle.model.IdeBaseConfig
import com.android.tools.idea.gradle.model.IdeProductFlavor
import com.android.tools.idea.gradle.dsl.api.android.ProductFlavorModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.model.PsChildModel
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
import com.android.tools.idea.gradle.structure.model.helpers.productFlavorMatchingFallbackValues
import com.android.tools.idea.gradle.structure.model.helpers.signingConfigs
import com.android.tools.idea.gradle.structure.model.helpers.toIntOrString
import com.android.tools.idea.gradle.structure.model.helpers.withProFileSelector
import com.android.tools.idea.gradle.structure.model.meta.ListProperty
import com.android.tools.idea.gradle.structure.model.meta.MapProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.SimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.VariableMatchingStrategy
import com.android.tools.idea.gradle.structure.model.meta.asAny
import com.android.tools.idea.gradle.structure.model.meta.asBoolean
import com.android.tools.idea.gradle.structure.model.meta.asFile
import com.android.tools.idea.gradle.structure.model.meta.asInt
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.meta.asUnit
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.meta.listProperty
import com.android.tools.idea.gradle.structure.model.meta.mapProperty
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.android.tools.idea.gradle.structure.model.meta.property
import com.android.tools.idea.gradle.structure.navigation.PsProductFlavorNavigationPath
import com.google.common.base.CaseFormat
import com.google.common.util.concurrent.Futures.immediateFuture
import icons.StudioIcons.Misc.PRODUCT_FLAVOR
import java.io.File
import javax.swing.Icon

data class PsProductFlavorKey(val dimension: String, val name: String)

open class PsProductFlavor(
  final override val parent: PsAndroidModule,
  private val renamed: (PsProductFlavorKey, PsProductFlavorKey) -> Unit
) : PsChildModel() {
  override val descriptor by ProductFlavorDescriptors
  var resolvedModel: IdeProductFlavor? = null
  private var parsedModel: ProductFlavorModel? = null

  fun init(resolvedModel: IdeProductFlavor?, parsedModel: ProductFlavorModel?) {
    this.resolvedModel = resolvedModel
    this.parsedModel = parsedModel
  }

  override val name: String get() = resolvedModel?.name ?: parsedModel?.name() ?: ""
  override val path: PsProductFlavorNavigationPath get() = PsProductFlavorNavigationPath(parent.path.productFlavorsPath, name)

  /**
   * The dimension the product flavor belongs to, i.e. either the configured dimension or the default dimension.
   */
  val effectiveDimension: String? get() =
    (configuredDimension.maybeValue ?: parent.flavorDimensions.singleOrNull()?.name)
      ?.takeIf { parent.findFlavorDimension(it) != null }

  var applicationId by ProductFlavorDescriptors.applicationId
  var applicationIdSuffix by ProductFlavorDescriptors.applicationIdSuffix

  /**
   * The 'dimension' property. Note, for filtering and matching, [effectiveDimension] should be used.
   */
  var configuredDimension by ProductFlavorDescriptors.dimension

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
  var versionNameSuffix by ProductFlavorDescriptors.versionNameSuffix
  var resConfigs by ProductFlavorDescriptors.resConfigs
  var matchingFallbacks by ProductFlavorDescriptors.matchingFallbacks
  var consumerProguardFiles by ProductFlavorDescriptors.consumerProGuardFiles
  var proguardFiles by ProductFlavorDescriptors.proGuardFiles
  var manifestPlaceholders by ProductFlavorDescriptors.manifestPlaceholders
  var testInstrumentationRunnerArguments by ProductFlavorDescriptors.testInstrumentationRunnerArguments

  override val isDeclared: Boolean get() = parsedModel != null
  override val icon: Icon = PRODUCT_FLAVOR

  fun rename(newName: String) {
    val oldName = name
    parsedModel!!.rename(newName)
    renamed(PsProductFlavorKey(effectiveDimension.orEmpty(), oldName), PsProductFlavorKey(effectiveDimension.orEmpty(), newName))
  }

  object ProductFlavorDescriptors : ModelDescriptor<PsProductFlavor, IdeProductFlavor, ProductFlavorModel> {
    override fun getResolved(model: PsProductFlavor): IdeProductFlavor? = model.resolvedModel

    override fun getParsed(model: PsProductFlavor): ProductFlavorModel? = model.parsedModel

    override fun prepareForModification(model: PsProductFlavor) = Unit

    override fun setModified(model: PsProductFlavor) {
      model.isModified = true
    }

    private fun variableName(name: String): String = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name)

    val applicationId: SimpleProperty<PsProductFlavor, String> = property(
      "Application ID",
      preferredVariableName = { variableName("$name-application-id") },
      resolvedValueGetter = { applicationId },
      parsedPropertyGetter = { applicationId() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val applicationIdSuffix: SimpleProperty<PsProductFlavor, String> = property(
      "Application ID Suffix",
      preferredVariableName = { variableName("$name-application-id-suffix") },
      resolvedValueGetter = { applicationIdSuffix },
      parsedPropertyGetter = { applicationIdSuffix() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val dimension: SimpleProperty<PsProductFlavor, String> = property(
      "Dimension",
      resolvedValueGetter = { dimension },
      parsedPropertyGetter = { dimension() },
      getter = { asString() },
      setter = { setValue(it) },
      refresher = { parent.resetProductFlavors() },
      parser = ::parseString,
      knownValuesGetter = { model -> immediateFuture(model.parent.flavorDimensions.map { ValueDescriptor(it.name, it.name) }) }
    )

    val maxSdkVersion: SimpleProperty<PsProductFlavor, Int> = property(
      "Max SDK Version",
      preferredVariableName = { variableName("$name-max-sdk-version") },
      resolvedValueGetter = { maxSdkVersion },
      parsedPropertyGetter = { maxSdkVersion() },
      getter = { asInt() },
      setter = { setValue(it) },
      parser = ::parseInt,
      knownValuesGetter = ::installedSdksAsInts
    )

    val minSdkVersion: SimpleProperty<PsProductFlavor, String> = property(
      "Min SDK Version",
      preferredVariableName = { variableName("$name-min-sdk-version") },
      resolvedValueGetter = { minSdkVersion?.apiLevel?.toString() },
      parsedPropertyGetter = { minSdkVersion() },
      getter = { asString() },
      setter = { setValue(it.toIntOrString()) },
      parser = ::parseString,
      knownValuesGetter = ::installedSdksAsStrings
    )

    val multiDexEnabled: SimpleProperty<PsProductFlavor, Boolean> = property(
      "Multi Dex Enabled",
      preferredVariableName = { variableName("$name-multi-dex-enabled") },
      resolvedValueGetter = { multiDexEnabled },
      parsedPropertyGetter = { multiDexEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val signingConfig: SimpleProperty<PsProductFlavor, Unit> = property(
      "Signing Config",
      resolvedValueGetter = IdeBaseConfig::kotlinUnitWorkAround,
      parsedPropertyGetter = { signingConfig() },
      getter = ResolvedPropertyModel::asUnit,
      setter = {},
      parser = ::parseReferenceOnly,
      formatter = ::formatUnit,
      knownValuesGetter = { model -> signingConfigs(model.parent) }
    )

    val targetSdkVersion: SimpleProperty<PsProductFlavor, String> = property(
      "Target SDK Version",
      preferredVariableName = { variableName("$name-target-sdk-version") },
      resolvedValueGetter = { targetSdkVersion?.apiLevel?.toString() },
      parsedPropertyGetter = { targetSdkVersion() },
      getter = { asString() },
      setter = { setValue(it.toIntOrString()) },
      parser = ::parseString,
      knownValuesGetter = ::installedSdksAsStrings

    )

    val testApplicationId: SimpleProperty<PsProductFlavor, String> = property(
      "Test Application ID",
      preferredVariableName = { variableName("$name-test-application-id") },
      resolvedValueGetter = { testApplicationId },
      parsedPropertyGetter = { testApplicationId() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val testFunctionalTest: SimpleProperty<PsProductFlavor, Boolean> = property(
      "Test Functional Test",
      preferredVariableName = { variableName("$name-test-functional-test") },
      resolvedValueGetter = { testFunctionalTest },
      parsedPropertyGetter = { testFunctionalTest() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val testHandleProfiling: SimpleProperty<PsProductFlavor, Boolean> = property(
      "Test Handle Profiling",
      preferredVariableName = { variableName("$name-test-handle-profiling") },
      resolvedValueGetter = { testHandleProfiling },
      parsedPropertyGetter = { testHandleProfiling() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val testInstrumentationRunner: SimpleProperty<PsProductFlavor, String> = property(
      "Test instrumentation runner class name",
      preferredVariableName = { variableName("$name-test-instrumentation-runner") },
      resolvedValueGetter = { testInstrumentationRunner },
      parsedPropertyGetter = { testInstrumentationRunner() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val versionCode: SimpleProperty<PsProductFlavor, Int> = property(
      "Version Code",
      preferredVariableName = { variableName("$name-version-code") },
      resolvedValueGetter = { versionCode },
      parsedPropertyGetter = { versionCode() },
      getter = { asInt() },
      setter = { setValue(it) },
      parser = ::parseInt
    )

    val versionName: SimpleProperty<PsProductFlavor, String> = property(
      "Version Name",
      preferredVariableName = { variableName("$name-version-name") },
      resolvedValueGetter = { versionName },
      parsedPropertyGetter = { versionName() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val versionNameSuffix: SimpleProperty<PsProductFlavor, String> = property(
      "Version Name Suffix",
      preferredVariableName = { variableName("$name-version-name-suffix") },
      resolvedValueGetter = { versionNameSuffix },
      parsedPropertyGetter = { versionNameSuffix() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val matchingFallbacks: ListProperty<PsProductFlavor, String> = listProperty(
      "Matching Fallbacks",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { matchingFallbacks() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString,
      variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE,
      knownValuesGetter = { model -> productFlavorMatchingFallbackValues(model.parent.parent, model.configuredDimension.maybeValue) }
    )

    val consumerProGuardFiles: ListProperty<PsProductFlavor, File> = listProperty(
      "Consumer ProGuard Files",
      resolvedValueGetter = { consumerProguardFiles.toList() },
      parsedPropertyGetter = { consumerProguardFiles() },
      getter = { asFile() },
      setter = { setValue(it.toString()) },
      parser = ::parseFile,
      knownValuesGetter = { model -> proGuardFileValues(model.parent) }
    )
      .withProFileSelector(module = { parent })

    val proGuardFiles: ListProperty<PsProductFlavor, File> = listProperty(
      "ProGuard Files",
      resolvedValueGetter = { proguardFiles.toList() },
      parsedPropertyGetter = { proguardFiles() },
      getter = { asFile() },
      setter = { setValue(it.toString()) },
      parser = ::parseFile,
      knownValuesGetter = { model -> proGuardFileValues(model.parent) }
    )
      .withProFileSelector(module = { parent })

    val resConfigs: ListProperty<PsProductFlavor, String> = listProperty(
      "Resource Configs",
      resolvedValueGetter = { resourceConfigurations.toList() },
      parsedPropertyGetter = { resConfigs() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val manifestPlaceholders: MapProperty<PsProductFlavor, Any> = mapProperty(
      "Manifest Placeholders",
      resolvedValueGetter = { manifestPlaceholders },
      parsedPropertyGetter = { manifestPlaceholders() },
      getter = { asAny() },
      setter = { setValue(it) },
      parser = ::parseAny
    )

    val testInstrumentationRunnerArguments: MapProperty<PsProductFlavor, String> = mapProperty(
      "Test Instrumentation Runner Arguments",
      resolvedValueGetter = { testInstrumentationRunnerArguments },
      parsedPropertyGetter = { testInstrumentationRunnerArguments() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    override val properties: Collection<ModelProperty<PsProductFlavor, *, *, *>> =
      listOf(applicationId, applicationIdSuffix, dimension, maxSdkVersion, minSdkVersion, multiDexEnabled, signingConfig, targetSdkVersion,
             testApplicationId, testFunctionalTest, testHandleProfiling, testInstrumentationRunner, versionCode, versionName,
             versionNameSuffix, matchingFallbacks, consumerProGuardFiles, proGuardFiles, resConfigs, manifestPlaceholders,
             testInstrumentationRunnerArguments)
  }
}

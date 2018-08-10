/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.builder.model.BuildType
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.helpers.*
import com.android.tools.idea.gradle.structure.model.meta.*
import java.io.File

private const val DEBUG_BUILD_TYPE_NAME = "debug"

open class PsBuildType(
  final override val parent: PsAndroidModule
) : PsChildModel() {

  var resolvedModel: BuildType? = null
  private var parsedModel: BuildTypeModel? = null

  constructor (parent: PsAndroidModule, resolvedModel: BuildType?, parsedModel: BuildTypeModel?) : this(parent) {
    init(resolvedModel, parsedModel)
  }

  fun init(resolvedModel: BuildType?, parsedModel: BuildTypeModel?) {
    this.resolvedModel = resolvedModel
    this.parsedModel = parsedModel
  }

  override val name get() = resolvedModel?.name ?: parsedModel?.name() ?: ""

  var applicationIdSuffix by BuildTypeDescriptors.applicationIdSuffix
  var embedMicroApp by BuildTypeDescriptors.embedMicroApp
  var jniDebuggable by BuildTypeDescriptors.jniDebuggable
  var minifyEnabled by BuildTypeDescriptors.minifyEnabled
  var pseudoLocalesEnabled by BuildTypeDescriptors.pseudoLocalesEnabled
  var renderscriptDebuggable by BuildTypeDescriptors.renderscriptDebuggable
  var renderscriptOptimLevel by BuildTypeDescriptors.renderscriptOptimLevel
  var testCoverageEnabled by BuildTypeDescriptors.testCoverageEnabled
  var versionNameSuffix by BuildTypeDescriptors.versionNameSuffix
  var zipAlignEnabled by BuildTypeDescriptors.zipAlignEnabled
  var multiDexEnabled by BuildTypeDescriptors.multiDexEnabled
  var debuggable by BuildTypeDescriptors.debuggable
  var proguardFiles by BuildTypeDescriptors.proGuardFiles
  var manifestPlaceholders by BuildTypeDescriptors.manifestPlaceholders

  override val isDeclared: Boolean get() = parsedModel != null

  fun ensureDeclared() {
    if (parsedModel == null) {
      parsedModel = parent.parsedModel!!.android().addBuildType(name)
      parent.isModified = true
    }
  }

  object BuildTypeDescriptors : ModelDescriptor<PsBuildType, BuildType, BuildTypeModel> {
    override fun getResolved(model: PsBuildType): BuildType? = model.resolvedModel

    override fun getParsed(model: PsBuildType): BuildTypeModel? = model.parsedModel

    override fun setModified(model: PsBuildType) {
      model.ensureDeclared()
      model.isModified = true
    }

    val applicationIdSuffix: SimpleProperty<PsBuildType, String> = property(
      "Application Id Suffix",
      resolvedValueGetter = { applicationIdSuffix },
      parsedPropertyGetter = { applicationIdSuffix() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val debuggable: SimpleProperty<PsBuildType, Boolean> = property(
      "Debuggable",
      // See: com.android.build.gradle.internal.dsl.BuildType#init
      defaultValueGetter = { it.name == DEBUG_BUILD_TYPE_NAME },
      resolvedValueGetter = { isDebuggable },
      parsedPropertyGetter = { debuggable() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val embedMicroApp: SimpleProperty<PsBuildType, Boolean> = property(
      "Embed Micro App",
      // See: com.android.build.gradle.internal.dsl.BuildType#init
      defaultValueGetter = { it.name != DEBUG_BUILD_TYPE_NAME },
      resolvedValueGetter = { isEmbedMicroApp },
      parsedPropertyGetter = { embedMicroApp() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val jniDebuggable: SimpleProperty<PsBuildType, Boolean> = property(
      "Jni Debuggable",
      defaultValueGetter = { false },
      resolvedValueGetter = { isJniDebuggable },
      parsedPropertyGetter = { jniDebuggable() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val minifyEnabled: SimpleProperty<PsBuildType, Boolean> = property(
      "Minify Enabled",
      defaultValueGetter = { false },
      resolvedValueGetter = { isMinifyEnabled },
      parsedPropertyGetter = { minifyEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val multiDexEnabled: SimpleProperty<PsBuildType, Boolean> = property(
      "Multi Dex Enabled",
      resolvedValueGetter = { multiDexEnabled },
      parsedPropertyGetter = { multiDexEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val pseudoLocalesEnabled: SimpleProperty<PsBuildType, Boolean> = property(
      "Pseudo Locales Enabled",
      defaultValueGetter = { false },
      resolvedValueGetter = { isPseudoLocalesEnabled },
      parsedPropertyGetter = { pseudoLocalesEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val renderscriptDebuggable: SimpleProperty<PsBuildType, Boolean> = property(
      "Renderscript Debuggable",
      defaultValueGetter = { false },
      resolvedValueGetter = { isRenderscriptDebuggable },
      parsedPropertyGetter = { renderscriptDebuggable() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val renderscriptOptimLevel: SimpleProperty<PsBuildType, Int> = property(
      "Renderscript optimization Level",
      defaultValueGetter = { 3 },
      resolvedValueGetter = { renderscriptOptimLevel },
      parsedPropertyGetter = { renderscriptOptimLevel() },
      getter = { asInt() },
      setter = { setValue(it) },
      parser = ::parseInt
    )

    val signingConfig: SimpleProperty<PsBuildType, Unit> = property(
      "Signing Config",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { signingConfig() },
      getter = { asUnit() },
      setter = {},
      parser = ::parseReferenceOnly,
      formatter = ::formatUnit,
      knownValuesGetter = { model -> signingConfigs(model.parent) }
    )

    val testCoverageEnabled: SimpleProperty<PsBuildType, Boolean> = property(
      "Test Coverage Enabled",
      defaultValueGetter = { false },
      resolvedValueGetter = { isTestCoverageEnabled },
      parsedPropertyGetter = { testCoverageEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val versionNameSuffix: SimpleProperty<PsBuildType, String> = property(
      "Version Name Suffix",
      resolvedValueGetter = { versionNameSuffix },
      parsedPropertyGetter = { versionNameSuffix() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val zipAlignEnabled: SimpleProperty<PsBuildType, Boolean> = property(
      "Zip Align Enabled",
      defaultValueGetter = { true },
      resolvedValueGetter = { isZipAlignEnabled },
      parsedPropertyGetter = { zipAlignEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val matchingFallbacks: ListProperty<PsBuildType, String> = listProperty(
      "Matching Fallbacks",
      resolvedValueGetter = { null },
      parsedPropertyGetter = { matchingFallbacks() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString,
      variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE,
      knownValuesGetter = { model -> buildTypeMatchingFallbackValues(model.parent.parent) }
    )

    val consumerProGuardFiles: ListProperty<PsBuildType, File> = listProperty(
      "Consumer ProGuard Files",
      resolvedValueGetter = { consumerProguardFiles.toList() },
      parsedPropertyGetter = { consumerProguardFiles() },
      getter = { asFile() },
      setter = { setValue(it.toString()) },
      parser = ::parseFile,
      knownValuesGetter = { model -> proGuardFileValues(model.parent) }
    )

    val proGuardFiles: ListProperty<PsBuildType, File> = listProperty(
      "ProGuard Files",
      resolvedValueGetter = { proguardFiles.toList() },
      parsedPropertyGetter = { proguardFiles() },
      getter = { asFile() },
      setter = { setValue(it.toString()) },
      parser = ::parseFile,
      knownValuesGetter = { model -> proGuardFileValues(model.parent) }
    )

    val manifestPlaceholders: MapProperty<PsBuildType, String> = mapProperty(
      "Manifest Placeholders",
      resolvedValueGetter = { manifestPlaceholders.mapValues { it.value.toString() } },
      parsedPropertyGetter = { manifestPlaceholders() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )
  }
}

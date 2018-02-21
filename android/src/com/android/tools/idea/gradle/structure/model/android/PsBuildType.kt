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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.helpers.*
import com.android.tools.idea.gradle.structure.model.meta.*
import java.io.File

private const val DEBUG_BUILD_TYPE_NAME = "debug"

open class PsBuildType(
  parent: PsAndroidModule,
  private val resolvedModel: BuildType?,
  private val parsedModel: BuildTypeModel?
) : PsChildModel(parent), PsAndroidModel {

  private var name = when {
    resolvedModel != null -> resolvedModel.name
    parsedModel != null -> parsedModel.name()
    else -> ""
  }

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

  override fun getName(): String = name
  override fun getParent(): PsAndroidModule = super.getParent() as PsAndroidModule
  override fun isDeclared(): Boolean = parsedModel != null
  override fun getResolvedModel(): BuildType? = resolvedModel
  override fun getGradleModel(): AndroidModuleModel = parent.gradleModel

  object BuildTypeDescriptors : ModelDescriptor<PsBuildType, BuildType, BuildTypeModel> {
    override fun getResolved(model: PsBuildType): BuildType? = model.resolvedModel

    override fun getParsed(model: PsBuildType): BuildTypeModel? = model.parsedModel

    override fun setModified(model: PsBuildType) {
      model.isModified = true
    }

    val applicationIdSuffix: ModelSimpleProperty<PsBuildType, String> = property(
      "Application Id Suffix",
      getResolvedValue = { applicationIdSuffix },
      getParsedProperty = { applicationIdSuffix() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) }
    )

    val debuggable: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Debuggable",
      // See: com.android.build.gradle.internal.dsl.BuildType#init
      defaultValueGetter = { it.name == DEBUG_BUILD_TYPE_NAME },
      getResolvedValue = { isDebuggable },
      getParsedProperty = { debuggable() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val embedMicroApp: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Embed Micro App",
      // See: com.android.build.gradle.internal.dsl.BuildType#init
      defaultValueGetter = { it.name != DEBUG_BUILD_TYPE_NAME },
      getResolvedValue = { isEmbedMicroApp },
      getParsedProperty = { embedMicroApp() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val jniDebuggable: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Jni Debuggable",
      defaultValueGetter = { false },
      getResolvedValue = { isJniDebuggable },
      getParsedProperty = { jniDebuggable() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val minifyEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Minify Enabled",
      defaultValueGetter = { false },
      getResolvedValue = { isMinifyEnabled },
      getParsedProperty = { minifyEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val multiDexEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Multi Dex Enabled",
      getResolvedValue = { multiDexEnabled },
      getParsedProperty = { multiDexEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val pseudoLocalesEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Pseudo Locales Enabled",
      defaultValueGetter = { false },
      getResolvedValue = { isPseudoLocalesEnabled },
      getParsedProperty = { pseudoLocalesEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val renderscriptDebuggable: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Renderscript Debuggable",
      defaultValueGetter = { false },
      getResolvedValue = { isRenderscriptDebuggable },
      getParsedProperty = { renderscriptDebuggable() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val renderscriptOptimLevel: ModelSimpleProperty<PsBuildType, Int> = property(
      "Renderscript optimization Level",
      defaultValueGetter = { 3 },
      getResolvedValue = { renderscriptOptimLevel },
      getParsedProperty = { renderscriptOptimLevel() },
      getter = { asInt() },
      setter = { setValue(it) },
      parse = { parseInt(it) }
    )

    val testCoverageEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Test Coverage Enabled",
      defaultValueGetter = { false },
      getResolvedValue = { isTestCoverageEnabled },
      getParsedProperty = { testCoverageEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val versionNameSuffix: ModelSimpleProperty<PsBuildType, String> = property(
      "Version Name Suffix",
      getResolvedValue = { versionNameSuffix },
      getParsedProperty = { versionNameSuffix() },
      getter = { asString() },
      setter = { setValue(it) },
      parse = { parseString(it) }
    )

    val zipAlignEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
      "Zip Align Enabled",
      defaultValueGetter = { true },
      getResolvedValue = { isZipAlignEnabled },
      getParsedProperty = { zipAlignEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parse = { parseBoolean(it) },
      getKnownValues = { booleanValues() }
    )

    val proGuardFiles: ModelListProperty<PsBuildType, File> = listProperty(
      "Proguard Files",
      getResolvedValue = { proguardFiles.toList() },
      getParsedProperty = { proguardFiles() },
      itemValueGetter = { asFile() },
      itemValueSetter = { setValue(it.toString()) },
      parse = { parseFile(it) }
    )

    val manifestPlaceholders: ModelMapProperty<PsBuildType, String> = mapProperty(
      "Manifest Placeholders",
      getResolvedValue = { manifestPlaceholders.mapValues { it.value.toString() } },
      getParsedProperty = { manifestPlaceholders() },
      itemValueGetter = { asString() },
      itemValueSetter = { setValue(it) },
      parse = { parseString(it) }
    )
  }
}

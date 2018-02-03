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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
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
        getParsedValue = { applicationIdSuffix().asString() },
        getParsedRawValue = { applicationIdSuffix().dslText() },
        setParsedValue = { applicationIdSuffix().setValue(it) },
        clearParsedValue = { applicationIdSuffix().clear() },
        setParsedRawValue = { applicationIdSuffix().setDslText(it) },
        parse = { parseString(it) }
    )
    val debuggable: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Debuggable",
        // See: com.android.build.gradle.internal.dsl.BuildType#init
        defaultValueGetter = { it.name == DEBUG_BUILD_TYPE_NAME },
        getResolvedValue = { isDebuggable },
        getParsedValue = { debuggable().asBoolean() },
        getParsedRawValue = { debuggable().dslText() },
        setParsedValue = { debuggable().setValue(it) },
        clearParsedValue = { debuggable().clear() },
        setParsedRawValue = { debuggable().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val embedMicroApp: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Embed Micro App",
        // See: com.android.build.gradle.internal.dsl.BuildType#init
        defaultValueGetter = { it.name != DEBUG_BUILD_TYPE_NAME },
        getResolvedValue = { isEmbedMicroApp },
        getParsedValue = { embedMicroApp().asBoolean() },
        getParsedRawValue = { embedMicroApp().dslText() },
        setParsedValue = { embedMicroApp().setValue(it) },
        clearParsedValue = { embedMicroApp().clear() },
        setParsedRawValue = { embedMicroApp().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val jniDebuggable: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Jni Debuggable",
        defaultValueGetter = { false },
        getResolvedValue = { isJniDebuggable },
        getParsedValue = { jniDebuggable().asBoolean() },
        getParsedRawValue = { jniDebuggable().dslText() },
        setParsedValue = { jniDebuggable().setValue(it) },
        clearParsedValue = { jniDebuggable().clear() },
        setParsedRawValue = { jniDebuggable().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val minifyEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Minify Enabled",
        defaultValueGetter = { false },
        getResolvedValue = { isMinifyEnabled },
        getParsedValue = { minifyEnabled().asBoolean() },
        getParsedRawValue = { minifyEnabled().dslText() },
        setParsedValue = { minifyEnabled().setValue(it) },
        clearParsedValue = { minifyEnabled().clear() },
        setParsedRawValue = { minifyEnabled().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val multiDexEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Multi Dex Enabled",
        getResolvedValue = { multiDexEnabled },
        getParsedValue = { multiDexEnabled().asBoolean() },
        getParsedRawValue = { multiDexEnabled().dslText() },
        setParsedValue = { multiDexEnabled().setValue(it) },
        clearParsedValue = { minifyEnabled().clear() },
        setParsedRawValue = { multiDexEnabled().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val pseudoLocalesEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Pseudo Locales Enabled",
        defaultValueGetter = { false },
        getResolvedValue = { isPseudoLocalesEnabled },
        getParsedValue = { pseudoLocalesEnabled().asBoolean() },
        getParsedRawValue = { pseudoLocalesEnabled().dslText() },
        setParsedValue = { pseudoLocalesEnabled().setValue(it) },
        clearParsedValue = { pseudoLocalesEnabled().clear() },
        setParsedRawValue = { pseudoLocalesEnabled().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val renderscriptDebuggable: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Renderscript Debuggable",
        defaultValueGetter = { false },
        getResolvedValue = { isRenderscriptDebuggable },
        getParsedValue = { renderscriptDebuggable().asBoolean() },
        getParsedRawValue = { renderscriptDebuggable().dslText() },
        setParsedValue = { renderscriptDebuggable().setValue(it) },
        clearParsedValue = { renderscriptDebuggable().clear() },
        setParsedRawValue = { renderscriptDebuggable().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val renderscriptOptimLevel: ModelSimpleProperty<PsBuildType, Int> = property(
        "Renderscript optimization Level",
        defaultValueGetter = { 3 },
        getResolvedValue = { renderscriptOptimLevel },
        getParsedValue = { renderscriptOptimLevel().asInt() },
        getParsedRawValue = { renderscriptOptimLevel().dslText() },
        setParsedValue = { renderscriptOptimLevel().setValue(it) },
        clearParsedValue = { renderscriptOptimLevel().clear() },
        setParsedRawValue = { renderscriptOptimLevel().setDslText(it) },
        parse = { parseInt(it) }
    )
    val testCoverageEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Test Coverage Enabled",
        defaultValueGetter = { false },
        getResolvedValue = { isTestCoverageEnabled },
        getParsedValue = { testCoverageEnabled().asBoolean() },
        getParsedRawValue = { testCoverageEnabled().dslText() },
        setParsedValue = { testCoverageEnabled().setValue(it) },
        clearParsedValue = { testCoverageEnabled().clear() },
        setParsedRawValue = { testCoverageEnabled().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val versionNameSuffix: ModelSimpleProperty<PsBuildType, String> = property(
        "Version Name Suffix",
        getResolvedValue = { versionNameSuffix },
        getParsedValue = { versionNameSuffix().asString() },
        getParsedRawValue = { versionNameSuffix().dslText() },
        setParsedValue = { versionNameSuffix().setValue(it) },
        clearParsedValue = { versionNameSuffix().clear() },
        setParsedRawValue = { versionNameSuffix().setDslText(it) },
        parse = { parseString(it) }
    )
    val zipAlignEnabled: ModelSimpleProperty<PsBuildType, Boolean> = property(
        "Zip Align Enabled",
        defaultValueGetter = { true },
        getResolvedValue = { isZipAlignEnabled },
        getParsedValue = { zipAlignEnabled().asBoolean() },
        getParsedRawValue = { zipAlignEnabled().dslText() },
        setParsedValue = { zipAlignEnabled().setValue(it) },
        clearParsedValue = { zipAlignEnabled().clear() },
        setParsedRawValue = { zipAlignEnabled().setDslText(it) },
        parse = { parseBoolean(it) },
        getKnownValues = { booleanValues() }
    )
    val proGuardFiles: ModelListProperty<PsBuildType, File> = listProperty(
      "Proguard Files",
      getResolvedValue = { proguardFiles.toList() },
      getParsedCollection = { proguardFiles().asParsedListValue(ResolvedPropertyModel::asFile, { setValue(it.toString()) }) },
      getParsedRawValue = { proguardFiles().dslText() },
      clearParsedValue = { proguardFiles().delete() },
      setParsedRawValue = { proguardFiles().setDslText(it)},
      parse = { parseFile(it) }
    )
  }
}

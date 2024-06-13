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

import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.model.IdeBaseConfig
import com.android.tools.idea.gradle.model.IdeBuildType
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.helpers.booleanValues
import com.android.tools.idea.gradle.structure.model.helpers.buildTypeMatchingFallbackValues
import com.android.tools.idea.gradle.structure.model.helpers.formatUnit
import com.android.tools.idea.gradle.structure.model.helpers.parseAny
import com.android.tools.idea.gradle.structure.model.helpers.parseBoolean
import com.android.tools.idea.gradle.structure.model.helpers.parseFile
import com.android.tools.idea.gradle.structure.model.helpers.parseInt
import com.android.tools.idea.gradle.structure.model.helpers.parseReferenceOnly
import com.android.tools.idea.gradle.structure.model.helpers.parseString
import com.android.tools.idea.gradle.structure.model.helpers.proGuardFileValues
import com.android.tools.idea.gradle.structure.model.helpers.signingConfigs
import com.android.tools.idea.gradle.structure.model.helpers.withProFileSelector
import com.android.tools.idea.gradle.structure.model.meta.ListProperty
import com.android.tools.idea.gradle.structure.model.meta.MapProperty
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.ModelProperty
import com.android.tools.idea.gradle.structure.model.meta.SimpleProperty
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
import com.android.tools.idea.gradle.structure.model.meta.property
import com.android.tools.idea.gradle.structure.navigation.PsBuildTypeNavigationPath
import com.google.common.base.CaseFormat
import icons.StudioIcons.Misc.BUILD_TYPE
import java.io.File
import javax.swing.Icon

private const val DEBUG_BUILD_TYPE_NAME = "debug"
fun IdeBaseConfig.kotlinUnitWorkAround() : Unit? = null

open class PsBuildType(
  final override val parent: PsAndroidModule,
  private val renamed: (String, String) -> Unit
) : PsChildModel() {
  override val descriptor by BuildTypeDescriptors
  var resolvedModel: IdeBuildType? = null
  private var parsedModel: BuildTypeModel? = null

  fun init(resolvedModel: IdeBuildType?, parsedModel: BuildTypeModel?) {
    this.resolvedModel = resolvedModel
    this.parsedModel = parsedModel
  }

  override val name get() = resolvedModel?.name ?: parsedModel?.name() ?: ""
  override val path: PsBuildTypeNavigationPath get() = PsBuildTypeNavigationPath(parent.path.buildTypesPath, name)

  var applicationIdSuffix by BuildTypeDescriptors.applicationIdSuffix
  var jniDebuggable by BuildTypeDescriptors.jniDebuggable
  var minifyEnabled by BuildTypeDescriptors.minifyEnabled
  var renderscriptDebuggable by BuildTypeDescriptors.renderscriptDebuggable
  var renderscriptOptimLevel by BuildTypeDescriptors.renderscriptOptimLevel
  var testCoverageEnabled by BuildTypeDescriptors.testCoverageEnabled
  var versionNameSuffix by BuildTypeDescriptors.versionNameSuffix
  var zipAlignEnabled by BuildTypeDescriptors.zipAlignEnabled
  var multiDexEnabled by BuildTypeDescriptors.multiDexEnabled
  var debuggable by BuildTypeDescriptors.debuggable
  var matchingFallbacks by BuildTypeDescriptors.matchingFallbacks
  var consumerProguardFiles by BuildTypeDescriptors.consumerProGuardFiles
  var proguardFiles by BuildTypeDescriptors.proGuardFiles
  var manifestPlaceholders by BuildTypeDescriptors.manifestPlaceholders
  var enableUnitTestCoverage by BuildTypeDescriptors.enableUnitTestCoverage
  var enableAndroidTestCoverage by BuildTypeDescriptors.enableAndroidTestCoverage

  override val isDeclared: Boolean get() = parsedModel != null
  override val icon: Icon = BUILD_TYPE

  fun ensureDeclared() {
    if (parsedModel == null) {
      parsedModel = parent.parsedModel!!.android().addBuildType(name)
      parent.isModified = true
    }
  }

  fun rename(newName: String) {
    ensureDeclared()
    val oldName = name
    parsedModel!!.rename(newName)
    renamed(oldName, newName)
  }

  object BuildTypeDescriptors : ModelDescriptor<PsBuildType, IdeBuildType, BuildTypeModel> {
    override fun getResolved(model: PsBuildType): IdeBuildType? = model.resolvedModel

    override fun getParsed(model: PsBuildType): BuildTypeModel? = model.parsedModel

    override fun prepareForModification(model: PsBuildType) {
      model.ensureDeclared()
    }

    override fun setModified(model: PsBuildType) {
      model.isModified = true
    }

    private fun variableName(name: String): String = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, name)

    val applicationIdSuffix: SimpleProperty<PsBuildType, String> = property(
      "Application ID Suffix",
      preferredVariableName = { variableName("$name-application-id-suffix") },
      resolvedValueGetter = { applicationIdSuffix },
      parsedPropertyGetter = { applicationIdSuffix() },
      getter = { asString() },
      setter = { setValue(it) },
      parser = ::parseString
    )

    val debuggable: SimpleProperty<PsBuildType, Boolean> = property(
      "Debuggable",
      preferredVariableName = { variableName("$name-debuggable") },
      // See: com.android.build.gradle.internal.dsl.BuildType#init
      defaultValueGetter = { it.name == DEBUG_BUILD_TYPE_NAME },
      resolvedValueGetter = { isDebuggable },
      parsedPropertyGetter = { debuggable() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val jniDebuggable: SimpleProperty<PsBuildType, Boolean> = property(
      "Jni Debuggable",
      preferredVariableName = { variableName("$name-jni-debuggable") },
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
      preferredVariableName = { variableName("$name-minify-enabled") },
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
      preferredVariableName = { variableName("$name-multi-dex-enabled") },
      resolvedValueGetter = { multiDexEnabled },
      parsedPropertyGetter = { multiDexEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val renderscriptDebuggable: SimpleProperty<PsBuildType, Boolean> = property(
      "Renderscript Debuggable",
      preferredVariableName = { variableName("$name-renderscript-debuggable") },
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
      preferredVariableName = { variableName("$name-renderscript-optim-level") },
      defaultValueGetter = { 3 },
      resolvedValueGetter = { renderscriptOptimLevel },
      parsedPropertyGetter = { renderscriptOptimLevel() },
      getter = { asInt() },
      setter = { setValue(it) },
      parser = ::parseInt
    )

    val signingConfig: SimpleProperty<PsBuildType, Unit> = property(
      "Signing Config",
      resolvedValueGetter = IdeBaseConfig::kotlinUnitWorkAround,
      parsedPropertyGetter = { signingConfig() },
      getter = ResolvedPropertyModel::asUnit,
      setter = {},
      parser = ::parseReferenceOnly,
      formatter = ::formatUnit,
      knownValuesGetter = { model -> signingConfigs(model.parent) }
    )

    val testCoverageEnabled: SimpleProperty<PsBuildType, Boolean> = property(
      "Test Coverage Enabled",
      preferredVariableName = { variableName("$name-test-coverage-enabled") },
      defaultValueGetter = { false },
      resolvedValueGetter = { null },
      parsedPropertyGetter = { testCoverageEnabled() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val versionNameSuffix: SimpleProperty<PsBuildType, String> = property(
      "Version Name Suffix",
      preferredVariableName = { variableName("$name-version-name-suffix") },
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
      .withProFileSelector(module = { parent })

    val proGuardFiles: ListProperty<PsBuildType, File> = listProperty(
      "ProGuard Files",
      resolvedValueGetter = { proguardFiles.toList() },
      parsedPropertyGetter = { proguardFiles() },
      getter = { asFile() },
      setter = { setValue(it.toString()) },
      parser = ::parseFile,
      knownValuesGetter = { model -> proGuardFileValues(model.parent) }
    )
      .withProFileSelector(module = { parent })

    val manifestPlaceholders: MapProperty<PsBuildType, Any> = mapProperty(
      "Manifest Placeholders",
      resolvedValueGetter = { manifestPlaceholders },
      parsedPropertyGetter = { manifestPlaceholders() },
      getter = { asAny() },
      setter = { setValue(it) },
      parser = ::parseAny
    )

    val enableUnitTestCoverage: SimpleProperty<PsBuildType, Boolean> = property(
      "Enable Unit Test Coverage",
      preferredVariableName = { variableName("$name-enable-unit-test-coverage") },
      defaultValueGetter = { false },
      resolvedValueGetter = { null },
      parsedPropertyGetter = { enableUnitTestCoverage() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    val enableAndroidTestCoverage: SimpleProperty<PsBuildType, Boolean> = property(
      "Enable Android Test Coverage",
      preferredVariableName = { variableName("$name-enable-android-test-coverage") },
      defaultValueGetter = { false },
      resolvedValueGetter = { null },
      parsedPropertyGetter = { enableAndroidTestCoverage() },
      getter = { asBoolean() },
      setter = { setValue(it) },
      parser = ::parseBoolean,
      knownValuesGetter = ::booleanValues
    )

    override val properties: Collection<ModelProperty<PsBuildType, *, *, *>> =
      listOf(applicationIdSuffix, debuggable, jniDebuggable, minifyEnabled, multiDexEnabled, renderscriptDebuggable, renderscriptOptimLevel,
             signingConfig, testCoverageEnabled, versionNameSuffix, zipAlignEnabled, matchingFallbacks, consumerProGuardFiles,
             proGuardFiles, manifestPlaceholders, enableUnitTestCoverage, enableAndroidTestCoverage)
  }
}

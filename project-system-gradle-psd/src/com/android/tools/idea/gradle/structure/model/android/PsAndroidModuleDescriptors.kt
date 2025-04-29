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

import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.project.model.NdkModel
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.helpers.*
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.pom.java.LanguageLevel

data class AndroidModuleResolvedModels(val android: IdeAndroidProject?, val ndk: NdkModel?)
object AndroidModuleDescriptors : ModelDescriptor<PsAndroidModule, AndroidModuleResolvedModels, AndroidModel> {
  override fun getResolved(model: PsAndroidModule): AndroidModuleResolvedModels =
    AndroidModuleResolvedModels(android = model.resolvedModel?.androidProject, model.resolvedNativeModel?.ndkModel)

  override fun getParsed(model: PsAndroidModule): AndroidModel? = model.parsedModel?.android()

  override fun prepareForModification(model: PsAndroidModule) = Unit

  override fun setModified(model: PsAndroidModule) {
    model.isModified = true
  }

  val compileSdkVersion: SimpleProperty<PsAndroidModule, String> = property(
    "Compile Sdk Version",
    preferredVariableName = { "compileSdkVersion" },
    resolvedValueGetter = { android?.compileTarget },
    parsedPropertyGetter = { compileSdkVersion() },
    getter = { asString() },
    setter = { setValue(it.toIntOrNull() ?: it) },
    parser = ::parseString,
    matcher = ::matchHashStrings,
    knownValuesGetter = ::compileSdkValues
  )

  val buildToolsVersion: SimpleProperty<PsAndroidModule, String> = property(
    "Build Tools Version",
    preferredVariableName = { "buildToolsVersion" },
    resolvedValueGetter = { android?.buildToolsVersion },
    parsedPropertyGetter = { buildToolsVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString,
    knownValuesGetter = ::buildToolsVersionValues,
    variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE
  )

  val ndkVersion: SimpleProperty<PsAndroidModule, String> = property(
    "NDK Version",
    preferredVariableName = { "ndkVersion" },
    defaultValueGetter = { it.resolvedNativeModel?.defaultNdkVersion },
    // TODO(192053331): Allow using the nativeModel in resolvedValueGetter as the ndkVersion isn't available from IdeAndroidProject.
    resolvedValueGetter = null,
    parsedPropertyGetter = { ndkVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString,
    knownValuesGetter = ::ndkVersionValues,
    variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE
  )

  val sourceCompatibility: SimpleProperty<PsAndroidModule, LanguageLevel> = property(
    "Source Compatibility",
    preferredVariableName = { "sourceCompatibility" },
    resolvedValueGetter = { LanguageLevel.parse(android?.javaCompileOptions?.sourceCompatibility) },
    parsedPropertyGetter = { compileOptions().sourceCompatibility() },
    getter = { asLanguageLevel() },
    setter = { setLanguageLevel(it) },
    parser = ::parseLanguageLevel,
    formatter = ::formatLanguageLevel,
    knownValuesGetter = ::languageLevels
  )

  val targetCompatibility: SimpleProperty<PsAndroidModule, LanguageLevel> = property(
    "Target Compatibility",
    preferredVariableName = { "targetCompatibility" },
    resolvedValueGetter = { LanguageLevel.parse(android?.javaCompileOptions?.targetCompatibility) },
    parsedPropertyGetter = { compileOptions().targetCompatibility() },
    getter = { asLanguageLevel() },
    setter = { setLanguageLevel(it) },
    parser = ::parseLanguageLevel,
    formatter = ::formatLanguageLevel,
    knownValuesGetter = ::languageLevels
  )

  val viewBindingEnabled: SimpleProperty<PsAndroidModule, Boolean> = property(
    "Enable View Binding",
    preferredVariableName = { "viewBindingEnabled" },
    resolvedValueGetter = { android?.viewBindingOptions?.enabled },
    parsedPropertyGetter = { viewBinding().enabled() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val includeDependenciesInfoInApk: SimpleProperty<PsAndroidModule, Boolean> = property(
    "Retain information about dependencies in the apk",
    preferredVariableName = { "includeInApk" },
    resolvedValueGetter = { android?.dependenciesInfo?.includeInApk },
    parsedPropertyGetter = { dependenciesInfo().includeInApk() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  val includeDependenciesInfoInBundle: SimpleProperty<PsAndroidModule, Boolean> = property(
    "Retain information about dependencies in the bundle",
    preferredVariableName = { "includeInBundle" },
    resolvedValueGetter = { android?.dependenciesInfo?.includeInBundle },
    parsedPropertyGetter = { dependenciesInfo().includeInBundle() },
    getter = { asBoolean() },
    setter = { setValue(it) },
    parser = ::parseBoolean,
    knownValuesGetter = ::booleanValues
  )

  override fun enumerateModels(model: PsAndroidModule): Collection<PsModel> =
    model.buildTypes + model.productFlavors + model.flavorDimensions + model.signingConfigs + model.dependencies.items + model.defaultConfig

  override val properties: Collection<ModelProperty<PsAndroidModule, *, *, *>> =
    listOf(compileSdkVersion, buildToolsVersion, ndkVersion, sourceCompatibility, targetCompatibility, viewBindingEnabled,
           includeDependenciesInfoInApk, includeDependenciesInfoInBundle)
}

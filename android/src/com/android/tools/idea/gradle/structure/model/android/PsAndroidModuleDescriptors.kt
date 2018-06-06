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

import com.android.builder.model.AndroidProject
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.structure.model.helpers.*
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.pom.java.LanguageLevel

object AndroidModuleDescriptors : ModelDescriptor<PsAndroidModule, AndroidProject, AndroidModel> {
  override fun getResolved(model: PsAndroidModule): AndroidProject? = model.resolvedModel?.androidProject

  override fun getParsed(model: PsAndroidModule): AndroidModel? = model.parsedModel?.android()

  override fun setModified(model: PsAndroidModule) {
    model.isModified = true
  }

  val compileSdkVersion: SimpleProperty<PsAndroidModule, String> = property(
    "Compile Sdk Version",
    resolvedValueGetter = { compileTarget },
    parsedPropertyGetter = { compileSdkVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseHashString,
    matcher = ::matchHashStrings,
    knownValuesGetter = ::installedCompiledApis
  )

  val buildToolsVersion: SimpleProperty<PsAndroidModule, String> = property(
    "Build Tools Version",
    resolvedValueGetter = { buildToolsVersion },
    parsedPropertyGetter = { buildToolsVersion() },
    getter = { asString() },
    setter = { setValue(it) },
    parser = ::parseString,
    knownValuesGetter = ::installedBuildTools,
    variableMatchingStrategy = VariableMatchingStrategy.WELL_KNOWN_VALUE
  )

  val sourceCompatibility: SimpleProperty<PsAndroidModule, LanguageLevel> = property(
    "Source Compatibility",
    resolvedValueGetter = { LanguageLevel.parse(javaCompileOptions.sourceCompatibility) },
    parsedPropertyGetter = { compileOptions().sourceCompatibility() },
    getter = { asLanguageLevel() },
    setter = { setLanguageLevel(it) },
    parser = ::parseLanguageLevel,
    formatter = ::formatLanguageLevel,
    knownValuesGetter = ::languageLevels
  )

  val targetCompatibility: SimpleProperty<PsAndroidModule, LanguageLevel> = property(
    "Target Compatibility",
    resolvedValueGetter = { LanguageLevel.parse(javaCompileOptions.targetCompatibility) },
    parsedPropertyGetter = { compileOptions().targetCompatibility() },
    getter = { asLanguageLevel() },
    setter = { setLanguageLevel(it) },
    parser = ::parseLanguageLevel,
    formatter = ::formatLanguageLevel,
    knownValuesGetter = ::languageLevels
  )
}

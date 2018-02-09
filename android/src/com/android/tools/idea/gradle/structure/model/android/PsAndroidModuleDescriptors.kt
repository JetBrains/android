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

import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.sdklib.AndroidTargetHash
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.structure.model.helpers.*
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.pom.java.LanguageLevel

object AndroidModuleDescriptors : ModelDescriptor<PsAndroidModule, IdeAndroidProject, AndroidModel> {
  override fun getResolved(model: PsAndroidModule): IdeAndroidProject = model.gradleModel.androidProject

  override fun getParsed(model: PsAndroidModule): AndroidModel? = model.parsedModel?.android()

  override fun setModified(model: PsAndroidModule) {
    model.isModified = true
  }

  val compileSdkVersion: ModelSimpleProperty<PsAndroidModule, String> = property(
      "Compile Sdk Version",
      getResolvedValue = { AndroidTargetHash.getPlatformVersion(compileTarget)?.featureLevel?.toString() ?: compileTarget },
      getParsedValue = { compileSdkVersion().asString() },
      getParsedRawValue = { compileSdkVersion().dslText() },
      setParsedValue = { compileSdkVersion().setValue(it) },
      clearParsedValue = { compileSdkVersion().delete() },
      parse = { parseString(it) },
      getKnownValues = { installedCompiledApis() }
  )

  val buildToolsVersion: ModelSimpleProperty<PsAndroidModule, String> = property(
      "Build Tools Version",
      getResolvedValue = { buildToolsVersion },
      getParsedValue = { buildToolsVersion().asString() },
      getParsedRawValue = { buildToolsVersion().dslText() },
      setParsedValue = { buildToolsVersion().setValue(it) },
      clearParsedValue = { buildToolsVersion().delete() },
      parse = { parseString(it) },
      getKnownValues = { installedBuildTools() }
  )

  val sourceCompatibility: ModelSimpleProperty<PsAndroidModule, LanguageLevel> = property(
      "Source Compatibility",
      getResolvedValue = { LanguageLevel.parse(javaCompileOptions.sourceCompatibility) },
      getParsedValue = { compileOptions().sourceCompatibility().value() },
      getParsedRawValue = { compileOptions().sourceCompatibility().dsl()  },
      setParsedValue = { compileOptions().setSourceCompatibility(it) },
      clearParsedValue = { compileOptions().removeSourceCompatibility() },
      parse = { parseEnum(it, LanguageLevel::parse) },
      getKnownValues = { languageLevels() }
  )

  val targetCompatibility: ModelSimpleProperty<PsAndroidModule, LanguageLevel> = property(
      "Target Compatibility",
      getResolvedValue = { LanguageLevel.parse(javaCompileOptions.targetCompatibility) },
      getParsedValue = { compileOptions().targetCompatibility().value() },
      getParsedRawValue = { compileOptions().targetCompatibility().dsl() },
      setParsedValue = { compileOptions().setTargetCompatibility(it) },
      clearParsedValue = { compileOptions().removeTargetCompatibility() },
      parse = { parseEnum(it, LanguageLevel::parse) },
      getKnownValues = { languageLevels() }
  )
}

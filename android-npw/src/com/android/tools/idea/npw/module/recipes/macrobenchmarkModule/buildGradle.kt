/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.idea.npw.module.recipes.macrobenchmarkModule

import com.android.tools.idea.npw.module.recipes.androidModule.gradleToKtsIfKts
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.flavorsConfigurationsBuildGradle
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.FlavorNameAndDimension
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.npw.module.recipes.toAndroidFieldVersion
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.renderIf
import com.intellij.openapi.module.Module

fun macrobenchmarksBuildGradle(
  newModule: ModuleTemplateData,
  flavorDimensionNames: List<String>,
  flavorNamesAndDimensions: List<FlavorNameAndDimension>,
  useGradleKts: Boolean,
  targetModule: Module,
  benchmarkBuildTypeName: String,
): String {
  val packageName = newModule.packageName
  val apis = newModule.apis
  val language = newModule.projectTemplateData.language
  val gradlePluginVersion = newModule.projectTemplateData.gradlePluginVersion
  // TODO(b/149203281): Fix support for composite builds.
  val targetModuleGradlePath = targetModule.getGradleProjectPath()?.path
  val flavorsConfiguration = flavorsConfigurationsBuildGradle(flavorDimensionNames, flavorNamesAndDimensions, useGradleKts)

  val benchmarkBuildType: String
  val debugSigningConfig: String
  val matchingFallbacks: String
  val addReceiverIfKts: String.() -> String

  if (useGradleKts) {
    benchmarkBuildType = """create("$benchmarkBuildTypeName")"""
    debugSigningConfig = """getByName("debug").signingConfig"""
    matchingFallbacks = "matchingFallbacks += listOf(\"release\")"
    addReceiverIfKts = { "it.$this" }
  }
  else {
    benchmarkBuildType = benchmarkBuildTypeName
    debugSigningConfig = "debug.signingConfig"
    matchingFallbacks = "matchingFallbacks = [\"release\"]"
    addReceiverIfKts = { this }
  }

  val kotlinOptionsBlock = renderIf(language == Language.Kotlin) {
    """
    kotlinOptions {
        jvmTarget = "1.8"
    }
    """
  }

  return """
${emptyPluginsBlock()}

android {
    namespace '$packageName'
    ${toAndroidFieldVersion("compileSdk", apis.buildApi.apiString, gradlePluginVersion)}

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    $kotlinOptionsBlock

    defaultConfig {
        ${toAndroidFieldVersion("minSdk", apis.minApi.apiString, gradlePluginVersion)}
        ${toAndroidFieldVersion("targetSdk", apis.targetApi.apiString, gradlePluginVersion)}

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // This benchmark buildType is used for benchmarking, and should function like your
        // release build (for example, with minification on). It's signed with a debug key
        // for easy local/CI testing.
        $benchmarkBuildType {
            debuggable = true
            signingConfig = $debugSigningConfig
            $matchingFallbacks
        }
    }

    $flavorsConfiguration

    targetProjectPath = "$targetModuleGradlePath"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
}

androidComponents {
    beforeVariants(selector().all()) {
        ${"enable".addReceiverIfKts()} = ${"buildType".addReceiverIfKts()} == "$benchmarkBuildTypeName"
    }
}

""".gradleToKtsIfKts(useGradleKts)
}

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

import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.npw.module.recipes.androidModule.gradleToKtsIfKts
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.npw.module.recipes.toAndroidFieldVersion
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.renderIf
import com.intellij.openapi.module.Module

fun buildGradle(
  explicitBuildToolsVersion: Boolean,
  buildApiString: String,
  buildToolsVersion: String,
  minApi: String,
  targetApiString: String,
  language: Language,
  gradlePluginVersion: GradlePluginVersion,
  useGradleKts: Boolean,
  targetModule: Module,
  benchmarkBuildTypeName: String,
): String {
  fun String.addReceiverIfKts() = when {
    useGradleKts -> "it.$this"
    else -> this
  }

  val benchmarkBuildType: String = when {
    useGradleKts -> """create("$benchmarkBuildTypeName")"""
    else -> benchmarkBuildTypeName
  }

  val debugSigningConfig: String = when {
    useGradleKts -> """getByName("debug").signingConfig"""
    else -> "debug.signingConfig"
  }

  val buildToolsVersionBlock = renderIf(explicitBuildToolsVersion) { "buildToolsVersion \"$buildToolsVersion\"" }
  val kotlinOptionsBlock = renderIf(language == Language.Kotlin) {
    """
   kotlinOptions {
      jvmTarget = "1.8"
   }
  """
  }

  val targetModuleGradlePath = GradleUtil.getGradlePath(targetModule)

  return """
${emptyPluginsBlock()}

android {
    ${toAndroidFieldVersion("compileSdk", buildApiString, gradlePluginVersion)}
    $buildToolsVersionBlock

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    $kotlinOptionsBlock

    defaultConfig {
        ${toAndroidFieldVersion("minSdk", minApi, gradlePluginVersion)}
        ${toAndroidFieldVersion("targetSdk", targetApiString, gradlePluginVersion)}

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        $benchmarkBuildType {
            debuggable = true
            signingConfig = $debugSigningConfig
        }
    }

    targetProjectPath = "$targetModuleGradlePath"
    properties["android.experimental.self-instrumenting"] = true
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

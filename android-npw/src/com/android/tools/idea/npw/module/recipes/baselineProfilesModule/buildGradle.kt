/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.baselineProfilesModule

import com.android.tools.idea.npw.module.recipes.androidModule.gradleToKtsIfKts
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.flavorsConfigurationsBuildGradle
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.npw.module.recipes.toAndroidFieldVersion
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.renderIf
import com.intellij.openapi.module.Module

private const val BENCHMARK_MIN_COMPILE_SDK = 34
private const val BENCHMARK_MIN_API = 28

fun baselineProfilesBuildGradle(
  newModule: ModuleTemplateData,
  flavors: ProductFlavorsWithDimensions,
  useGradleKts: Boolean,
  targetModule: Module,
  useGmd: GmdSpec?,
  useInstrumentationArgumentForAppId: Boolean
): String {
  val packageName = newModule.packageName
  val apis = newModule.apis
  val language = newModule.projectTemplateData.language
  val agpVersion = newModule.projectTemplateData.agpVersion
  // TODO(b/149203281): Fix support for composite builds.
  val targetModuleGradlePath = targetModule.getGradleProjectPath()?.path
  val flavorsConfiguration = flavorsConfigurationsBuildGradle(flavors, useGradleKts)

  val addTargetAppIdAsInstrumentationArgumentBlock = if (useInstrumentationArgumentForAppId) {
    """

      androidComponents {
          onVariants${if (useGradleKts) "" else "(selector().all())"} {  v ->
              v.instrumentationRunnerArguments.put(
                  "targetAppId",
                  v.testedApks.map { v.artifacts.getBuiltArtifactsLoader().load(it)?.applicationId }
              )
          }
      }

    """.trimIndent()
  } else ""

  val kotlinOptionsBlock = renderIf(language == Language.Kotlin) {
    """
    kotlinOptions {
        jvmTarget = "1.8"
    }
    """
  }

  val gmdDefinition = renderIf(useGmd != null) {
    useGmd!!

    val createGMD: String = if (useGradleKts) {
      "create<ManagedVirtualDevice>(\"${useGmd.identifier}\")"
    }
    else {
      "${useGmd.identifier}(ManagedVirtualDevice)"
    }

    """
    testOptions.managedDevices.devices {
        $createGMD {
            device = "${useGmd.deviceName}"
            apiLevel = ${useGmd.apiLevel}
            systemImageSource = "${useGmd.systemImageSource}"
        }
    }
    """.trimIndent()
  }

  val pluginConfiguration = buildString {
    appendLine("// This is the configuration block for the Baseline Profile plugin.")
    appendLine("// You can specify to run the generators on a managed devices or connected devices.")
    appendLine("baselineProfile {")
    if (useGmd != null) {
      appendLine("managedDevices += \"${useGmd.identifier}\"")
      appendLine("useConnectedDevices = false")
    }
    else {
      appendLine("useConnectedDevices = true")
    }
    append("}")
  }

  return """
${renderIf(useGmd != null) { "import com.android.build.api.dsl.ManagedVirtualDevice" }}
${emptyPluginsBlock()}

android {
  namespace '$packageName'
  ${toAndroidFieldVersion("compileSdk", "${maxOf(BENCHMARK_MIN_COMPILE_SDK, apis.buildApi.api)}", agpVersion)}

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  $kotlinOptionsBlock

  defaultConfig {
        ${toAndroidFieldVersion("minSdk", "${maxOf(apis.minApi.api, BENCHMARK_MIN_API)}", agpVersion)}
        ${toAndroidFieldVersion("targetSdk", "${maxOf(apis.targetApi.api, BENCHMARK_MIN_API)}", agpVersion)}

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = "$targetModuleGradlePath"

    $flavorsConfiguration

    $gmdDefinition
}

$pluginConfiguration

dependencies {
}

""".gradleToKtsIfKts(useGradleKts) + addTargetAppIdAsInstrumentationArgumentBlock
}
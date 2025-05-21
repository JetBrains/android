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

import com.android.sdklib.AndroidMajorVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.npw.module.recipes.androidModule.gradleToKtsIfKts
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.flavorsConfigurationsBuildGradle
import com.android.tools.idea.npw.module.recipes.compileSdk
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.npw.module.recipes.minSdk
import com.android.tools.idea.npw.module.recipes.targetSdk
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.renderIf
import com.intellij.openapi.module.Module

private val BENCHMARK_MIN_COMPILE_SDK = AndroidVersion(34, 0)
private val BENCHMARK_MIN_API = AndroidMajorVersion(28)

fun baselineProfilesBuildGradle(
  newModule: ModuleTemplateData,
  flavors: ProductFlavorsWithDimensions,
  useGradleKts: Boolean,
  targetModule: Module,
  useGmd: GmdSpec?,
  useInstrumentationArgumentForAppId: Boolean,
): String {
  val packageName = newModule.packageName
  val apis = newModule.apis
  val language = newModule.projectTemplateData.language
  val agpVersion = newModule.projectTemplateData.agpVersion
  // TODO(b/149203281): Fix support for composite builds.
  val targetModuleGradlePath = targetModule.getGradleProjectPath()?.path
  val flavorsConfiguration = flavorsConfigurationsBuildGradle(flavors, useGradleKts)

  val addTargetAppIdAsInstrumentationArgumentBlock =
    if (useInstrumentationArgumentForAppId) {
      """

      androidComponents {
          onVariants${if (useGradleKts) "" else "(selector().all())"} {  v ->
              ${if (useGradleKts) "val" else "def"} artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
              v.instrumentationRunnerArguments.put(
                  "targetAppId",
                  v.testedApks.map { artifactsLoader.load(it)?.applicationId }
              )
          }
      }

    """
        .trimIndent()
    } else ""

  val kotlinOptionsBlock =
    renderIf(language == Language.Kotlin) {
      """
    kotlinOptions {
        jvmTarget = "11"
    }
    """
    }

  val gmdDefinition =
    renderIf(useGmd != null) {
      useGmd!!

      val createGMD: String =
        if (useGradleKts) {
          "create<ManagedVirtualDevice>(\"${useGmd.identifier}\")"
        } else {
          "${useGmd.identifier}(ManagedVirtualDevice)"
        }

      buildString {
        appendLine(
          "    // This code creates the gradle managed device used to generate baseline profiles."
        )
        appendLine("    // To use GMD please invoke generation through the command line:")
        appendLine("    // ./gradlew $targetModuleGradlePath:generateBaselineProfile")
        appendLine("    testOptions.managedDevices.devices {")
        appendLine("        $createGMD {")
        appendLine("            device = \"${useGmd.deviceName}\"")
        appendLine("            apiLevel = ${useGmd.apiLevel}")
        appendLine("            systemImageSource = \"${useGmd.systemImageSource}\"")
        appendLine("        }")
        appendLine("    }")
      }
    }

  val pluginConfiguration = buildString {
    appendLine("// This is the configuration block for the Baseline Profile plugin.")
    appendLine(
      "// You can specify to run the generators on a managed devices or connected devices."
    )
    appendLine("baselineProfile {")
    if (useGmd != null) {
      appendLine("managedDevices += \"${useGmd.identifier}\"")
      appendLine("useConnectedDevices = false")
    } else {
      appendLine("useConnectedDevices = true")
    }
    append("}")
  }

  return """
${renderIf(useGmd != null) { "import com.android.build.api.dsl.ManagedVirtualDevice" }}
${emptyPluginsBlock()}

android {
  namespace '$packageName'
  ${compileSdk(maxOf(BENCHMARK_MIN_COMPILE_SDK, apis.buildApi), agpVersion)}

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  $kotlinOptionsBlock

  defaultConfig {
        ${minSdk(maxOf(apis.minApi, BENCHMARK_MIN_API), agpVersion)}
        ${targetSdk(maxOf(apis.targetApi, BENCHMARK_MIN_API), agpVersion)}

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = "$targetModuleGradlePath"

    $flavorsConfiguration

    $gmdDefinition
}

$pluginConfiguration

dependencies {
}

"""
    .gradleToKtsIfKts(useGradleKts) + addTargetAppIdAsInstrumentationArgumentBlock
}

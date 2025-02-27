/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.npw.module.recipes.androidModule.gradleToKtsIfKts
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.npw.module.recipes.toAndroidFieldVersion

fun buildKmpGradle(
  agpVersion: AgpVersion,
  name: String,
  packageName: String,
  compileApiString: String,
  minApi: String,
): String {
  val androidTargetBlock =
    androidTargetConfig(
      agpVersion = agpVersion,
      compileApiString = compileApiString,
      minApi = minApi,
      packageName = packageName,
    )

  val iosTargetBlock = iosTargetConfig(name)

  val sourceSetConfigurationsBlock =
    """
      // Source set declarations.
      // Declaring a target automatically creates a source set with the same name. By default, the
      // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
      // common to share sources between related targets.
      // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
      commonMain {
        dependencies {
          // Add KMP dependencies here
        }
      }

      commonTest {
        dependencies {
        }
      }

      androidMain {
        dependencies {
          // Add Android-specific dependencies here. Note that this source set depends on
          // commonMain by default and will correctly pull the Android artifacts of any KMP
          // dependencies declared in commonMain.
        }
      }

      getByName("androidDeviceTest") {
        dependencies {
        }
      }

      iosMain {
        dependencies {
          // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
          // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
          // part of KMP’s default source set hierarchy. Note that this source set depends
          // on common by default and will correctly pull the iOS artifacts of any
          // KMP dependencies declared in commonMain.
        }
      }
    }
  """

  val kotlinBlock =
    """
    kotlin {
      $androidTargetBlock
      $iosTargetBlock
      $sourceSetConfigurationsBlock
    }
  """
      .trimIndent()

  val allBlocks =
    """
    ${emptyPluginsBlock()}
    $kotlinBlock
    """

  return allBlocks.gradleToKtsIfKts(true)
}

private fun androidTargetConfig(
  agpVersion: AgpVersion,
  packageName: String,
  compileApiString: String,
  minApi: String,
): String {
  return """
      // Target declarations - add or remove as needed below. These define
      // which platforms this KMP module supports.
      // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
      namespace '$packageName'
      ${toAndroidFieldVersion("compileSdk", compileApiString, agpVersion)}
      ${toAndroidFieldVersion("minSdk", minApi, agpVersion)}

      withHostTestBuilder {
      }

      withDeviceTestBuilder {
          sourceSetTreeName = "test"
      }.configure {
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
      }
    }
"""
}

private fun iosTargetConfig(name: String): String {
  return """
      // For iOS targets, this is also where you should
      // configure native binary output. For more information, see:
      // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

      // A step-by-step guide on how to include this library in an XCode
      // project can be found here:
      // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "${name}Kit"

    iosX64 {
      binaries.framework {
        baseName = xcfName
      }
    }

    iosArm64 {
      binaries.framework {
        baseName = xcfName
      }
    }

    iosSimulatorArm64 {
      binaries.framework {
        baseName = xcfName
      }
    }
  """
}

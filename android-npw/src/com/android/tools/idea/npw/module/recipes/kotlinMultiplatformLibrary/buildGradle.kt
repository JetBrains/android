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
  packageName: String,
  compileApiString: String,
  minApi: String,
): String {
  val androidTargetBlock = androidTargetConfig(
    agpVersion = agpVersion,
    compileApiString = compileApiString,
    minApi = minApi,
    packageName = packageName,
  )

  val sourceSetConfigurationsBlock = """
    sourceSets {
      getByName("androidMain") {
        dependencies {
          // put your android target dependencies here
        }
      }
      getByName("androidInstrumentedTest") {
        dependencies {
        }   
      }
      commonMain {
        dependencies {
          // put your common multiplatform dependencies here
        }
      }
      commonTest {
        dependencies {
        }
      }
    }
  """.trimIndent()

  val kotlinBlock = """
    kotlin {
      $androidTargetBlock
      $sourceSetConfigurationsBlock
    }
  """.trimIndent()

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
    androidLibrary {
      namespace '$packageName'
      ${toAndroidFieldVersion("compileSdk", compileApiString, agpVersion)}
      ${toAndroidFieldVersion("minSdk", minApi, agpVersion)}
      
      withAndroidTestOnJvmBuilder {
          compilationName = "unitTest"
          defaultSourceSetName = "androidUnitTest"
      }
      
      withAndroidTestOnDeviceBuilder {
          compilationName = "instrumentedTest"
          defaultSourceSetName = "androidInstrumentedTest"
          sourceSetTreeName = "test"
      }
    }
    """.trimIndent()
}
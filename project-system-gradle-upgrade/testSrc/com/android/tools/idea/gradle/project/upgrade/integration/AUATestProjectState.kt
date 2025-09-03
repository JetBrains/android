/*
 * Copyright (C) 2021 The Android Open Source Project
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
/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade.integration

import com.android.sdklib.AndroidApiLevel
import com.android.sdklib.AndroidVersion
import com.android.testutils.junit4.OldAgpSuite
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.LATEST
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_8_11
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_4_0
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_4_1
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_4_2
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_7_0
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_7_1
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_7_2
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_7_3
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_8_0
import com.android.tools.idea.gradle.project.upgrade.integration.TestProject.FROM_AGP_40_ALL_DEPRECATIONS
import com.android.tools.idea.gradle.project.upgrade.integration.TestProject.FROM_AGP_40_BASIC
import com.android.tools.idea.gradle.project.upgrade.integration.TestProject.FROM_AGP_80_BASIC
import com.android.utils.FileUtils
import com.intellij.openapi.projectRoots.JavaSdkVersion

/**
 * List of projects available for integration tests.
 * Each project has multiple states for different versions
 * between which we test running upgrades.
 */
enum class TestProject(
  vararg val path: String
){
  FROM_AGP_40_BASIC("FromAgp40", "Basic"),
  FROM_AGP_40_ALL_DEPRECATIONS("FromAgp40", "AllDeprecations"),
  FROM_AGP_80_BASIC("FromAgp80", "Basic"),
}

/**
 * Definition of version pairs available for testing.
 * All versions listed here should directly map to old_agp_test targets in BUILD file.
 * If you add a new version here don't forget to add new target to BUILD.
 * There is no validation of this yet.
 */
enum class TestAGPVersion(
  val agpVersion: String?,
  val jdkVersion: JavaSdkVersion? = null,
  val kotlinVersion: String = "1.3.72",
  val compileSdkVersion: AndroidVersion = AndroidVersion(AndroidApiLevel(34))
) {
  V_4_0("4.0.0", jdkVersion = JavaSdkVersion.JDK_11),
  V_4_1("4.1.0", jdkVersion = JavaSdkVersion.JDK_11),
  V_4_2("4.2.0", jdkVersion = JavaSdkVersion.JDK_11),
  V_7_0("7.0.0", jdkVersion = JavaSdkVersion.JDK_11),
  V_7_1("7.1.0", jdkVersion = JavaSdkVersion.JDK_11),
  V_7_2("7.2.0", jdkVersion = JavaSdkVersion.JDK_11),
  V_7_3("7.3.0", jdkVersion = JavaSdkVersion.JDK_11, kotlinVersion = "1.6.21"),
  V_8_0("8.0.2", jdkVersion = JavaSdkVersion.JDK_17, kotlinVersion = "1.6.21"),
  V_8_11("8.11.0", jdkVersion = JavaSdkVersion.JDK_17, kotlinVersion = "1.6.21"),
  LATEST(null, jdkVersion = JavaSdkVersion.JDK_17, kotlinVersion = "2.2.10"),
}

/**
 * List of available for tests project states.
 * Each state describes which project is it for and which agp version.
 * State can be described as base (all files in project) + patch (only
 * files that change for this state).
 *
 * @param basePath directory for this state base under this project directory (defined in [TestProject]).
 * @param patchPath (optional) directory for this state patch if it is not fully described by base.
 */
enum class AUATestProjectState(
  val project: TestProject,
  val version: TestAGPVersion,
  val minimalState: Boolean,
  val basePath: String,
  val patchPath: String? = null,
) {
  BASIC_4_0(FROM_AGP_40_BASIC, V_4_0, minimalState = true, basePath = "4.0.0"),
  BASIC_4_1(FROM_AGP_40_BASIC, V_4_1, minimalState = true, basePath = "4.0.0", patchPath = "4.1.0"),
  BASIC_4_2(FROM_AGP_40_BASIC, V_4_2, minimalState = true, basePath = "4.0.0", patchPath = "4.2.0"),
  BASIC_7_0(FROM_AGP_40_BASIC, V_7_0, minimalState = true, basePath = "4.0.0", patchPath = "7.0.0"),
  // No changes from 7.0 apart from versions so reuse the same files.
  BASIC_7_1(FROM_AGP_40_BASIC, V_7_1, minimalState = true, basePath = "4.0.0", patchPath = "7.0.0"),
  // No changes from 7.0 apart from versions so reuse the same files.
  BASIC_7_2(FROM_AGP_40_BASIC, V_7_2, minimalState = true, basePath = "4.0.0", patchPath = "7.0.0"),
  // No changes from 7.0 apart from versions so reuse the same files.
  BASIC_7_3(FROM_AGP_40_BASIC, V_7_3, minimalState = true, basePath = "4.0.0", patchPath = "7.0.0"),
  BASIC_8_11_MIN(FROM_AGP_40_BASIC, V_8_11, minimalState = true, basePath = "4.0.0", patchPath = "dev-minimal"),
  BASIC_8_11_FULL(FROM_AGP_40_BASIC, V_8_11, minimalState = false, basePath = "4.0.0", patchPath = "dev-upgraded"),

  ALL_DEPRECATIONS_4_2_MIN(FROM_AGP_40_ALL_DEPRECATIONS, V_4_2, minimalState = true, basePath = "4.2.0-base"),
  ALL_DEPRECATIONS_4_2_FULL(FROM_AGP_40_ALL_DEPRECATIONS, V_4_2, minimalState = false, basePath = "4.2.0-base", patchPath = "4.2.0-upgraded"),
  ALL_DEPRECATIONS_7_0_MIN(FROM_AGP_40_ALL_DEPRECATIONS, V_7_0, minimalState = true, basePath = "4.2.0-base", patchPath = "7.0.0-minimal"),
  ALL_DEPRECATIONS_7_0_FULL(FROM_AGP_40_ALL_DEPRECATIONS, V_7_0, minimalState = false, basePath = "4.2.0-base", patchPath = "7.0.0-upgraded"),
  // No changes from 7.0 apart from versions so reuse the same files.
  ALL_DEPRECATIONS_7_1_MIN(FROM_AGP_40_ALL_DEPRECATIONS, V_7_1, minimalState = true, basePath = "4.2.0-base", patchPath = "7.0.0-minimal"),
  ALL_DEPRECATIONS_7_1_FULL(FROM_AGP_40_ALL_DEPRECATIONS, V_7_1, minimalState = false, basePath = "4.2.0-base", patchPath = "7.1.0-upgraded"),
  // No changes from 7.0 apart from versions so reuse the same files.
  ALL_DEPRECATIONS_7_2_MIN(FROM_AGP_40_ALL_DEPRECATIONS, V_7_2, minimalState = true, basePath = "4.2.0-base", patchPath = "7.0.0-minimal"),
  // No changes from 7.1 apart from versions so reuse the same files.
  ALL_DEPRECATIONS_7_2_FULL(FROM_AGP_40_ALL_DEPRECATIONS, V_7_2, minimalState = false, basePath = "4.2.0-base", patchPath = "7.1.0-upgraded"),
  // No changes from 7.0 apart from versions so reuse the same files.
  ALL_DEPRECATIONS_7_3_MIN(FROM_AGP_40_ALL_DEPRECATIONS, V_7_3, minimalState = true, basePath = "4.2.0-base", patchPath = "7.0.0-minimal"),
  // No changes from 7.1 apart from versions so reuse the same files.
  ALL_DEPRECATIONS_7_3_FULL(FROM_AGP_40_ALL_DEPRECATIONS, V_7_3, minimalState = false, basePath = "4.2.0-base", patchPath = "7.1.0-upgraded"),
  ALL_DEPRECATIONS_8_11_MIN(FROM_AGP_40_ALL_DEPRECATIONS, V_8_11, minimalState = true, basePath = "4.2.0-base", patchPath = "dev-minimal"),
  ALL_DEPRECATIONS_8_11_FULL(FROM_AGP_40_ALL_DEPRECATIONS, V_8_11, minimalState = false, basePath = "4.2.0-base", patchPath = "dev-upgraded"),

  FROM_80_BASIC_8_0(FROM_AGP_80_BASIC, V_8_0, minimalState = true, basePath = "8.0.0"),
  FROM_80_BASIC_LATEST_MIN(FROM_AGP_80_BASIC, LATEST, minimalState = true, basePath = "8.0.0", patchPath = "dev-minimal"),
  FROM_80_BASIC_LATEST_FULL(FROM_AGP_80_BASIC, LATEST, minimalState = false, basePath = "8.0.0", patchPath = "dev-upgraded"),
  ;

  fun projectBasePath() = FileUtils.join(*project.path, basePath)
  fun projectPatchPath() = patchPath?.let { FileUtils.join(*project.path, it) }
}

class UpgradeTestCase(
  val from: AUATestProjectState,
  val to: AUATestProjectState
) {
  override fun toString(): String {
    return "$from to $to"
  }
}

fun allBaseProjectsForCurrentRunner(): List<AUATestProjectState> = AUATestProjectState.values()
  .filter { (OldAgpSuite.AGP_VERSION == null || (it.version.agpVersion ?: "LATEST") == OldAgpSuite.AGP_VERSION) }

fun generateAllTestCases(): List<UpgradeTestCase> {
  val baseProjects = allBaseProjectsForCurrentRunner()
  // For each base need to find all same project higher versions.
  val result = arrayListOf<UpgradeTestCase>()
  baseProjects.filter { it.minimalState }.forEach { base ->
    AUATestProjectState.values()
      .filter { it.project == base.project }
      // Includes upgrades to same version, even if there is nothing to be done.
      .filter { it >= base }
      .forEach { result.add(UpgradeTestCase(base, it)) }
  }
  return result
}

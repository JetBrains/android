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

import com.android.testutils.junit4.OldAgpSuite
import com.android.tools.idea.gradle.project.upgrade.CompatibleGradleVersion
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.LATEST
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_4_0
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_4_1
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_4_2
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_7_0
import com.android.tools.idea.gradle.project.upgrade.integration.TestAGPVersion.V_7_1
import com.android.tools.idea.gradle.project.upgrade.integration.TestProject.ALL_DEPRECATIONS
import com.android.tools.idea.gradle.project.upgrade.integration.TestProject.BASIC
import com.android.utils.FileUtils

/**
 * List of projects available for integration tests.
 * Each project has multiple states for different versions
 * between which we test running upgrades.
 */
enum class TestProject(
  val path: String
){
  BASIC("Basic"),
  ALL_DEPRECATIONS("AllDeprecations")
}

/**
 * Definition of version pairs available for testing.
 * All versions listed here should directly map to old_agp_test targets in BUILD file.
 * If you add a new version here don't forget to add new target to BUILD.
 * There is no validation of this yet.
 */
enum class TestAGPVersion(
  val agpVersion: String?,
  val gradleVersion: CompatibleGradleVersion,
  val kotlinVersion: String = "1.3.72",
) {
  V_4_0("4.0.0", CompatibleGradleVersion.VERSION_6_1_1),
  V_4_1("4.1.0", CompatibleGradleVersion.VERSION_6_5),
  V_4_2("4.2.0", CompatibleGradleVersion.VERSION_6_7_1),
  V_7_0("7.0.0", CompatibleGradleVersion.VERSION_7_0_2),
  V_7_1("7.1.0", CompatibleGradleVersion.VERSION_7_2),
  LATEST(null, CompatibleGradleVersion.VERSION_FOR_DEV, "1.6.21"),
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
  BASIC_4_0(BASIC, V_4_0, minimalState = true, basePath = "4.0.0"),
  BASIC_4_1(BASIC, V_4_1, minimalState = true, basePath = "4.0.0", patchPath = "4.1.0"),
  BASIC_4_2(BASIC, V_4_2, minimalState = true, basePath = "4.0.0", patchPath = "4.2.0"),
  BASIC_7_0(BASIC, V_7_0, minimalState = true, basePath = "4.0.0", patchPath = "7.0.0"),
  // No changes from 7.0 apart from versions so reuse the same files.
  BASIC_7_1(BASIC, V_7_1, minimalState = true, basePath = "4.0.0", patchPath = "7.0.0"),
  BASIC_DEV_MIN(BASIC, LATEST, minimalState = true, basePath = "4.0.0", patchPath = "dev-minimal"),
  BASIC_DEV_FULL(BASIC, LATEST, minimalState = false, basePath = "4.0.0", patchPath = "dev-upgraded"),

  ALL_DEPRECATIONS_4_2_MIN(ALL_DEPRECATIONS, V_4_2, minimalState = true, basePath = "4.2.0-base"),
  ALL_DEPRECATIONS_4_2_FULL(ALL_DEPRECATIONS, V_4_2, minimalState = false, basePath = "4.2.0-base", patchPath = "4.2.0-upgraded"),
  ALL_DEPRECATIONS_7_0_MIN(ALL_DEPRECATIONS, V_7_0, minimalState = true, basePath = "4.2.0-base", patchPath = "7.0.0-minimal"),
  ALL_DEPRECATIONS_7_0_FULL(ALL_DEPRECATIONS, V_7_0, minimalState = false, basePath = "4.2.0-base", patchPath = "7.0.0-upgraded"),
  // No changes from 7.0 apart from versions so reuse the same files.
  ALL_DEPRECATIONS_7_1_MIN(ALL_DEPRECATIONS, V_7_1, minimalState = true, basePath = "4.2.0-base", patchPath = "7.0.0-minimal"),
  ALL_DEPRECATIONS_DEV_MIN(ALL_DEPRECATIONS, LATEST, minimalState = true, basePath = "4.2.0-base", patchPath = "dev-minimal"),
  ALL_DEPRECATIONS_DEV_FULL(ALL_DEPRECATIONS, LATEST, minimalState = false, basePath = "4.2.0-base", patchPath = "dev-upgraded"),
  ;

  fun projectBasePath() = FileUtils.join(project.path, basePath)
  fun projectPatchPath() = patchPath?.let { FileUtils.join(project.path, it) }
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

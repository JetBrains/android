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
package com.android.tools.idea.gradle.project.upgrade.integration

import org.junit.Assert
import org.junit.Test

class BaseProjectsMatchingTargetsTest {

  /**
   * This test prevents someone adding a new upgrade project tests without updating BUILD file correspondingly.
   */
  @Test
  fun bazelTargetsMatchTestSourceFiles() {
    val baseProjectAgpVersions = AUATestProjectState.values().map { it.version.agpVersion ?: "LATEST" }.distinct()
    val definedVersionsInTargets = System.getProperty("agp.version.targets")?.split(":") ?: emptyList()

    println("Agp versions for defined projects: $baseProjectAgpVersions")
    println("Agp versions from defined targets: $definedVersionsInTargets")

    val missingVersions = baseProjectAgpVersions.filterNot { definedVersionsInTargets.contains(it) }

    if (missingVersions.isNotEmpty()) {
      Assert.fail("Missing expected oldAgpTest Bazel targets for AGP versions: ${missingVersions.joinToString(", ")}")
    }
  }
}
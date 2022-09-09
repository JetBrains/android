/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.SdkConstants
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.intellij.openapi.project.Project
import java.io.File

/**
 * A test project definition that can be used in integration tests involving projects set up by Gradle.
 */
interface TestProjectDefinition {
  val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean

  /**
   * Prepares a test project defined by this instance for the given [agpVersion] in the given [integrationTestEnvironment] and returns
   * a handle to open/use the project in the test.
   */
  fun preparedTestProject(
    integrationTestEnvironment: IntegrationTestEnvironment,
    name: String,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
    ndkVersion: String?
  ): PreparedTestProject


  companion object {
    @JvmStatic
    @JvmOverloads
    fun IntegrationTestEnvironment.prepareTestProject(
      testProject: TestProjectDefinition,
      name: String = "project",
      agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.selected,
      ndkVersion: String? = SdkConstants.NDK_DEFAULT_VERSION
    ): PreparedTestProject {
      return testProject.preparedTestProject(this, name, agpVersion, ndkVersion)
    }
  }
}

interface PreparedTestProject {
  fun <T> open(updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions = { it }, body: (Project) -> T): T
  val root: File
}

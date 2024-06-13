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
package com.android.tools.idea.projectsystem

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.projectsystem.runsGradle.GradleProjectSystemIntegrationTestCase
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.ModelVersion
import com.android.tools.idea.testing.applySelectedAgpVersions
import org.jetbrains.annotations.Contract
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OldAgpTest
@RunWith(Parameterized::class)
class GradleProjectSystemOldAgpIntegrationTest : GradleProjectSystemIntegrationTestCase() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun tests(): Collection<*> {
      return tests
        .applySelectedAgpVersions()
        .filter { it.agpVersion >= AGP_35 }
        .filter {
          it.agpVersion.modelVersion == ModelVersion.V2 && (it as TestDefinition).modelsV2 ||
          it.agpVersion.modelVersion == ModelVersion.V1 && (it as TestDefinition).modelsV2.not()
        }
        .map { listOf(it).toTypedArray() }
    }
  }
}
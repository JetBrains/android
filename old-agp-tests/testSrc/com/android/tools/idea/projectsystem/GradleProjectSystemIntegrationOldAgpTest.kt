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

import org.jetbrains.annotations.Contract
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GradleProjectSystemAgp35IntegrationTest : GradleProjectSystemIntegrationTestCase() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> {
      return tests
        .map { it.copy(agpVersion = AgpVersion.AGP_35) }
        .map { listOf(it).toTypedArray() }
    }
  }
}

@RunWith(Parameterized::class)
class GradleProjectSystemAgp40IntegrationTest : GradleProjectSystemIntegrationTestCase() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> {
      return tests
        .map { it.copy(agpVersion = AgpVersion.AGP_40) }
        .map { listOf(it).toTypedArray() }
    }
  }
}

@RunWith(Parameterized::class)
class GradleProjectSystemAgp41IntegrationTest : GradleProjectSystemIntegrationTestCase() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> {
      return tests
        .map { it.copy(agpVersion = AgpVersion.AGP_41) }
        .map { listOf(it).toTypedArray() }
    }
  }
}

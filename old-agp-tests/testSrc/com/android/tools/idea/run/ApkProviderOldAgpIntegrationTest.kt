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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase
import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase.AgpVersion.AGP_35
import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase.AgpVersion.AGP_40
import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase.AgpVersion.AGP_41
import org.jetbrains.annotations.Contract
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ApkProviderAgp35IntegrationTest : ApkProviderIntegrationTestCase() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> {
      return tests
        .map { it.copy(gradleVersion = "5.5", agpVersion = AGP_35, kotlinVersion = "1.4.32") }
        .map { listOf(it).toTypedArray() }
    }
  }
}

@RunWith(Parameterized::class)
class ApkProviderAgp40IntegrationTest : ApkProviderIntegrationTestCase() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> {
      return tests
        .map { it.copy(agpVersion = AGP_40, gradleVersion = "6.5") }
        .map { listOf(it).toTypedArray() }
    }
  }
}

@RunWith(Parameterized::class)
class ApkProviderAgp41IntegrationTest : ApkProviderIntegrationTestCase() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> {
      return tests
        .map { it.copy(agpVersion = AGP_41) }
        .map { listOf(it).toTypedArray() }
    }
  }
}


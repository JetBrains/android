/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants
import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.applicableAgpVersions
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.Parameterized.Parameters
import org.jetbrains.annotations.Contract
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Integration test for Gradle Sync with old versions of Android plugin.
 */
@OldAgpTest
@RunWith(Parameterized::class)
class SyncWithUnsupportedAGPPluginTest(private val environmentDescriptor: AgpVersionSoftwareEnvironmentDescriptor) {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name="{0}")
    fun testParameters(): Collection<*> {
      return applicableAgpVersions().filter { it < AGP_40 }.reversed().map { arrayOf(it) }
    }
  }

  @Test
  fun testGradleSyncFails() {
    var exceptionText: String? = null
    val preparedProject =
      projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = environmentDescriptor)
    preparedProject.open(
      updateOptions = {
        it.copy(
          verifyOpened = { },
          syncExceptionHandler = { e: Exception ->
            exceptionText = e.message
          })
      }
    ) {}
      assertThat(exceptionText).contains(
        "The project is using an incompatible version (AGP ${environmentDescriptor.agpVersion}) of the Android " +
          "Gradle plugin. Minimum supported version is AGP ${SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION}."
      )
    }
  }
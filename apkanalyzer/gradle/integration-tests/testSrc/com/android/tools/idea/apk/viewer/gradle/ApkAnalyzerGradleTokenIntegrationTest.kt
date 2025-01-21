/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer.gradle

import com.android.testutils.truth.PathSubject.paths
import com.android.tools.idea.apk.viewer.ApkAnalyzerToken
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.applicableAgpVersions
import com.android.tools.idea.testing.buildAndAssertSuccess
import com.android.tools.idea.testing.gradleModule

import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ApkAnalyzerGradleTokenIntegrationTest(private val agpVersion: AgpVersionSoftwareEnvironmentDescriptor) {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.Companion.withIntegrationTestEnvironment()

  @Test
  fun testGetDefaultApkFile() {
    projectRule.prepareTestProject(ApkAnalyzerTestProject.SIMPLE_APPLICATION, agpVersion = agpVersion).open {
      project.buildAndAssertSuccess { invoker -> invoker.assemble(arrayOf(project.gradleModule(":app")!!)) }
      val defaultApkFile = ApkAnalyzerToken.Companion.getDefaultApkToAnalyze(project)
      assertThat(defaultApkFile).isNotNull()
      assertThat(defaultApkFile!!.name).isEqualTo("overridden_debug.apk")
      assertAbout(paths()).that(defaultApkFile.toNioPath()).exists()
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun tests(): Collection<AgpVersionSoftwareEnvironmentDescriptor> {
      return applicableAgpVersions().filter { it >= AgpVersionSoftwareEnvironmentDescriptor.AGP_41 } // not supported before AGP 4.1 b/191146142
    }
  }
}

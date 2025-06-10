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
package com.android.tools.idea.gradle.project.sync.runsGradle

import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.roots.ModuleRootManager
import org.junit.Rule
import org.junit.Test

class FakeSourcesTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testFakeSources() {
    val preparedProject = projectRule.prepareTestProject(TestProject.WITH_GRADLE_METADATA)
    preparedProject.open {
      val module = project.findModule("app.main")
      val rootManager = ModuleRootManager.getInstance(module)
      val sourcesRoots = rootManager.orderEntries().sources().roots
      val jars = sourcesRoots.map { it.name }
      // This library is published with doctype="fake-source" to verify that we can find
      // such sources (which in practice includes several androidx libraries)
      assertThat(jars).contains("library-1.1-sources.jar")
    }
  }
}
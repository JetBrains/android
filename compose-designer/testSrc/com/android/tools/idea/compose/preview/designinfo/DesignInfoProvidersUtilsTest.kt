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
package com.android.tools.idea.compose.preview.designinfo

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesignInfoProvidersUtilsTest {
  @get:Rule val rule = AndroidProjectRule.inMemory()

  private val project
    get() = rule.project
  private val module
    get() = rule.module
  private lateinit var testProjectSystem: TestProjectSystem

  @Before
  fun setup() {
    StudioFlags.COMPOSE_CONSTRAINT_VISUALIZATION.override(true)
    testProjectSystem = TestProjectSystem(project)
    runInEdtAndWait { testProjectSystem.useInTests() }
  }

  @After
  fun teardown() {
    StudioFlags.COMPOSE_CONSTRAINT_VISUALIZATION.clearOverride()
  }

  @Test
  fun noDesignInfoProvidersWithUnsupportedRelease() {
    assertFalse(hasDesignInfoProviders(module))
    addConstraintLayoutDependency("1.0.0-alpha05")
    assertFalse(hasDesignInfoProviders(module))
    testProjectSystem.emulateSync(ProjectSystemSyncManager.SyncResult.SUCCESS)
    assertFalse(hasDesignInfoProviders(module))
  }

  @Test
  fun hasDesignInfoProvidersWithAlphaRelease() {
    checkHasDesignInfoProviderAfterSync("1.0.0-alpha06")
  }

  @Test
  fun hasDesignInfoProvidersWithAlphaRelease2() {
    checkHasDesignInfoProviderAfterSync("1.0.1-alpha01")
  }

  @Test
  fun hasDesignInfoProvidersWithBetaRelease() {
    checkHasDesignInfoProviderAfterSync("1.0.0-beta01")
  }

  @Test
  fun hasDesignInfoProvidersWithStableRelease() {
    checkHasDesignInfoProviderAfterSync("1.0.0")
  }

  private fun checkHasDesignInfoProviderAfterSync(version: String) {
    assertFalse(hasDesignInfoProviders(module))
    addConstraintLayoutDependency(version)
    assertFalse(hasDesignInfoProviders(module))
    testProjectSystem.emulateSync(ProjectSystemSyncManager.SyncResult.SUCCESS)
    assertTrue(hasDesignInfoProviders(module))
  }

  private fun addConstraintLayoutDependency(version: String) =
    testProjectSystem.addDependency(
      GoogleMavenArtifactId.ANDROIDX_CONSTRAINT_LAYOUT_COMPOSE,
      module,
      GradleVersion.parse(version)
    )
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.templates.IdeGoogleMavenRepository
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat

/**
 * Integration tests for [GradleProjectSystem]; contains tests that require a working gradle project.
 */
class GradleProjectSystemIntegrationTest : AndroidGradleTestCase() {

  @Throws(Exception::class)
  fun testGetAvailableDependency() {
    loadSimpleApplication()
    val projectSystem = project.getProjectSystem()
    val availableAppCompatVersion = IdeGoogleMavenRepository.findVersion("com.android.support", "appcompat-v7")
    val availableAppCompat = GradleCoordinate("com.android.support", "appcompat-v7", availableAppCompatVersion.toString())

    assertThat(isSameArtifact(
      projectSystem.getAvailableDependency(GradleCoordinate("com.android.support", "appcompat-v7", "+")),
      availableAppCompat
    )).isTrue()

    assertThat(isSameArtifact(
      projectSystem.getAvailableDependency(GradleCoordinate("com.android.support", "appcompat-v7", availableAppCompatVersion.toString())),
      availableAppCompat
    )).isTrue()
  }

  @Throws(Exception::class)
  fun testGetAvailableDependencyWhenUnavailable() {
    loadSimpleApplication()
    val projectSystem = project.getProjectSystem()

    assertThat(projectSystem.getAvailableDependency(GradleCoordinate("com.android.support", "appcompat-v7", "99.9.9"))).isNull()
    assertThat(projectSystem.getAvailableDependency(GradleCoordinate("nonexistent", "dependency123", "+"))).isNull()
  }

  private fun isSameArtifact(first: GradleCoordinate?, second: GradleCoordinate?) =
    GradleCoordinate.COMPARE_PLUS_LOWER.compare(first, second) == 0
}

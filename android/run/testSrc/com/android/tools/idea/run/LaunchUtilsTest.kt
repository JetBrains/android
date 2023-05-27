/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.run.util.LaunchUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Truth
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test

class LaunchUtilsTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testActivity() {
    projectRule.prepareTestProject(AndroidCoreTestProject.RUN_CONFIG_ACTIVITY).open {
      Truth.assertThat(LaunchUtils.isWatchFeatureRequired(AndroidFacet.getInstance(project.gradleModule(":")!!)!!)).isFalse()
    }
  }

  @Test
  fun testActivityAlias() {
    projectRule.prepareTestProject(AndroidCoreTestProject.RUN_CONFIG_ALIAS).open {
      Truth.assertThat(LaunchUtils.isWatchFeatureRequired(AndroidFacet.getInstance(project.gradleModule(":")!!)!!)).isFalse()
    }
  }

  @Test
  fun testWatchFaceService() {
    projectRule.prepareTestProject(AndroidCoreTestProject.RUN_CONFIG_WATCHFACE).open {
      Truth.assertThat(LaunchUtils.isWatchFeatureRequired(AndroidFacet.getInstance(project.gradleModule(":")!!)!!)).isTrue()
    }
  }
}

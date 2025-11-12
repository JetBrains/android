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

import com.android.sdklib.AndroidApiLevel
import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.util.KotlinGradleProjectSystemUtil
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.withCompileSdk
import com.android.tools.idea.testing.withKotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * Moved from [com.android.tools.idea.gradle.util.runsGradle.GradleProjectSystemUtilSoftwareVersionsTest]
 * because pre 2.0.0 KGP is not supported by Gradle 9.0.
 */
@OldAgpTest(gradleVersions = ["8.13"], agpVersions = ["8.12.0"])
class OldKotlinTest() {

  private val agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_8_12.withCompileSdk(AndroidApiLevel(35))

  @get:Rule
  val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = agpVersion)

  @Test
  fun testOldKotlin() {
    projectRule.load(TestProjectPaths.KOTLIN_KAPT, agpVersion.withKotlin("1.6.21"))

    val kotlinVersionInUse = KotlinGradleProjectSystemUtil.getKotlinVersionsInUse(projectRule.project,
                                                                                  projectRule.project.basePath!!)?.firstOrNull()?.toString()
    assertThat(kotlinVersionInUse).isNotNull()
    assertThat(kotlinVersionInUse).isEqualTo("1.6.21")
  }
}
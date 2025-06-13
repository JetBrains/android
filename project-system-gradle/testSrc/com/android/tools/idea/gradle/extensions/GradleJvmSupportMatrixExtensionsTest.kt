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
package com.android.tools.idea.gradle.extensions

import com.android.tools.idea.gradle.util.CompatibleGradleVersion
import com.android.tools.idea.jdk.JavaVersionLts
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GradleJvmSupportMatrixExtensionsTest(private val gradleVersion: GradleVersion) : LightPlatformTestCase() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): List<GradleVersion> {
      return CompatibleGradleVersion.entries.map { it.version }
    }
  }

  @Test
  fun `Gradle recommended java version` () {
    val recommendedNonLtsVersion = GradleJvmSupportMatrix.getRecommendedJavaVersion(gradleVersion, false)
    val expectedRecommendedVersion = gradleVersion.supportedJavaVersions.last()
    assertEquals(expectedRecommendedVersion, recommendedNonLtsVersion)
  }

  @Test
  fun `Gradle recommended java version considering only LTS` () {
    val recommendedLtsVersion = GradleJvmSupportMatrix.getRecommendedJavaVersion(gradleVersion, true)
    val expectedRecommendedVersion = gradleVersion.supportedJavaVersions.last {
      JavaVersionLts.isLtsVersion(it)
    }
    assertEquals(expectedRecommendedVersion, recommendedLtsVersion)
  }

  private val GradleVersion.supportedJavaVersions: List<JavaVersion>
    get() = GradleJvmSupportMatrix.getSupportedJavaVersions(this)
      .filter { it.feature <= IdeSdks.DEFAULT_JDK_VERSION.maxLanguageLevel.feature() }
}
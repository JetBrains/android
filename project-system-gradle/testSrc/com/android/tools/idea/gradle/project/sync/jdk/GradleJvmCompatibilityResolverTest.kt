/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.jdk

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion

class GradleJvmCompatibilityResolverTest : LightPlatformTestCase() {

  fun `test resolve jvm compatibility for old Gradle with expected versions`() {
    val expectedGradleVersion = GradleVersion.version("8.0")
    GradleJvmCompatibilityResolver.resolve(project, expectedGradleVersion).run {
      assertEquals(expectedGradleVersion, gradleVersion)
      assertEquals(JavaVersion.compose(8), minimumSupportedJavaVersion)
      assertEquals(JavaVersion.compose(19), maximumSupportedJavaVersion)
      assertEquals(JavaVersion.compose(17), recommendedJavaVersion)
    }
  }

  fun `test resolve jvm compatibility for gradle with expected versions`() {
    val expectedGradleVersion = GradleVersion.version("9.1.0")
    GradleJvmCompatibilityResolver.resolve(project, expectedGradleVersion).run {
      assertEquals(expectedGradleVersion, gradleVersion)
      assertEquals(JavaVersion.compose(17), minimumSupportedJavaVersion)
      assertEquals(JavaVersion.compose(25), maximumSupportedJavaVersion)
      assertEquals(JavaVersion.compose(21), recommendedJavaVersion)
    }
  }
}

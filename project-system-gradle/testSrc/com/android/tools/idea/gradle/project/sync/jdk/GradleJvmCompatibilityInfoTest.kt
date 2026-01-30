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

import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleJvmCompatibilityInfoTest {

  @Test
  fun `test minimum and maximum SupportedJavaVersion returns first and last element respectively`() {
    val info =
      GradleJvmCompatibilityInfo(
        GradleVersion.version("9.0.0"),
        JavaVersion.compose(17),
        listOf(JavaVersion.compose(11), JavaVersion.compose(17)),
      )
    assertEquals(JavaVersion.compose(11), info.minimumSupportedJavaVersion)
    assertEquals(JavaVersion.compose(17), info.maximumSupportedJavaVersion)
    assertEquals(JavaVersion.compose(17), info.recommendedJavaVersion)
  }

  @Test
  fun `test minimum and maximum SupportedJavaVersion returns null when list is empty`() {
    val info = GradleJvmCompatibilityInfo(GradleVersion.version("9.0.0"), JavaVersion.compose(11), emptyList())
    assertNull(info.minimumSupportedJavaVersion)
    assertNull(info.maximumSupportedJavaVersion)
    assertEquals(JavaVersion.compose(11), info.recommendedJavaVersion)
  }

  @Test
  fun `test isCompatible returns true for supported version`() {
    val info =
      GradleJvmCompatibilityInfo(
        GradleVersion.version("8.0"),
        JavaVersion.compose(17),
        listOf(JavaVersion.compose(11), JavaVersion.compose(17)),
      )
    assertTrue(info.isCompatible(JavaVersion.compose(11)))
    assertTrue(info.isCompatible(JavaVersion.compose(17)))
  }

  @Test
  fun `test isCompatible returns false for unsupported version`() {
    val info =
      GradleJvmCompatibilityInfo(
        GradleVersion.version("8.0"),
        JavaVersion.compose(17),
        listOf(JavaVersion.compose(11), JavaVersion.compose(17)),
      )
    assertFalse(info.isCompatible(JavaVersion.compose(8)))
    assertFalse(info.isCompatible(JavaVersion.compose(21)))
  }
}

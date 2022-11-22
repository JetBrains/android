/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.lint.model

import com.android.tools.lint.model.DefaultLintModelMavenName
import com.google.common.truth.Truth.assertThat
import org.junit.Test

// Most of the tests for LintModelFactory are in LintModelSnapshotComparisonTest
class LintModelFactoryTest {
  @Test
  fun testNonMavenArtifact() {
    // 259832798: Android linter warnings are not shown due to bug in AndroidLintIdeClient.findRuleJars
    assertThat(
      LintModelFactory.getMavenName("androidx.databinding:viewbinding:7.3.1@aar")).isEqualTo(
      DefaultLintModelMavenName("androidx.databinding", "viewbinding", "7.3.1")
    )

    assertThat(
      LintModelFactory.getMavenName("/some/path/to/lib/build/libs/lib-all.jar")).isEqualTo(
      DefaultLintModelMavenName("__non_maven__", "/some/path/to/lib/build/libs/lib-all.jar")
    )
  }
}
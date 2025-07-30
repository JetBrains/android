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
package com.android.tools.idea.flags.overrides

import com.android.tools.idea.flags.FeatureConfiguration
import com.google.common.truth.Truth
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test

class FeatureConfigurationTest {

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun `verify configurations from version`() {
    assert(formatFullVersion(channel = "dEv")).isEqualTo(FeatureConfiguration.INTERNAL)
    assert(formatFullVersion()).isEqualTo(FeatureConfiguration.INTERNAL)


    assert(formatFullVersion(channel = "Nightly")).isEqualTo(FeatureConfiguration.NIGHTLY)
    assert(formatFullVersion(channel = "Canary")).isEqualTo(FeatureConfiguration.PREVIEW)
    assert(formatFullVersion(channel = "Beta")).isEqualTo(FeatureConfiguration.STABLE)
    assert(formatFullVersion(channel = "RC")).isEqualTo(FeatureConfiguration.STABLE)
    assert(formatFullVersion(channel = "Stable")).isEqualTo(FeatureConfiguration.STABLE)
  }

  @Test
  fun `test unit test`() {
    Truth.assertThat(FeatureConfiguration.computeConfiguration()).isEqualTo(FeatureConfiguration.INTERNAL)
  }
}

private fun assert(versionString: String) = Truth
  .assertWithMessage(versionString)
  .that(FeatureConfiguration.getConfigurationFromVersionName(versionString))

private fun formatFullVersion(
  majorVersion: String = "2023",
  minorVersion: String = "1",
  microVersion: String = "1",
  channel: String = "Dev",
): String = "Iguana | ${majorVersion}.${minorVersion}.${microVersion} $channel"

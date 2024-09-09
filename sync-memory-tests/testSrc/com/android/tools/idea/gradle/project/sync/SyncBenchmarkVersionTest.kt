/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.gradle.Version
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironment
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.BuildEnvironment
import com.google.common.truth.Truth
import org.gradle.util.GradleVersion
import org.junit.Test

class SyncBenchmarkVersionTest {

  @Test
  fun `gradle snapshot benchmarks are ahead of latest`() {
    val benchmarkVersions = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST_GRADLE_SNAPSHOT
    val latestVersions = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST

    Truth.assertThat(benchmarkVersions.resolvedAgpVersion() >= latestVersions.resolvedAgpVersion()).isTrue()
    Truth.assertThat(benchmarkVersions.resolvedGradleVersion() >= latestVersions.resolvedGradleVersion()).isTrue()
    Truth.assertThat(benchmarkVersions.resolvedKotlinVersion() >= latestVersions.resolvedKotlinVersion()).isTrue()
  }

  @Test
  fun `kotlin snapshot benchmarks are ahead of latest`() {
    val benchmarkVersions = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST_KOTLIN_SNAPSHOT
    val latestVersions = AgpVersionSoftwareEnvironmentDescriptor.AGP_LATEST

    Truth.assertThat(benchmarkVersions.resolvedAgpVersion() >= latestVersions.resolvedAgpVersion()).isTrue()
    Truth.assertThat(benchmarkVersions.resolvedGradleVersion() >= latestVersions.resolvedGradleVersion()).isTrue()
    Truth.assertThat(benchmarkVersions.resolvedKotlinVersion() >= latestVersions.resolvedKotlinVersion()).isTrue()
  }

  fun AgpVersionSoftwareEnvironment.resolvedAgpVersion() = AgpVersion.parse(agpVersion ?: BuildEnvironment.getInstance().gradlePluginVersion)
  fun AgpVersionSoftwareEnvironment.resolvedGradleVersion() = GradleVersion.version(gradleVersion ?: SdkConstants.GRADLE_LATEST_VERSION)
  fun AgpVersionSoftwareEnvironment.resolvedKotlinVersion() = Version.parse(kotlinVersion ?: KOTLIN_VERSION_FOR_TESTS)
}
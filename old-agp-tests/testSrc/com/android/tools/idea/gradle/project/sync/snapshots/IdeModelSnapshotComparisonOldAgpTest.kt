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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.testutils.junit4.OldAgpSuite
import com.android.testutils.junit4.OldAgpTest
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.annotations.Contract
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OldAgpTest(agpVersions = ["4.1", "4.2", "7.0", "7.1", "7.2"], gradleVersions = ["6.5", "6.7.1", "7.0.2", "7.2", "7.3.3"])
@RunsInEdt
@RunWith(Parameterized::class)
class IdeModelSnapshotComparisonOldAgpTest : IdeModelSnapshotComparisonTest() {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{1}\${0}\${2}")
    fun testProjects(): Collection<*> = testProjectsFor(
      AgpVersion.values().filter { (OldAgpSuite.AGP_VERSION == null || it.legacyAgpVersion == OldAgpSuite.AGP_VERSION) && it != AgpVersion.CURRENT }.zip(
      GradleVersion.values().filter { (OldAgpSuite.GRADLE_VERSION == null || it.legacyGradleVersion == OldAgpSuite.GRADLE_VERSION) && it != GradleVersion.CURRENT }).toMap()
    )
  }
}
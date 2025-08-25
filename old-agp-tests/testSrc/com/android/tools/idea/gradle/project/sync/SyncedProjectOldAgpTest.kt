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
@file:Suppress("ClassName")

package com.android.tools.idea.gradle.project.sync

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTest
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectTest
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_33
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35_JDK_8
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_42
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_70
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_71
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72_V1
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_73
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_74
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_80
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_81
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_82
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_83
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_84
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_85
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_86
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_87
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_88
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_89
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_8_10
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_8_11
import com.android.tools.idea.testing.applicableAgpVersions
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.annotations.Contract
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A parameterised test that runs [SyncedProjectTest] in all requested environments.
 *
 * When running in bazel it is a specific version of the AGP and when running in the IDE it is all known versions.
 */
@OldAgpTest
@RunsInEdt
@RunWith(Parameterized::class)
class SyncedProjectsAllAgpTest(agpVersion: AgpVersionSoftwareEnvironmentDescriptor) : SyncedProjectTest(agpVersion = agpVersion) {
  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*> {
      return applicableAgpVersions().filter { it >= AGP_40 }.reversed().map { arrayOf(it) }
    }
  }
}

// Convenience test classes to run tests in the IDE. These classes are explicitly excluded from running in bazel in [OldAgpTests] since
// it relies on [SyncedProjectsAllAgpTest] to run tests in the requested environment.

class SyncProject_AGP_33Test: SyncedProjectTest(agpVersion = AGP_33)
class SyncProject_AGP_35Test: SyncedProjectTest(agpVersion = AGP_35)
class SyncProject_AGP_40Test: SyncedProjectTest(agpVersion = AGP_40)
class SyncProject_AGP_41Test: SyncedProjectTest(agpVersion =  AGP_41)
class SyncProject_AGP_42Test: SyncedProjectTest(agpVersion = AGP_42)
class SyncProject_AGP_70Test: SyncedProjectTest(agpVersion = AGP_70)
class SyncProject_AGP_71Test: SyncedProjectTest(agpVersion = AGP_71)
class SyncProject_AGP_72_V1Test: SyncedProjectTest(agpVersion = AGP_72_V1)
class SyncProject_AGP_72Test: SyncedProjectTest(agpVersion = AGP_72)
class SyncProject_AGP_73Test: SyncedProjectTest(agpVersion = AGP_73)
class SyncProject_AGP_74Test: SyncedProjectTest(agpVersion = AGP_74)
class SyncProject_AGP_80Test: SyncedProjectTest(agpVersion = AGP_80)
class SyncProject_AGP_81Test: SyncedProjectTest(agpVersion = AGP_81)
class SyncProject_AGP_82Test: SyncedProjectTest(agpVersion = AGP_82)
class SyncProject_AGP_83Test: SyncedProjectTest(agpVersion = AGP_83)
class SyncProject_AGP_84Test: SyncedProjectTest(agpVersion = AGP_84)
class SyncProject_AGP_85Test: SyncedProjectTest(agpVersion = AGP_85)
class SyncProject_AGP_86Test: SyncedProjectTest(agpVersion = AGP_86)
class SyncProject_AGP_87Test: SyncedProjectTest(agpVersion = AGP_87)
class SyncProject_AGP_88Test: SyncedProjectTest(agpVersion = AGP_88)
class SyncProject_AGP_89Test: SyncedProjectTest(agpVersion = AGP_89)
class SyncProject_AGP_8_10_Test: SyncedProjectTest(agpVersion = AGP_8_10)
class SyncProject_AGP_8_11_Test: SyncedProjectTest(agpVersion = AGP_8_11)


class OldAgpTestProjectTest: TestProjectTest() {
  @Test
  @OldAgpTest(agpVersions = ["7.1.0"], gradleVersions = ["7.2"])
  override fun testMigratePackageAttribute_agp71() {
    super.testMigratePackageAttribute_agp71()
  }

  @Test
  @OldAgpTest(agpVersions = ["8.0.2"], gradleVersions = ["8.0"])
  override fun testMigratePackageAttribute_agp80() {
    super.testMigratePackageAttribute_agp80()
  }
}
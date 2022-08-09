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
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_32
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_42
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_70
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_71
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72_V1
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_73
import com.android.tools.idea.testing.applicableAgpVersions
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.annotations.Contract
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
      return applicableAgpVersions().filter { it >= AGP_35 }.reversed().map { arrayOf(it) }
    }
  }
}

// Convenience test classes to run tests in the IDE. These classes are explicitly excluded from running in bazel in [OldAgpTests] since
// it relies on [SyncedProjectsAllAgpTest] to run tests in the requested environment.

class SyncProject_AGP_32Test: SyncedProjectTest(agpVersion = AGP_32)
class SyncProject_AGP_35Test: SyncedProjectTest(agpVersion = AGP_35)
class SyncProject_AGP_40Test: SyncedProjectTest(agpVersion = AGP_40)
class SyncProject_AGP_41Test: SyncedProjectTest(agpVersion =  AGP_41)
class SyncProject_AGP_42Test: SyncedProjectTest(agpVersion = AGP_42)
class SyncProject_AGP_70Test: SyncedProjectTest(agpVersion = AGP_70)
class SyncProject_AGP_71Test: SyncedProjectTest(agpVersion = AGP_71)
class SyncProject_AGP_72_V1Test: SyncedProjectTest(agpVersion = AGP_72_V1)
class SyncProject_AGP_72Test: SyncedProjectTest(agpVersion = AGP_72)
class SyncProject_AGP_73Test: SyncedProjectTest(agpVersion = AGP_73)
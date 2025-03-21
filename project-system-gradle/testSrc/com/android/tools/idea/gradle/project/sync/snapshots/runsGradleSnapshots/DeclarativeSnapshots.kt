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
package com.android.tools.idea.gradle.project.sync.snapshots.runsGradleSnapshots

import com.android.tools.idea.gradle.project.sync.declarative.DeclarativeSchemaModelTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestBase
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.DeclarativeTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_DECLARATIVE_GRADLE_SNAPSHOT
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DeclarativeSnapshots(val version: AgpVersionSoftwareEnvironmentDescriptor) :
  SyncedProjectTestBase<DeclarativeTestProject>(agpVersion = version) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testParameters(): Collection<*> {
      return listOf(AGP_DECLARATIVE_GRADLE_SNAPSHOT,
                    // making sure we can consume schema for stable Gradle version
                    AGP_CURRENT)
    }
  }

  override fun getTestDefs(testProject: DeclarativeTestProject): List<SyncedProjectTestDef> {
    return DeclarativeSchemaModelTestDef.tests
  }
  @Test
  fun testSchemaModel() = testProject(DeclarativeTestProject.DECLARATIVE_ANDROID)
}
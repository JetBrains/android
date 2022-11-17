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
package com.android.tools.idea.gradle.project.sync

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

@OldAgpTest(agpVersions = ["4.0.0"], gradleVersions = ["6.7.1"])
class MakeBeforeRunTaskProviderIntegrationOldAgpTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @Test
  fun testWatchFaceProject() {
    val preparedProject = projectRule.prepareTestProject(
      testProject = AndroidCoreTestProject.WEAR_WATCHFACE,
      agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_40,
    )
    preparedProject.open { project ->
      val result = project.buildAndWait {buildInvoker ->
        buildInvoker.assemble(TestCompileType.ALL)
      }
      assertThat(result.isBuildSuccessful).isTrue()
    }
  }
}

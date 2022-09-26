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
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
@OldAgpTest(agpVersions = ["3.5.0"], gradleVersions = ["5.5"])
class Jvm1_8SyncTest {
  @Test
  fun test18() {
    val preparedProject =
      projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35_JDK_8)
    preparedProject.open { project ->
      val projectSdk = ProjectRootManager.getInstance(project).projectSdk
      expect.that(projectSdk?.version).isEqualTo(JDK_1_8)
    }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()
}
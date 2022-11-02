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
package com.android.tools.idea.gradle.project.build

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildAndWait
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_8
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.util.projectStructure.version
import org.junit.Rule
import org.junit.Test

@RunsInEdt
@OldAgpTest(agpVersions = ["3.5.0"], gradleVersions = ["5.5"])
class Jvm8BuildTest {
  /**
   * Confirm that a build that uses OutputBuildAction (like bundle) succeeds when using AGP 3.5 with JDK 8 (b/248658503)
   */
  @Test
  fun testJvm8Bundle() {
    val preparedProject =
      projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35_JDK_8)
    var buildResult: AssembleInvocationResult? = null

    preparedProject.open { project ->
      val projectSdk = ProjectRootManager.getInstance(project).projectSdk
      expect.that(projectSdk?.version).isEqualTo(JDK_1_8)

      fun buildEventHandler(event: BuildEvent) {
        (event as? OutputBuildEvent)?.let {println(it.message)}
      }

      buildResult = project.buildAndWait(eventHandler = ::buildEventHandler) { buildInvoker ->
        buildInvoker.bundle(arrayOf(project.gradleModule(":app")!!))
      }
    }
    buildResult?.let {expect.that(it.isBuildSuccessful).isTrue()} ?: expect.fail("buildResult should not be null")
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()
}
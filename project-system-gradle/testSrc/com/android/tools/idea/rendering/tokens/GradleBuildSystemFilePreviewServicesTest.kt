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
package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState.GRADLE_BUILD_TOPIC
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.Companion.openTestProject
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.gradleModule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

typealias GradleBuildMode = com.android.tools.idea.gradle.util.BuildMode

@RunWith(Enclosed::class)
class GradleBuildSystemFilePreviewServicesTest {

  @RunWith(JUnit4::class)
  class BuildTargets {

    @get:Rule
    val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

    @Test
    fun `build targets can be obtained`() {
      projectRule.openTestProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION) {
        val appModule = project.gradleModule(":app")!!.getMainModule()
        BuildTargetReference.gradleOnly(appModule)
        BuildTargetReference.from(appModule, appModule.fileUnderGradleRoot("src/main/java/google/simpleapplication/MyActivity.java")!!)
      }
    }
  }


  @RunWith(JUnit4::class)
  class Listener  {
    @get:Rule
    val projectRule = ProjectRule()

    @get:Rule
    val otherProjectRule = ProjectRule()

    private val project get() = projectRule.project
    private val otherProject get() = otherProjectRule.project
    lateinit var services: GradleBuildSystemFilePreviewServices

    private class CapturingBuildListener : BuildSystemFilePreviewServices.BuildListener {
      var capturedMode: BuildMode? = null
      var capturedResult: ListenableFuture<BuildSystemFilePreviewServices.BuildListener.BuildResult>? = null

      override fun buildStarted(
        buildMode: BuildMode,
        buildResult: ListenableFuture<BuildSystemFilePreviewServices.BuildListener.BuildResult>
      ) {
        capturedMode = buildMode
        capturedResult = buildResult
      }
    }

    @Before
    fun setup() {
      services = GradleBuildSystemFilePreviewServices()
    }

    @Test
    fun `republishes build started`() {
      val listener = CapturingBuildListener()
      services.subscribeBuildListener(project, project, listener)

      project.messageBus.syncPublisher(GRADLE_BUILD_TOPIC)
        .buildStarted(createBuildContext(GradleBuildMode.COMPILE_JAVA))

      assertThat(listener.capturedMode).isEqualTo(BuildMode.COMPILE)
      assertThat(listener.capturedResult?.isDone).isFalse()
    }

    @Test
    fun `ignores other projects`() {
      val listener = CapturingBuildListener()
      services.subscribeBuildListener(project, project, listener)

      otherProject.messageBus.syncPublisher(GRADLE_BUILD_TOPIC)
        .buildStarted(createBuildContext(GradleBuildMode.COMPILE_JAVA))

      assertThat(listener.capturedMode).isNull()
      assertThat(listener.capturedResult).isNull()
    }

    @Test
    fun `ignores unrelated build modes`() {
      val listener = CapturingBuildListener()
      services.subscribeBuildListener(project, project, listener)

      project.messageBus.syncPublisher(GRADLE_BUILD_TOPIC)
        .buildStarted(createBuildContext(GradleBuildMode.SOURCE_GEN))

      assertThat(listener.capturedMode).isNull()
      assertThat(listener.capturedResult).isNull()
    }

    @Test
    fun `republishes build finished`() {
      val listener = CapturingBuildListener()
      services.subscribeBuildListener(project, project, listener)

      val publisher = project.messageBus.syncPublisher(GRADLE_BUILD_TOPIC)

      val context = createBuildContext(GradleBuildMode.COMPILE_JAVA)
      publisher.buildStarted(context)
      publisher.buildFinished(BuildStatus.SUCCESS, context)

      assertThat(listener.capturedMode).isEqualTo(BuildMode.COMPILE)
      assertThat(listener.capturedResult?.isDone).isTrue()
      assertThat(listener.capturedResult?.get()?.status).isEqualTo(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    }

    @Test
    fun `multiple listeners work`() {
      val listener1 = CapturingBuildListener()
      val listener2 = CapturingBuildListener()
      services.subscribeBuildListener(project, project, listener1)
      services.subscribeBuildListener(project, project, listener2)

      val publisher = project.messageBus.syncPublisher(GRADLE_BUILD_TOPIC)

      val context = createBuildContext(GradleBuildMode.COMPILE_JAVA)
      publisher.buildStarted(context)
      publisher.buildFinished(BuildStatus.SUCCESS, context)

      assertThat(listener1.capturedMode).isEqualTo(BuildMode.COMPILE)
      assertThat(listener1.capturedResult?.isDone).isTrue()
      assertThat(listener1.capturedResult?.get()?.status).isEqualTo(ProjectSystemBuildManager.BuildStatus.SUCCESS)
      assertThat(listener2.capturedMode).isEqualTo(BuildMode.COMPILE)
      assertThat(listener2.capturedResult?.isDone).isTrue()
      assertThat(listener2.capturedResult?.get()?.status).isEqualTo(ProjectSystemBuildManager.BuildStatus.SUCCESS)
    }

    @Test
    fun `ignores build finished if started late`() {
      val listener = CapturingBuildListener()
      services.subscribeBuildListener(project, project, listener)

      val publisher = project.messageBus.syncPublisher(GRADLE_BUILD_TOPIC)

      val context = createBuildContext(com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA)
      publisher.buildFinished(BuildStatus.SUCCESS, context)

      // Does not crash.
      assertThat(listener.capturedMode).isNull()
      assertThat(listener.capturedResult).isNull()
    }

    private fun createBuildContext(buildMode: com.android.tools.idea.gradle.util.BuildMode) = BuildContext(
      GradleBuildInvoker.Request.builder(project, File("/"))
        .setMode(buildMode)
        .build()
    )
  }
}

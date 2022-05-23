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
package com.android.tools.idea.gradle.task

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Expect
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class AndroidGradleTaskManagerTest : GradleIntegrationTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath

  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData"
  override fun getAdditionalRepos(): Collection<File> = emptyList()

  @Test
  fun `app assembleDebug from root and app`() {
    val path = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { project ->
      val capturedRequests = hookExecuteTasks(project)
      val facade = ApplicationManager.getApplication().getService(ExternalSystemFacadeManager::class.java)
        .getFacade(project, path.absolutePath, GradleConstants.SYSTEM_ID)

      val externalSystemTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
      // 1) This is a common form used by Android Studio etc.
      facade.taskManager.executeTasks(externalSystemTaskId, listOf(":app:assembleDebug"), path.absolutePath, null, null)
      // 2) This is a way in which tasks are invoked from the Gradle tool window and from Gradle run configurations, if configured this way.
      facade.taskManager.executeTasks(externalSystemTaskId, listOf("assembleDebug"), path.resolve("app").absolutePath, null, null)

      expect.that(capturedRequests).hasSize(2)

      expect.that(capturedRequests.getOrNull(0)?.taskId).isSameAs(externalSystemTaskId)
      expect.that(capturedRequests.getOrNull(0)?.project).isSameAs(project)
      expect.that(capturedRequests.getOrNull(0)?.rootProjectPath).isEqualTo(path)
      expect.that(capturedRequests.getOrNull(0)?.gradleTasks).isEqualTo(listOf(":app:assembleDebug"))

      expect.that(capturedRequests.getOrNull(1)?.taskId).isSameAs(externalSystemTaskId)
      expect.that(capturedRequests.getOrNull(1)?.project).isSameAs(project)
      expect.that(capturedRequests.getOrNull(1)?.rootProjectPath).isEqualTo(path.resolve("app"))
      expect.that(capturedRequests.getOrNull(1)?.gradleTasks).isEqualTo(listOf("assembleDebug"))
    }
  }

  private fun hookExecuteTasks(project: Project): List<GradleBuildInvoker.Request> {
    val buildInvoker = GradleBuildInvoker.getInstance(project)
    val requests = mutableListOf<GradleBuildInvoker.Request>()
    val requestCapturingInvoker = object : GradleBuildInvoker by buildInvoker {
      override fun executeTasks(request: GradleBuildInvoker.Request): ListenableFuture<GradleInvocationResult> {
        requests.add(request)
        return buildInvoker.executeTasks(request)
      }
    }
    project.replaceService(GradleBuildInvoker::class.java, requestCapturingInvoker, project)
    return requests
  }
}
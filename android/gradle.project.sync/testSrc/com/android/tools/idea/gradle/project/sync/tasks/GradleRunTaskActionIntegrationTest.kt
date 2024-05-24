/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.tasks

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.ide.DataManager
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.ActionPlaces.TOOLWINDOW_GRADLE
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.action.task.RunExternalSystemTaskAction
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DEFAULT_TASK
import org.jetbrains.plugins.gradle.util.GradleTaskClassifier
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JPanel

@RunsInEdt
class GradleRunTaskActionIntegrationTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION).onEdt()

  private val project: Project
    get() = androidProjectRule.project
  private val fixture: CodeInsightTestFixture
    get() = androidProjectRule.fixture

  @Test
  fun `Given assembleDebug task When executing from Gradle tool window Then no exception is thrown`() {
    executeGradleTask("assembleDebug")
  }

  @Test
  fun `Given app assembleDebug task When executing from Gradle tool window Then no exception is thrown`() {
    executeGradleTask("assembleDebug", project.basePath.plus("/app"))
  }

  private fun executeGradleTask(taskName: String, linkedExternalProjectPath: @SystemIndependent String = project.basePath.toString()) {
    val gradleTaskListener = TestGradleTaskListener()
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(gradleTaskListener, project)
    TestGradleTaskAction.executeTask(project, fixture, taskName, linkedExternalProjectPath)
    gradleTaskListener.capturedException?.let {
      throw it
    }
  }

  private class TestGradleTaskListener: ExternalSystemTaskNotificationListenerAdapter() {
    var capturedException: Exception? = null

    override fun onFailure(id: ExternalSystemTaskId, exception: Exception) {
      capturedException = exception
    }
  }

  private object TestGradleTaskAction : RunExternalSystemTaskAction() {
    fun executeTask(
      project: Project,
      fixture: CodeInsightTestFixture,
      taskName: String,
      linkedExternalProjectPath: @SystemIndependent String
    ) {
      val gradleTaskActionEvent = mock<AnActionEvent>().apply {
        whenever(dataContext).thenReturn(IdeUiService.getInstance().createUiDataContext(createContextComponent(project, fixture)))
        whenever(place).thenReturn(TOOLWINDOW_GRADLE)
      }
      val gradleTaskData = TaskData(GRADLE_SYSTEM_ID, taskName, linkedExternalProjectPath, "Test run task from Gradle tool window").apply {
        group = GradleTaskClassifier.classifyTaskName(taskName)
        type = GRADLE_API_DEFAULT_TASK
      }
      perform(project, GRADLE_SYSTEM_ID, gradleTaskData, gradleTaskActionEvent)
    }

    private fun createContextComponent(project: Project, fixture: CodeInsightTestFixture): JComponent {
      val buildGradleFile = VfsUtil.findRelativeFile(project.guessProjectDir(), "build.gradle")
      fixture.openFileInEditor(buildGradleFile!!)
      val panel = JPanel().apply {
        add(fixture.editor.component)
      }
      DataManager.registerDataProvider(panel) { dataId ->
        if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
      }
      return fixture.editor.contentComponent
    }
  }
}
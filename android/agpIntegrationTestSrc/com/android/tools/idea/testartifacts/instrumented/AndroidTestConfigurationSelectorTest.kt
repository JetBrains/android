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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createContext
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.action.task.RunExternalSystemTaskAction
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Test for run configuration selector {@link RunExternalSystemTaskAction}
 */
class AndroidTestConfigurationSelectorTest : AndroidGradleTestCase() {
  override fun shouldRunTest(): Boolean {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest()
  }

  private class TestRunExternalSystemTaskAction : RunExternalSystemTaskAction() {
    fun performDelegator(project: Project,
                projectSystemId: ProjectSystemId,
                taskData: TaskData,
                e: AnActionEvent) {
      this.perform(project, projectSystemId, taskData, e);
    }
  }

  fun testRunTaskAsNewRunConfiguration() {
    loadSimpleApplication()
    val mainTestFacet = AndroidFacet.getInstance(getModule("app").getMainModule())!!

    val mockProjectSystemId = mock(ProjectSystemId::class.java)
    `when`(mockProjectSystemId.readableName).thenReturn("test project name")
    val androidTestClassName = "google.simpleapplication.ApplicationTest"
    val element: PsiElement = JavaPsiFacade.getInstance(project).findClass(androidTestClassName, GlobalSearchScope.projectScope(project))!!
    val context = createContext(project, element)
    val mockActionEvent = mock(AnActionEvent::class.java)
    `when`(mockActionEvent.dataContext).thenReturn(context.dataContext)
    `when`(mockActionEvent.place).thenReturn(context.place)
    val testTaskData = TaskData(mockProjectSystemId, "testAndroidTestConfig", project.projectFilePath.toString(),
                                "Run configurtation selector test")
    TestRunExternalSystemTaskAction().performDelegator(project, mockProjectSystemId, testTaskData, mockActionEvent)

    val runConfig = context.runManager.selectedConfiguration!!.configuration as AndroidTestRunConfiguration
    assertEmpty(runConfig.checkConfiguration(mainTestFacet))
    assertEquals(runConfig.CLASS_NAME, androidTestClassName)
    assertEquals(runConfig.TESTING_TYPE, AndroidTestRunConfiguration.TEST_CLASS)
  }
}
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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.UNIT_TESTING
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.modules
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertTrue
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify

/** Tests for {@link AssembleRunConfigurationAction }. */
@RunsInEdt
class AssembleRunConfigurationActionTest {
  @get:Rule val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }

  private var myPresentation: Presentation? = null

  @Mock private var myEvent: AnActionEvent? = null

  @Mock private val myBuildInvoker: GradleBuildInvoker? = null

  private val myAction = AssembleRunConfigurationAction()

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    myPresentation = Presentation()
    Mockito.`when`(myEvent!!.presentation).thenReturn(myPresentation)
    Mockito.`when`(myEvent!!.project).thenReturn(project)
    IdeComponents(project).replaceProjectService<GradleBuildInvoker?>(GradleBuildInvoker::class.java, myBuildInvoker!!)
  }

  /**
   * Test to verify that the Java Run Configs are enabled on Android Studio, and that for test configs, we only invoke the task to build the
   * test module.
   */
  @Test
  fun testJavaRunConfigIsEnabled() {
    projectRule.loadProject(UNIT_TESTING)
    val gradleJavaConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.javalib.JavaLibJavaTest")
    assertThat(gradleJavaConfiguration).isNotNull()
    val runConfigSettingsForSelected =
      RunManager.getInstance(project)
        .createConfiguration(gradleJavaConfiguration!!, GradleExternalTaskConfigurationType.getInstance().factory)
    RunManager.getInstance(project).selectedConfiguration = runConfigSettingsForSelected

    myAction.update(myEvent!!)
    // Check that we, in fact, have this action disabled for the Java Run Config.
    assertTrue(myPresentation!!.isEnabledAndVisible)

    myAction.actionPerformed(myEvent!!)
    val testModules =
      project.modules.filter { module ->
        (module.getGradleProjectPath() as? GradleSourceSetProjectPath).let { gradleId ->
          gradleId?.path?.contains("javalib") == true && gradleId.sourceSet == IdeModuleSourceSetImpl("test", true)
        }
      }

    assertTrue(testModules.size == 1)
    // Verify that the task was invoked to build the test module only.
    verify(myBuildInvoker)?.buildConfiguration(testModules.toTypedArray(), false)
  }

  @Test
  fun testAndroidUnitTestRCIsVisibleAndEnabled() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    val androidGradleRunConfig = createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")
    assertThat(androidGradleRunConfig).isNotNull()
    val runConfigSettingsForSelected =
      RunManager.getInstance(project)
        .createConfiguration(androidGradleRunConfig!!, GradleExternalTaskConfigurationType.getInstance().factory)
    RunManager.getInstance(project).selectedConfiguration = runConfigSettingsForSelected

    myAction.update(myEvent!!)
    // Check that we, in fact, have this action enabled for the Android Gradle unitTest Run Config.
    assertTrue(myPresentation!!.isEnabledAndVisible)
  }
}

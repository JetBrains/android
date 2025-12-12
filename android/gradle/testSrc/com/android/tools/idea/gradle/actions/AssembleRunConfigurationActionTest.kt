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

import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.UNIT_TESTING
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

/**
 * Tests for {@link AssembleRunConfigurationAction }.
 */
@RunsInEdt
class AssembleRunConfigurationActionTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }

  private var myPresentation: Presentation? = null

  @Mock
  private var myEvent: AnActionEvent? = null
  private val myAction = AssembleRunConfigurationAction()

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    myPresentation = Presentation()
    Mockito.`when`(myEvent!!.presentation).thenReturn(myPresentation)
    Mockito.`when`(myEvent!!.project).thenReturn(project)
  }

  /**
   * Test to verify that the Java Run Configs are not enabled on Android Studio. Context: b/467659028.
   */
  @Test
  fun testJavaRunConfigIsNotEnabled() {
    projectRule.loadProject(UNIT_TESTING)
    val gradleJavaConfiguration = createAndroidGradleTestConfigurationFromClass(
      project, "com.example.javalib.JavaLibJavaTest")
    assertThat(gradleJavaConfiguration).isNotNull()
    val runConfigSettingsForSelected =
      RunManager.getInstance(project).createConfiguration(
        gradleJavaConfiguration!!, GradleExternalTaskConfigurationType.getInstance().factory)
    RunManager.getInstance(project).selectedConfiguration = runConfigSettingsForSelected

    myAction.update(myEvent!!)
    // Check that we, in fact, have this action disabled for the Java Run Config.
    assertFalse(myPresentation!!.isEnabledAndVisible)
  }

  @Test
  fun testAndroidUnitTestRCIsVisibleAndEnabled() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    val androidGradleRunConfig = createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")
    assertThat(androidGradleRunConfig).isNotNull()
    val runConfigSettingsForSelected =
      RunManager.getInstance(project).createConfiguration(
        androidGradleRunConfig!!, GradleExternalTaskConfigurationType.getInstance().factory)
    RunManager.getInstance(project).selectedConfiguration = runConfigSettingsForSelected

    myAction.update(myEvent!!)
    // Check that we, in fact, have this action enabled for the Android Gradle unitTest Run Config.
    assertTrue(myPresentation!!.isEnabledAndVisible)
  }
}
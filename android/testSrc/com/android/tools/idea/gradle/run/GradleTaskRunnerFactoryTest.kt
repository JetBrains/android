/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.run

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider.GradleTaskRunnerFactory
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testing.Facets
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.google.common.truth.Truth
import com.intellij.execution.configurations.JavaRunConfigurationModule
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.module.Module
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import junit.framework.TestCase.assertNotNull
import org.gradle.tooling.model.GradleProject
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.MockitoAnnotations.initMocks

/**
 * Tests for [GradleTaskRunnerFactory].
 */
class GradleTaskRunnerFactoryTest : PlatformTestCase() {
  @Mock
  private lateinit var gradleVersions: GradleVersions
  private lateinit var taskRunnerFactory: GradleTaskRunnerFactory

  override fun setUp() {
    super.setUp()
    initMocks(this)
    taskRunnerFactory = GradleTaskRunnerFactory(project, gradleVersions)
  }

  fun testCreateTaskRunnerWithAndroidRunConfigurationBaseAndGradle3Dot5() {
    `when`(gradleVersions.getGradleVersion(project)).thenReturn(GradleVersion(3, 5, 0))
    val configurationModule = Mockito.mock(JavaRunConfigurationModule::class.java)
    `when`(configurationModule.module).thenReturn(null)
    val configuration = Mockito.mock(AndroidRunConfigurationBase::class.java)
    `when`(configuration.configurationModule).thenReturn(configurationModule)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.buildAction
    TestCase.assertNotNull(buildAction)
  }

  fun testCreateTaskRunnerWithAndroidRunConfigurationBaseAndGradleOlderThan3Dot5() {
    `when`(gradleVersions.getGradleVersion(project)).thenReturn(GradleVersion(3, 4, 1))
    val configuration = Mockito.mock(AndroidRunConfigurationBase::class.java)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.buildAction
    TestCase.assertNull(buildAction)
  }

  fun testCreateTaskRunnerWithConfigurationNotAndroidRunConfigurationBase() {
    val configuration = Mockito.mock(RunConfiguration::class.java)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.buildAction
    TestCase.assertNull(buildAction)
  }

  fun testCreateTaskRunnerForDynamicFeatureInstrumentedTest() {
    `when`(gradleVersions.getGradleVersion(project)).thenReturn(GradleVersion(3, 5, 0))
    // Setup a base-app Module
    val androidModuleModel1 = Mockito.mock(AndroidModuleModel::class.java)
    val ideAndroidProject1 = Mockito.mock(IdeAndroidProject::class.java)
    setUpModuleAsAndroidModule(module, androidModuleModel1, ideAndroidProject1)
    // Setup an additional Dynamic Feature module
    val featureModule = createModule("feature1")
    val androidModuleModel2 = Mockito.mock(AndroidModuleModel::class.java)
    val ideAndroidProject2 = Mockito.mock(IdeAndroidProject::class.java)
    setUpModuleAsAndroidModule(featureModule, androidModuleModel2, ideAndroidProject2)
    `when`(ideAndroidProject2.projectType).thenReturn(AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE)
    `when`(ideAndroidProject1.dynamicFeatures).thenReturn(ImmutableList.of(":feature1"))
    `when`(ideAndroidProject2.dynamicFeatures).thenReturn(emptyList())
    val configurationModule = Mockito.mock(JavaRunConfigurationModule::class.java)
    `when`(configurationModule.module).thenReturn(featureModule)
    val configuration = Mockito.mock(AndroidTestRunConfiguration::class.java)
    `when`(configuration.configurationModule).thenReturn(configurationModule)
    val taskRunner = taskRunnerFactory.createTaskRunner(configuration)
    val buildAction = taskRunner.myBuildAction as OutputBuildAction
    TestCase.assertNotNull(buildAction)
    UsefulTestCase.assertSize(2, buildAction.myGradlePaths)
    Truth.assertThat(buildAction.myGradlePaths).containsExactly(":" + module.name, ":feature1")
  }

  private fun setUpModuleAsAndroidModule(
    module: Module,
    androidModel: AndroidModuleModel,
    ideAndroidProject: IdeAndroidProject
  ) {
    setUpModuleAsGradleModule(module)
    `when`(androidModel.androidProject).thenReturn(ideAndroidProject)
    val androidModelFeatures = Mockito.mock(AndroidModelFeatures::class.java)
    `when`(androidModelFeatures.isTestedTargetVariantsSupported).thenReturn(false)
    `when`(androidModel.features).thenReturn(androidModelFeatures)
    val androidFacet = Facets.createAndAddAndroidFacet(module)
    val state = androidFacet.configuration.state
    TestCase.assertNotNull(state)
    state.ASSEMBLE_TASK_NAME = "assembleTask2"
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet("afterSyncTask1", "afterSyncTask2")
    state.COMPILE_JAVA_TASK_NAME = "compileTask2"
    AndroidModel.set(androidFacet, androidModel)
  }

  private fun setUpModuleAsGradleModule(module: Module) {
    val gradleFacet = Facets.createAndAddGradleFacet(module)
    gradleFacet.configuration.GRADLE_PROJECT_PATH = SdkConstants.GRADLE_PATH_SEPARATOR + module.name
    val gradleProjectStub: GradleProject = GradleProjectStub(
      emptyList(),
      SdkConstants.GRADLE_PATH_SEPARATOR + module.name,
      Projects.getBaseDirPath(project)
    )
    val model = GradleModuleModel(module.name, gradleProjectStub, emptyList(), null, null, null, null)
    gradleFacet.setGradleModuleModel(model)
  }
}
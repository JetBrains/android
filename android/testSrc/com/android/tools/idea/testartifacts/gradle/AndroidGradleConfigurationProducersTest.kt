/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.gradle

import com.android.tools.idea.gradle.run.AndroidGradleTestTasksProvider
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromDirectory
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromFile
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass
import com.android.tools.idea.testartifacts.getPsiElement
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_SAME_NAME_CLASSES
import com.android.tools.idea.testing.TestProjectPaths.TEST_RESOURCES
import com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES
import com.android.tools.idea.testing.TestProjectPaths.UNIT_TESTING
import com.google.common.truth.Truth
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import junit.framework.TestCase
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * Tests for producing Gradle Run Configuration for Android unit test.
 */
class AndroidGradleConfigurationProducersTest : AndroidGradleTestCase() {

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationInSimpleProject() {
    loadSimpleApplication()
    verifyCanCreateClassGradleRunConfigurationFromTestScope()
    verifyCannotCreateClassGradleRunConfigurationFromAndroidTestScope()
    verifyCannotCreateDirectoryGradleRunConfigurationFromAndroidTestDirectory()
    verifyAndroidGradleTestTasksProviderDoesntCreateTestTasksForJavaModule()
    verifyCanCreateGradleConfigurationFromTestDirectory()
  }

  @Throws(Exception::class)
  fun testKotlinTestSupport() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    verifyCannotCreateGradleConfigurationFromAndroidTestDirectory()
    verifyCannotCreateKotlinClassGradleConfigurationFromAndroidTestScope()
    verifyCanCreateGradleConfigurationFromTestDirectoryKotlin()
  }

  @Throws(Exception::class)
  fun testKotlinMultiplatform() {
    loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM)
    verifyCanCreateKotlinClassGradleConfigurationFromAndroidTest()
    verifyCanCreateKotlinDirectoryGradleConfigurationFromAndroidTest()
  }

  @Throws(Exception::class)
  fun testTasksIsReExecuted() {
    loadProject(TEST_RESOURCES)

    // Create the Run configuration.
    val listener = object : ExternalSystemTaskNotificationListenerAdapter() {
      var messagesLog = StringBuilder()
      var finalMessage = ""

      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        super.onTaskOutput(id, text, stdOut)
        messagesLog.append(text)
      }

      override fun onEnd(id: ExternalSystemTaskId) {
        super.onEnd(id)
        finalMessage = messagesLog.toString()
      }
    }

    val psiElement = JavaPsiFacade.getInstance(project).findClass("com.example.app.ExampleUnitTest", GlobalSearchScope.projectScope(project))
    val configurationFromContext = createConfigurationFromContext(psiElement!!)
    val gradleRunConfiguration = configurationFromContext!!.configuration as GradleRunConfiguration
    TestCase.assertTrue(gradleRunConfiguration.settings.taskNames == listOf(":app:testDebugUnitTest"))
    // Set the execution settings using the runConfiguration parameters.
    val executionSettings = GradleManager()
      .executionSettingsProvider
      .`fun`(Pair.create(project, project.basePath))
      .apply { putUserData(GradleConstants.RUN_TASK_AS_TEST, true) }

    AndroidGradleTaskManager().executeTasks(
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      gradleRunConfiguration.settings.taskNames,
      project.basePath!!,
      executionSettings,
      null,
      listener
    )

    assertTrue(listener.finalMessage.lines().contains("> Task :app:testDebugUnitTest"))

    // Clear the logged messages.
    listener.messagesLog = StringBuilder()

    AndroidGradleTaskManager().executeTasks(
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      gradleRunConfiguration.settings.taskNames,
      project.basePath!!,
      executionSettings,
      null,
      listener
    )

    // Check that the test task was re-executed, and not marked as UP-TO-DATE.
    assertFalse(listener.messagesLog.lines().contains("> Task :app:testDebugUnitTest UP-TO-DATE"))
    assertTrue(listener.messagesLog.lines().contains("> Task :app:testDebugUnitTest"))
  }

  private fun createConfigurationFromContext(psiFile: PsiElement): ConfigurationFromContextImpl? {
    val context = TestConfigurationTesting.createContext(project, psiFile)
    return context.configurationsFromContext?.firstOrNull() as ConfigurationFromContextImpl?
  }

  private fun checkConfigurationTasksAreAsExpected(
    configurationFromContext: ConfigurationFromContextImpl,
    file: VirtualFile,
    moduleName: String) {
    val configuration = configurationFromContext.configuration as? GradleRunConfiguration
    assertNotNull(configuration)
    // Make sure that the tasks we set are expected when provided by AndroidGradleTestTasksProvider.
    val module2 = ModuleManager.getInstance(project).modules
      .first { module ->  module.name == moduleName }
    TestCase.assertNotNull(module2)

    TestCase.assertEquals(configuration!!.settings.taskNames, listOf(":module2:testDebugUnitTest"))
  }
  
  private fun verifyCannotCreateGradleConfigurationFromAndroidTestDirectory() {
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/androidTest/java"))
  }

  private fun verifyCannotCreateKotlinClassGradleConfigurationFromAndroidTestScope() {
    TestCase.assertNull(createAndroidGradleConfigurationFromFile(
      project, "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"))
  }

  private fun verifyCanCreateClassGradleRunConfigurationFromTestScope() {
    TestCase.assertNotNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest"))
  }

  private fun verifyCannotCreateClassGradleRunConfigurationFromAndroidTestScope() {
    TestCase.assertNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest"))
  }

  private fun verifyAndroidGradleTestTasksProviderDoesntCreateTestTasksForJavaModule() {
    // Here we test to verify that the configuration tasks aren't provided by the AndroidGradleTestTasksProvider.
    // We do that by verifying the tasks value.
    val gradleJavaConfiguration = createAndroidGradleTestConfigurationFromClass(
      project, "google.simpleapplication.UnitTest")
    TestCase.assertNotNull(gradleJavaConfiguration)
    Truth.assertThat(gradleJavaConfiguration!!.settings.taskNames).isEqualTo(listOf(":app:testDebugUnitTest"))
  }

  private fun verifyCannotCreateDirectoryGradleRunConfigurationFromAndroidTestDirectory() {
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/androidTest/java"))
  }

  private fun verifyCanCreateKotlinClassGradleConfigurationFromAndroidTest() {
    val psiFile = getPsiElement(project, "module2/src/androidTest/kotlin/com/example/library/TestUnitTest.kt", false) as PsiFile
    // Create a runConfiguration context based on the testClass.
    val configurationFromContext = createConfigurationFromContext(psiFile)
    TestCase.assertNotNull(configurationFromContext)
    // Make sure that the configuration is created by the testClass gradle provider.
    assertTrue(configurationFromContext!!.configurationProducer is TestClassGradleConfigurationProducer)

    // Make sure that the runConfiguration test tasks we set are expected when provided by AndroidGradleTestTasksProvider.
    checkConfigurationTasksAreAsExpected(
      configurationFromContext,
      psiFile.virtualFile,
      "kotlinMultiPlatform.module2"
    )
  }

  private fun verifyCanCreateKotlinDirectoryGradleConfigurationFromAndroidTest() {
    val psiFile = getPsiElement(project, "module2/src/androidTest/kotlin/com/example/library", true)
    // Create a runConfiguration context based on the testClass.
    val configurationFromContext = createConfigurationFromContext(psiFile)
    TestCase.assertNotNull(configurationFromContext)
    // Make sure that the configuration is created by the AllInPackage gradle provider.
    assertTrue(configurationFromContext!!.configurationProducer is AllInPackageGradleConfigurationProducer)

    // Check that the run Configuration is a GradleRunConfiguration, and has tasks to run tests.
    (psiFile as? PsiDirectory)?.virtualFile?.let {
      checkConfigurationTasksAreAsExpected(
        configurationFromContext,
        it,
        "kotlinMultiPlatform.module2"
      )
    }
  }

  private fun verifyCanCreateGradleConfigurationFromTestDirectory() {
    val gradleRunConfiguration = createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java")
    val testTaskNames = gradleRunConfiguration?.settings?.taskNames
    TestCase.assertTrue(testTaskNames != null && testTaskNames.size == 1)
    TestCase.assertTrue(testTaskNames?.single() == ":app:testDebugUnitTest")
  }

  private fun verifyCanCreateGradleConfigurationFromTestDirectoryKotlin() {
    val gradleRunConfiguration = createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java")
    val testTaskNames = gradleRunConfiguration?.settings?.taskNames
    TestCase.assertTrue(testTaskNames != null && testTaskNames.size == 1)
    TestCase.assertTrue(testTaskNames?.single() == ":app:testDebugUnitTest")
  }
}
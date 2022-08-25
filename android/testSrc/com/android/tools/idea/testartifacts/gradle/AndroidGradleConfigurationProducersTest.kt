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

import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromDirectory
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromFile
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass
import com.android.tools.idea.testartifacts.createGradleConfigurationFromPsiElement
import com.android.tools.idea.testartifacts.getPsiElement
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_WITH_DUPLICATES
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM
import com.android.tools.idea.testing.TestProjectPaths.TEST_RESOURCES
import com.android.tools.idea.testing.TestProjectPaths.UNIT_TESTING
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.DefaultCoverageFileProvider
import com.intellij.coverage.IDEACoverageRunner
import com.intellij.coverage.JavaCoverageEngine
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.search.GlobalSearchScope
import junit.framework.Assert
import junit.framework.TestCase
import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsoleManager
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
    verifyCanCreateGradleConfigurationFromTestDirectory()
  }

  @Throws(Exception::class)
  fun testCanCreateDifferentConfigurationsWhenDuplicateNames() {
    loadProject(SIMPLE_APPLICATION_WITH_DUPLICATES)
    verifyCanCreateGradleConfigurationFromSameNameTestClass()
    verifyCanCreateGradleConfigurationFromSameNameTestDirectory()
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
    // Starting from IDEA 2021.3, both the task names and script parameters are merged into taskNames as a list of separate tasks
    // that is passed to the Gradle executor as such.
    assertThat(gradleRunConfiguration.settings.taskNames).isEqualTo(listOf(":app:testDebugUnitTest", "--tests", "\"com.example.app.ExampleUnitTest\""))
    // Set the execution settings using the runConfiguration parameters.
    val executionSettings = GradleManager()
      .executionSettingsProvider
      .`fun`(Pair.create(project, project.basePath))
      .apply { putUserData(GradleConstants.RUN_TASK_AS_TEST, true) }

    AndroidGradleTaskManager().executeTasks(
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      listOf(":app:testDebugUnitTest"),
      project.basePath!!,
      executionSettings,
      null,
      listener
    )

    assertThat(listener.finalMessage.lines()).contains("> Task :app:testDebugUnitTest")

    // Clear the logged messages.
    listener.messagesLog = StringBuilder()

    AndroidGradleTaskManager().executeTasks(
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      gradleRunConfiguration.settings.taskNames.map { it.trimQuotes()},
      project.basePath!!,
      executionSettings,
      null,
      listener
    )

    // Check that the test task was re-executed, and not marked as UP-TO-DATE.
    assertThat(listener.messagesLog.lines()).doesNotContain("> Task :app:testDebugUnitTest UP-TO-DATE")
    assertThat(listener.messagesLog.lines()).contains("> Task :app:testDebugUnitTest")
  }

  @Throws(Exception::class)
  fun testJavaModulesTestTasksAreCreated() {
    loadProject(UNIT_TESTING)
    val gradleJavaConfiguration = createAndroidGradleTestConfigurationFromClass(
      project, "com.example.javalib.JavaLibJavaTest")
    TestCase.assertNotNull(gradleJavaConfiguration)
    // See above comment about the changes to task names.
    assertThat(gradleJavaConfiguration!!.settings.taskNames).isEqualTo(listOf(":javalib:test", "--tests", "\"com.example.javalib.JavaLibJavaTest\""))
  }

  @Throws(Exception::class)
  fun testConsoleManagerIsApplicableForTestTaskExecution() {
    loadSimpleApplication()
    // Verify We can render method Run configurations using GradleTestsExecutionConsoleManager.
    val methodPsiElement = JavaPsiFacade.getInstance(project)
      .findClass("google.simpleapplication.UnitTest", GlobalSearchScope.projectScope(project))!!
      .children.filterIsInstance<PsiMethodImpl>().first()
    val  methodConfiguration = createGradleConfigurationFromPsiElement(project, methodPsiElement)
    assertThat(methodConfiguration).isNotNull()
    val methodConfigTask = ExternalSystemExecuteTaskTask(project, methodConfiguration!!.settings, null, methodConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(methodConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)

    // Verify We can render class Run configurations using GradleTestsExecutionConsoleManager.
    val classConfiguration = createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")
    assertThat(classConfiguration).isNotNull()
    val classConfigTask = ExternalSystemExecuteTaskTask(project, classConfiguration!!.settings, null, classConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(classConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)

    // Verify We can render package Run configurations using GradleTestsExecutionConsoleManager.
    val packagePsiElement = getPsiElement(project, "app/src/test/java/google/simpleapplication", true)
    val packageConfiguration = createGradleConfigurationFromPsiElement(project, packagePsiElement)
    assertThat(packageConfiguration).isNotNull()
    val packageConfigTask = ExternalSystemExecuteTaskTask(project, packageConfiguration!!.settings, null, packageConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(packageConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)

    // Verify We can render directory Run configurations using GradleTestsExecutionConsoleManager.
    val  directoryConfiguration = createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java")
    assertThat(directoryConfiguration).isNotNull()
    val directoryConfigTask = ExternalSystemExecuteTaskTask(project, directoryConfiguration!!.settings, null, directoryConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(directoryConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)
  }

  @Throws(Exception::class)
  fun testCompositeProjectTestConfiguration() {
    loadProject(TestProjectPaths.BASIC_COMPOSITE_BUILD)
    val configuration = createAndroidGradleConfigurationFromDirectory(project, "TestCompositeLib1/app/src/test/java/")
    Assert.assertNotNull(configuration)
    assertThat(configuration!!.settings.taskNames.size).isEqualTo(1)
    assertThat(configuration.settings.taskNames[0]).isEqualTo(":includedLib1:app:testDebugUnitTest")
  }

  @Throws(Exception::class)
  fun testCoverageEngineDoesntRequireRecompilation() {
    loadSimpleApplication()
    // Run a Gradle task.
    AndroidGradleTaskManager().executeTasks(
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      listOf(":app:testDebugUnitTest"),
      project.basePath!!,
      GradleManager().executionSettingsProvider.`fun`(Pair.create(project, project.basePath)),
      null,
      object : ExternalSystemTaskNotificationListenerAdapter() {}
    )

    // Check that the JavaCoverageEngine won't require project rebuild.
    val filePsiElement = getPsiElement(project, "app/src/main/java/google/simpleapplication/MyActivity.java", false)
    val module = ModuleUtilCore.findModuleForPsiElement(filePsiElement)
    assertThat(module).isNotNull()

    val runner = CoverageRunner.getInstance(IDEACoverageRunner::class.java)
    val fileProvider = DefaultCoverageFileProvider(filePsiElement.containingFile.virtualFile.toIoFile())
    val suite =
      JavaCoverageEngine.getInstance().createCoverageSuite(
        runner, "Simple", fileProvider, null, -1, null, false, false, false, project)
    val bundle = CoverageSuitesBundle(suite)

    val coverageEngine = JavaCoverageEngine()
    val needToRebuild =
      coverageEngine.recompileProjectAndRerunAction(module!!, bundle) {
        CoverageDataManager.getInstance(project).chooseSuitesBundle(bundle)
      }

    assertThat(needToRebuild).isFalse()
  }

  private fun createConfigurationFromContext(psiFile: PsiElement): ConfigurationFromContextImpl? {
    val context = TestConfigurationTesting.createContext(project, psiFile)
    return context.configurationsFromContext?.firstOrNull() as ConfigurationFromContextImpl?
  }

  private fun checkConfigurationTasksAreAsExpected(
    configurationFromContext: ConfigurationFromContextImpl,
    configurationTasks: List<String>
  ) {
    val configuration = configurationFromContext.configuration as? GradleRunConfiguration
    // Make sure that the tasks we set are as expected.
    val module2 = ModuleManager.getInstance(project).modules
      .first { module ->  module.name == "kotlinMultiPlatform.module2" }
    assertThat(module2).isNotNull()

    assertThat(configuration!!.settings.taskNames).isEqualTo(configurationTasks)
  }
  
  private fun verifyCannotCreateGradleConfigurationFromAndroidTestDirectory() {
    assertThat(createAndroidGradleConfigurationFromDirectory(project, "app/src/androidTest/java")).isNull()
  }

  private fun verifyCannotCreateKotlinClassGradleConfigurationFromAndroidTestScope() {
    assertThat(
      createAndroidGradleConfigurationFromFile(project, "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"))
      .isNull()
  }

  private fun verifyCanCreateClassGradleRunConfigurationFromTestScope() {
    assertThat(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")).isNotNull()
  }

  private fun verifyCannotCreateClassGradleRunConfigurationFromAndroidTestScope() {
    assertThat(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest")).isNull()
  }

  private fun verifyCannotCreateDirectoryGradleRunConfigurationFromAndroidTestDirectory() {
    assertThat(createAndroidGradleConfigurationFromDirectory(project, "app/src/androidTest/java")).isNull()
  }

  private fun verifyCanCreateKotlinClassGradleConfigurationFromAndroidTest() {
    val psiFile = getPsiElement(project, "module2/src/androidTest/kotlin/com/example/library/TestUnitTest.kt", false) as PsiFile
    // Create a runConfiguration context based on the testClass.
    val configurationFromContext = createConfigurationFromContext(psiFile)
    // Make sure that the configuration is created by the testClass gradle provider.
    assertThat(configurationFromContext!!.configurationProducer).isInstanceOf(TestClassGradleConfigurationProducer::class.java)

    // Make sure that the runConfiguration test tasks we set are as expected.
    checkConfigurationTasksAreAsExpected(
      configurationFromContext,
      // See above comment about the changes to task names.
      listOf(":module2:testDebugUnitTest", "--tests", "\"com.example.library.TestUnitTest\"")
    )
  }

  private fun verifyCanCreateKotlinDirectoryGradleConfigurationFromAndroidTest() {
    val psiFile = getPsiElement(project, "module2/src/androidTest/kotlin/com/example/library", true)
    // Create a runConfiguration context based on the testClass.
    val configurationFromContext = createConfigurationFromContext(psiFile)
    // Make sure that the configuration is created by the AllInPackage gradle provider.
    assertThat(configurationFromContext!!.configurationProducer).isInstanceOf(AllInPackageGradleConfigurationProducer::class.java)

    // Check that the run Configuration is a GradleRunConfiguration, and has tasks to run tests.
    (psiFile as? PsiDirectory)?.virtualFile?.let {
      checkConfigurationTasksAreAsExpected(
        configurationFromContext,
        // See above comment about the changes to task names.
        listOf(":module2:cleanTestDebugUnitTest", ":module2:testDebugUnitTest", "--tests", "\"com.example.library.*\"")
      )
    }
  }

  private fun verifyCanCreateGradleConfigurationFromTestDirectory() {
    val gradleRunConfiguration = createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java")
    val testTaskNames = gradleRunConfiguration?.settings?.taskNames
    assertThat(testTaskNames).containsExactly(":app:testDebugUnitTest")
  }

  private fun verifyCanCreateGradleConfigurationFromSameNameTestClass() {
    var psiElement = getPsiElement(project, "app/src/test/java/google/simpleapplication/UnitTest.java", false)
    val appGradleTestClassConfiguration = createGradleConfigurationFromPsiElement(project, psiElement)
    assertThat(appGradleTestClassConfiguration).isNotNull()

    psiElement = getPsiElement(project, "libs/src/test/java/google/simpleapplication/UnitTest.java", false)
    var libGradleTestClassConfiguration = findExistingGradleTestConfigurationFromPsiElement(project, psiElement)
    // Verify that Gradle doesn't consider the run configuration in libs module equal to the run configuration in app module.
    // The run configuration is null in this case because we can successfully detect in the tasks name that the modules are different
    // between the two contexts.
    assertThat(libGradleTestClassConfiguration).isNull()

    libGradleTestClassConfiguration = createGradleConfigurationFromPsiElement(project, psiElement)
    assertThat(libGradleTestClassConfiguration).isNotNull()
    assertThat(libGradleTestClassConfiguration).isNotSameAs(appGradleTestClassConfiguration)
  }

  private fun verifyCanCreateGradleConfigurationFromSameNameTestDirectory() {
    val appModulePsiElement = getPsiElement(project, "app/src/test/java/google/simpleapplication", true)
    val appGradleTestPackageConfiguration = createGradleConfigurationFromPsiElement(project, appModulePsiElement)
    assertThat(appGradleTestPackageConfiguration).isNotNull()

    val libModulePsiLocation = getPsiElement(project, "libs/src/test/java/google/simpleapplication", true)
    val libExistingTestPackageConfiguration = findExistingGradleTestConfigurationFromPsiElement(project, libModulePsiLocation)
    // Verify that Gradle doesn't consider the run configuration in libs module equal to the run configuration in app module.
    // The run configuration is null in this case because we can successfully detect in the tasks name that the modules are different
    // between the two contexts.
    assertThat(libExistingTestPackageConfiguration).isNull()

    val libGradleTestPackageConfiguration = createGradleConfigurationFromPsiElement(project, libModulePsiLocation)
    assertThat(libGradleTestPackageConfiguration).isNotNull()
    assertThat(libGradleTestPackageConfiguration).isNotSameAs(appGradleTestPackageConfiguration)
  }

  private fun findExistingGradleTestConfigurationFromPsiElement(project: Project, psiElement: PsiElement): GradleRunConfiguration? {
    val context = TestConfigurationTesting.createContext(project, psiElement)
    // Search for any existing run configuration that was created from this context.
    val existing = context.findExisting() ?: return null
    return existing.configuration  as? GradleRunConfiguration
  }

  private fun verifyCanCreateGradleConfigurationFromTestDirectoryKotlin() {
    val gradleRunConfiguration = createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java")
    val testTaskNames = gradleRunConfiguration?.settings?.taskNames
    assertThat(testTaskNames).containsExactly(":app:testDebugUnitTest")
  }
}
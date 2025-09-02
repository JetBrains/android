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
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.createGradleRunConfiguration
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromDirectory
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromFile
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromDirectory
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromMethod
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromMethod
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM
import com.android.tools.idea.testing.TestProjectPaths.BASIC_COMPOSITE_BUILD
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_WITH_DUPLICATES
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM
import com.android.tools.idea.testing.TestProjectPaths.TEST_RESOURCES
import com.android.tools.idea.testing.TestProjectPaths.UNIT_TESTING
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.DefaultCoverageFileProvider
import com.intellij.coverage.IDEACoverageRunner
import com.intellij.coverage.JavaCoverageEngine
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import java.io.File
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsoleManager
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Rule
import org.junit.Test

/**
 * Tests for producing Gradle Run Configuration for Android unit test.
 */
@RunsInEdt
class AndroidGradleConfigurationProducersTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }

  @Test
  fun testCanCreateGradleConfigurationInSimpleProject() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    verifyCanCreateClassGradleRunConfigurationFromTestScope()
    verifyCannotCreateClassGradleRunConfigurationFromAndroidTestScope()
    verifyCannotCreateMethodGradleRunConfigurationFromAndroidTestMethodScope()
    verifyCannotCreateDirectoryGradleRunConfigurationFromAndroidTestDirectory("app/src/androidTest/java")
    verifyCannotCreateDirectoryGradleRunConfigurationFromAndroidTestDirectory("app/src/androidTest")
    verifyCanCreateGradleConfigurationFromTestDirectory()
  }

  @Test
  fun testCanCreateDifferentConfigurationsWhenDuplicateNames() {
    projectRule.loadProject(SIMPLE_APPLICATION_WITH_DUPLICATES)
    verifyCanCreateGradleConfigurationFromSameNameTestClass()
    verifyCanCreateGradleConfigurationFromSameNameTestDirectory()
  }

  @Test
  fun testKotlinTestSupport() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN)
    verifyCannotCreateDirectoryGradleRunConfigurationFromAndroidTestDirectory("app/src/androidTest/java")
    verifyCannotCreateKotlinClassGradleConfigurationFromAndroidTestScope()
    verifyCanCreateGradleConfigurationFromTestDirectoryKotlin()
  }

  @Test
  fun testKotlinMultiplatform() {
    projectRule.loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM)
    verifyCanCreateKotlinClassGradleConfigurationFromAndroidUnitTest()
    verifyCanCreateKotlinDirectoryGradleConfigurationFromAndroidUnitTest()
  }

  @Test
  fun testTasksIsReExecuted() {
    projectRule.loadProject(TEST_RESOURCES)

    // Create the Run configuration.
    val listener = object : ExternalSystemTaskNotificationListener {
      var messagesLog = StringBuilder()
      var finalMessage = ""

      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        messagesLog.append(text)
      }

      override fun onEnd(proojecPath: String, id: ExternalSystemTaskId) {
        finalMessage = messagesLog.toString()
      }
    }

    val gradleRunConfiguration = createAndroidGradleTestConfigurationFromClass(project, "com.example.app.ExampleUnitTest")!!
    // Starting from IDEA 2021.3, both the task names and script parameters are merged into taskNames as a list of separate tasks
    // that is passed to the Gradle executor as such.
    assertThat(gradleRunConfiguration.settings.taskNames).isEqualTo(listOf(":app:testDebugUnitTest", "--tests", "\"com.example.app.ExampleUnitTest\""))
    // Set the execution settings using the runConfiguration parameters.
    val firstExecutionSettings =
      ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(project, project.basePath!!, GradleConstants.SYSTEM_ID)

    // Get all the UserData properties we get from creating a test RC. These need to be passed to the execution settings because they
    // determine if the task will be executed as a test and that they will be forcefully re-executed.
    val keyMap = gradleRunConfiguration.get()
    for (key in keyMap.keys) {
      firstExecutionSettings.putUserData(key as Key<Any>, keyMap[key])
    }

    firstExecutionSettings.tasks = listOf(":app:testDebugUnitTest")

    AndroidGradleTaskManager().executeTasks(
      project.basePath!!,
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      firstExecutionSettings,
      listener
    )

    assertThat(listener.finalMessage.lines()).contains("> Task :app:testDebugUnitTest")
    // Clear the logged messages.
    listener.messagesLog = StringBuilder()

    // Prepare for second tasks execution.
    val secondExecutionSettings =
      ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(project, project.basePath!!, GradleConstants.SYSTEM_ID)
    for (key in keyMap.keys) {
      secondExecutionSettings.putUserData(key as Key<Any>, keyMap[key])
    }

    secondExecutionSettings.tasks = listOf(":app:testDebugUnitTest")

    AndroidGradleTaskManager().executeTasks(
      project.basePath!!,
      ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project),
      secondExecutionSettings,
      listener
    )

    // Check that the test task was re-executed because the task.upToDateWhen is set to false.
    val expectedMessage = "Task ':app:testDebugUnitTest' is not up-to-date because:((\r)?\n)+\\s+Task\\.upToDateWhen is false\\.".toRegex()
    assertThat(expectedMessage.containsMatchIn(listener.finalMessage)).isTrue()
    assertThat(listener.messagesLog.lines()).contains("> Task :app:testDebugUnitTest")
  }

  @Test
  fun testJavaModulesTestTasksAreCreated() {
    projectRule.loadProject(UNIT_TESTING)
    val gradleJavaConfiguration = createAndroidGradleTestConfigurationFromClass(
      project, "com.example.javalib.JavaLibJavaTest")
    assertThat(gradleJavaConfiguration).isNotNull()
    // See above comment about the changes to task names.
    assertThat(gradleJavaConfiguration!!.settings.taskNames).isEqualTo(listOf(":javalib:test", "--tests", "\"com.example.javalib.JavaLibJavaTest\""))
  }

  @Test
  fun testConsoleManagerIsApplicableForTestTaskExecution() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    // Verify We can render method Run configurations using GradleTestsExecutionConsoleManager.
    val methodConfiguration = createAndroidGradleTestConfigurationFromMethod(project, "google.simpleapplication.UnitTest", "passingTest")
    assertThat(methodConfiguration).isNotNull()
    val methodConfigTask = ExternalSystemExecuteTaskTask(project, methodConfiguration!!.settings, null, methodConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(methodConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)

    // Verify We can render class Run configurations using GradleTestsExecutionConsoleManager.
    val classConfiguration = createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")
    assertThat(classConfiguration).isNotNull()
    val classConfigTask = ExternalSystemExecuteTaskTask(project, classConfiguration!!.settings, null, classConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(classConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)

    // Verify We can render package Run configurations using GradleTestsExecutionConsoleManager.
    val packagePsiElement = TestConfigurationTestingUtil.getPsiElement(project, "app/src/test/java/google/simpleapplication", true)
    val packageConfiguration = packagePsiElement.createGradleRunConfiguration()
    assertThat(packageConfiguration).isNotNull()
    val packageConfigTask = ExternalSystemExecuteTaskTask(project, packageConfiguration!!.settings, null, packageConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(packageConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)

    // Verify We can render directory Run configurations using GradleTestsExecutionConsoleManager.
    val  directoryConfiguration = createAndroidGradleTestConfigurationFromDirectory(project, "app/src/test/java")
    assertThat(directoryConfiguration).isNotNull()
    val directoryConfigTask = ExternalSystemExecuteTaskTask(project, directoryConfiguration!!.settings, null, directoryConfiguration)
    assertThat(ExternalSystemUtil.getConsoleManagerFor(directoryConfigTask)).isInstanceOf(GradleTestsExecutionConsoleManager::class.java)
  }

  @Test
  fun testCompositeProjectTestConfiguration() {
    projectRule.loadProject(BASIC_COMPOSITE_BUILD)
    val configuration = createAndroidGradleTestConfigurationFromDirectory(project, "TestCompositeLib1/app/src/test/java/")
    assertThat(configuration).isNotNull()
    assertThat(configuration!!.settings.taskNames.size).isEqualTo(1)
    assertThat(configuration.settings.taskNames[0]).isEqualTo(":includedLib1:app:testDebugUnitTest")
  }

  @Test
  fun testCoverageEngineDoesntRequireRecompilation() {
    projectRule.loadProject(SIMPLE_APPLICATION)
    // Run a Gradle task.
    val projectPath = project.basePath!!
    val id = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
    val settings = GradleManager().executionSettingsProvider.`fun`(Pair.create<Project, String>(project, projectPath)).apply {
      tasks = listOf(":app:testDebugUnitTest")
    }
    AndroidGradleTaskManager().executeTasks(projectPath, id, settings, ExternalSystemTaskNotificationListener.NULL_OBJECT)

    // Check that the JavaCoverageEngine won't require project rebuild.
    val filePsiElement = TestConfigurationTestingUtil.getPsiElement(project, "app/src/main/java/google/simpleapplication/MyActivity.java",
                                                                    false)
    val module = ModuleUtilCore.findModuleForPsiElement(filePsiElement)
    assertThat(module).isNotNull()

    val runner = CoverageRunner.getInstance(IDEACoverageRunner::class.java)
    val fileProvider = DefaultCoverageFileProvider(filePsiElement.containingFile.virtualFile.toIoFile())
    val suite = JavaCoverageEngine.getInstance().createCoverageSuite("Simple", project, runner, fileProvider, -1)
    val bundle = CoverageSuitesBundle(suite)

    val coverageEngine = JavaCoverageEngine()
    val needToRebuild =
      coverageEngine.recompileProjectAndRerunAction(module!!, bundle) {
        CoverageDataManager.getInstance(project).chooseSuitesBundle(bundle)
      }

    assertThat(needToRebuild).isFalse()
  }

  @Test
  fun testKotlinMultiplatformUnitTestRunConfigurationFromDirectory() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    val configuration = createAndroidGradleTestConfigurationFromDirectory(project, "kmpFirstLib/src/androidUnitTest")
    assertThat(configuration).isNotNull()
    assertThat(configuration!!.settings.taskNames).containsExactly(
      ":kmpFirstLib:cleanTestAndroidUnitTest",
      ":kmpFirstLib:testAndroidUnitTest",
    )
  }

  @Test
  fun testKotlinMultiplatformUnitTestRunConfigurationFromClass() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    val configuration = createAndroidGradleTestConfigurationFromClass(project, "com.example.kmpfirstlib.KmpAndroidFirstLibClassTest")
    assertThat(configuration).isNotNull()
    assertThat(configuration!!.settings.taskNames).containsExactly(
      ":kmpFirstLib:cleanTestAndroidUnitTest",
      ":kmpFirstLib:testAndroidUnitTest",
      "--tests", "\"com.example.kmpfirstlib.KmpAndroidFirstLibClassTest\""
    )
  }

  @Test
  fun testKotlinMultiplatformUnitTestRunConfigurationFromMethod() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    val configuration = createAndroidGradleTestConfigurationFromMethod(project, "com.example.kmpfirstlib.KmpAndroidFirstLibClassTest", "testThatPasses")
    assertThat(configuration).isNotNull()
    assertThat(configuration!!.settings.taskNames).containsExactly(
      ":kmpFirstLib:cleanTestAndroidUnitTest",
      ":kmpFirstLib:testAndroidUnitTest",
      "--tests", "\"com.example.kmpfirstlib.KmpAndroidFirstLibClassTest.testThatPasses\""
    )
  }

  @Test
  fun testKotlinMultiplatformCommonUnitTestRunConfigurationFromClass() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    val configuration = createAndroidGradleTestConfigurationFromClass(project, "com.example.kmpfirstlib.KmpCommonFirstLibClassTest")
    assertThat(configuration).isNotNull()
    assertThat(configuration!!.settings.taskNames).containsExactly(
      ":kmpFirstLib:cleanTestAndroidUnitTest",
      ":kmpFirstLib:testAndroidUnitTest",
      "--tests", "\"com.example.kmpfirstlib.KmpCommonFirstLibClassTest\""
    )
  }

  // For reference: b/389733593
  @Test
  fun testOnlyUnitTestConfigurationIsCreatedWhenAndroidTestIsDisabled() {
    projectRule.loadProject(SIMPLE_APPLICATION) { root ->
      val appBuildFile = File(root, "app/build.gradle")
      appBuildFile.appendText("""

        androidComponents {
          beforeVariants(selector().all()) { variant ->
            variant.androidTest.enable = false
          }
        }
      """.trimIndent())
    }
    // Verify we cannot create androidTest RC from UnitTest class.
    assertThat(createAndroidTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")).isNull()
    // Verify we cannot create androidTest RC from UnitTest method.
    assertThat(createAndroidTestConfigurationFromMethod(project, "google.simpleapplication.UnitTest", "passingTest")).isNull()
    // Verify we cannot create androidTest RC from UnitTest directory.
    assertThat(createAndroidTestConfigurationFromDirectory(project, "app/src/test/java")).isNull()

    // Now verify that we can instead create unitTest RCs from all these contexts.
    assertThat(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")).isNotNull()
    assertThat(createAndroidGradleTestConfigurationFromMethod(project, "google.simpleapplication.UnitTest", "passingTest")).isNotNull()
    assertThat(createAndroidGradleTestConfigurationFromDirectory(project, "app/src/test/java")).isNotNull()
  }

  private fun createConfigurationFromContext(psiFile: PsiElement): ConfigurationFromContextImpl? {
    val context = TestConfigurationTestingUtil.createContext(project, psiFile)
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

  private fun verifyCannotCreateKotlinClassGradleConfigurationFromAndroidTestScope() {
    assertThat(
      createAndroidGradleTestConfigurationFromFile(project, "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"))
      .isNull()
  }

  private fun verifyCanCreateClassGradleRunConfigurationFromTestScope() {
    assertThat(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest")).isNotNull()
  }

  private fun verifyCannotCreateClassGradleRunConfigurationFromAndroidTestScope() {
    assertThat(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest")).isNull()
  }

  private fun verifyCannotCreateDirectoryGradleRunConfigurationFromAndroidTestDirectory(directory: String) {
    assertThat(createAndroidGradleTestConfigurationFromDirectory(project, directory)).isNull()
  }

  private fun verifyCannotCreateMethodGradleRunConfigurationFromAndroidTestMethodScope() {
    assertThat(createAndroidGradleTestConfigurationFromMethod(project, "google.simpleapplication.ApplicationTest", "exampleTest")).isNull()
  }

  private fun verifyCanCreateKotlinClassGradleConfigurationFromAndroidUnitTest() {
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project,
                                                             "module2/src/androidUnitTest/kotlin/com/example/library/TestUnitTest.kt",
                                                             false) as PsiFile
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

  private fun verifyCanCreateKotlinDirectoryGradleConfigurationFromAndroidUnitTest() {
    val psiFile = TestConfigurationTestingUtil.getPsiElement(project, "module2/src/androidUnitTest/kotlin/com/example/library", true)
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
    val gradleRunConfiguration = createAndroidGradleTestConfigurationFromDirectory(project, "app/src/test/java")
    val testTaskNames = gradleRunConfiguration?.settings?.taskNames
    assertThat(testTaskNames).containsExactly(":app:testDebugUnitTest")
  }

  private fun verifyCanCreateGradleConfigurationFromSameNameTestClass() {
    var psiElement = TestConfigurationTestingUtil.getPsiElement(project, "app/src/test/java/google/simpleapplication/UnitTest.java", false)
    val appGradleTestClassConfiguration = psiElement.createGradleRunConfiguration()
    assertThat(appGradleTestClassConfiguration).isNotNull()

    psiElement = TestConfigurationTestingUtil.getPsiElement(project, "libs/src/test/java/google/simpleapplication/UnitTest.java", false)
    var libGradleTestClassConfiguration = findExistingGradleTestConfigurationFromPsiElement(project, psiElement)
    // Verify that Gradle doesn't consider the run configuration in libs module equal to the run configuration in app module.
    // The run configuration is null in this case because we can successfully detect in the tasks name that the modules are different
    // between the two contexts.
    assertThat(libGradleTestClassConfiguration).isNull()

    libGradleTestClassConfiguration = psiElement.createGradleRunConfiguration()
    assertThat(libGradleTestClassConfiguration).isNotNull()
    assertThat(libGradleTestClassConfiguration).isNotSameAs(appGradleTestClassConfiguration)
  }

  private fun verifyCanCreateGradleConfigurationFromSameNameTestDirectory() {
    val appModulePsiElement = TestConfigurationTestingUtil.getPsiElement(project, "app/src/test/java/google/simpleapplication", true)
    val appGradleTestPackageConfiguration = appModulePsiElement.createGradleRunConfiguration()
    assertThat(appGradleTestPackageConfiguration).isNotNull()

    val libModulePsiLocation = TestConfigurationTestingUtil.getPsiElement(project, "libs/src/test/java/google/simpleapplication", true)
    val libExistingTestPackageConfiguration = findExistingGradleTestConfigurationFromPsiElement(project, libModulePsiLocation)
    // Verify that Gradle doesn't consider the run configuration in libs module equal to the run configuration in app module.
    // The run configuration is null in this case because we can successfully detect in the tasks name that the modules are different
    // between the two contexts.
    assertThat(libExistingTestPackageConfiguration).isNull()

    val libGradleTestPackageConfiguration = libModulePsiLocation.createGradleRunConfiguration()
    assertThat(libGradleTestPackageConfiguration).isNotNull()
    assertThat(libGradleTestPackageConfiguration).isNotSameAs(appGradleTestPackageConfiguration)
  }

  private fun findExistingGradleTestConfigurationFromPsiElement(project: Project, psiElement: PsiElement): GradleRunConfiguration? {
    val context = TestConfigurationTestingUtil.createContext(project, psiElement)
    // Search for any existing run configuration that was created from this context.
    return context.findExisting()?.configuration as? GradleRunConfiguration
  }

  private fun verifyCanCreateGradleConfigurationFromTestDirectoryKotlin() {
    val gradleRunConfiguration = createAndroidGradleTestConfigurationFromDirectory(project, "app/src/test/java")
    val testTaskNames = gradleRunConfiguration?.settings?.taskNames
    assertThat(testTaskNames).containsExactly(":app:testDebugUnitTest")
  }
}
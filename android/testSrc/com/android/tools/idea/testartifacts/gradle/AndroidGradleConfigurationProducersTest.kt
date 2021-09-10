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
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromDirectory
import com.android.tools.idea.testartifacts.createAndroidGradleConfigurationFromFile
import com.android.tools.idea.testartifacts.createAndroidGradleTestConfigurationFromClass
import com.android.tools.idea.testartifacts.getPsiElement

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN
import com.android.tools.idea.testing.TestProjectPaths.TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM
import com.android.tools.idea.testing.TestProjectPaths.UNIT_TESTING
import com.google.common.truth.Truth
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.PsiJavaDirectoryImpl
import junit.framework.TestCase
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestTasksProvider
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * Tests for producing Gradle Run Configuration for Android unit test.
 */
class AndroidGradleConfigurationProducersTest : AndroidGradleTestCase() {

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestClass() {
    loadSimpleApplication()
    TestCase.assertNotNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.UnitTest"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestClass() {
    loadSimpleApplication()
    TestCase.assertNull(createAndroidGradleTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest"))
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestDirectory() {
    loadSimpleApplication()
    TestCase.assertNotNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/test/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestDirectory() {
    loadSimpleApplication()
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(project, "app/src/androidTest/java"))
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromTestDirectoryKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNotNull(createAndroidGradleConfigurationFromDirectory(
      project, "app/src/test/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestDirectoryKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNull(createAndroidGradleConfigurationFromDirectory(
      project, "app/src/androidTest/java"))
  }

  @Throws(Exception::class)
  fun testCannotCreateGradleConfigurationFromTestClassKotlin() {
    loadProject(TEST_ARTIFACTS_KOTLIN)
    TestCase.assertNull(createAndroidGradleConfigurationFromFile(
      project, "app/src/androidTest/java/com/example/android/kotlin/ExampleInstrumentedTest.kt"))
  }

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromAndroidTestKotlinClass() {
    loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM)
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

  @Throws(Exception::class)
  fun testCanCreateGradleConfigurationFromAndroidTestKotlinDirectory() {
    loadProject(TEST_ARTIFACTS_KOTLIN_MULTIPLATFORM)
    val psiFile = getPsiElement(project, "module2/src/androidTest/kotlin/com/example/library", true) as PsiJavaDirectoryImpl
    // Create a runConfiguration context based on the testClass.
    val configurationFromContext = createConfigurationFromContext(psiFile)
    TestCase.assertNotNull(configurationFromContext)
    // Make sure that the configuration is created by the AllInPackage gradle provider.
    assertTrue(configurationFromContext!!.configurationProducer is AllInPackageGradleConfigurationProducer)

    // Check that the run Configuration is a GradleRunConfiguration, and has tasks to run tests.
    checkConfigurationTasksAreAsExpected(
      configurationFromContext,
      psiFile.virtualFile,
      "kotlinMultiPlatform.module2"
    )
  }

  @Throws(Exception::class)
  fun testAndroidGradleTestTasksProviderDoesntCreateJavaModulesTestTasks() {
    loadProject(UNIT_TESTING)
    // Here we test to verify that the configuration tasks aren't provided by the AndroidGradleTestTasksProvider.
    // We do that by verifying the tasks value.
    val gradleJavaConfiguration = createAndroidGradleTestConfigurationFromClass(
      project, "com.example.javalib.JavaLibJavaTest")
    TestCase.assertNotNull(gradleJavaConfiguration)
    Truth.assertThat(gradleJavaConfiguration!!.settings.taskNames).isEqualTo(listOf(":javalib:test"))
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
    val androidGradleTestTasksProvider = GradleTestTasksProvider.EP_NAME.extensions.filterIsInstance<AndroidGradleTestTasksProvider>().first()
    TestCase.assertNotNull(androidGradleTestTasksProvider)

    TestCase.assertEquals(configuration!!.settings.taskNames, androidGradleTestTasksProvider.getTasks(module2, file))
  }
}
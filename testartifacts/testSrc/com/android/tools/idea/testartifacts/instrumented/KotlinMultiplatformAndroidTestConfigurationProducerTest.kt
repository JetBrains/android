/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/**
 * Test for {@link KotlinMultiplatformAndroidTestConfigurationProducer}
 */
class KotlinMultiplatformAndroidTestConfigurationProducerTest: AndroidGradleTestCase() {

  override fun shouldRunTest(): Boolean {
    // Do not run tests on Windows (see http://b.android.com/222904)
    return !SystemInfo.isWindows && super.shouldRunTest();
  }

  @Throws(Exception::class)
  fun testCanCreateMultipleTestConfigurationFromCommonTestDirectory() {
    loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    // make sure that we do both set up Android Run configs and unit test Run config.
    val element = TestConfigurationTesting.getPsiElement(project, "kmpFirstLib/src/commonTest/kotlin", true)
    val runConfigs = TestConfigurationTesting.createConfigurationsFromPsiElement(project, element)

    assertNotNull(runConfigs)
    assertTrue(runConfigs!!.isNotEmpty())
    assertThat(runConfigs.size).isEqualTo(2)
    val configurations = runConfigs.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertNotNull(androidRunConfig)
    assertThat(androidRunConfig!!.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty()
    assertThat(androidRunConfig.CLASS_NAME).isEmpty()
    assertThat(androidRunConfig.METHOD_NAME).isEmpty()
    assertThat(androidRunConfig.TEST_NAME_REGEX).isEmpty()
    assertThat(androidRunConfig.suggestedName()).isEqualTo("All Tests")

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertNotNull(unitTestConfig)
    assertTrue(unitTestConfig!!.isRunAsTest)
  }

  @Throws(Exception::class)
  fun testCanCreateMultipleTestConfigurationFromCommonTestClass() {
    loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    // make sure that we do both set up Android Run configs and unit test Run config.
    val element = JavaPsiFacade.getInstance(project).findClass("com.example.kmpfirstlib.KmpCommonFirstLibClassTest", GlobalSearchScope.projectScope(project))
    val runConfigs = element?.let { TestConfigurationTesting.createConfigurationsFromPsiElement(project, it) }

    assertNotNull(runConfigs)
    assertTrue(runConfigs!!.isNotEmpty())
    assertThat(runConfigs.size).isEqualTo(2)
    val configurations = runConfigs.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertNotNull(androidRunConfig)
    assertThat(androidRunConfig!!.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_CLASS)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty()
    assertThat(androidRunConfig.CLASS_NAME).isEqualTo("com.example.kmpfirstlib.KmpCommonFirstLibClassTest")
    assertThat(androidRunConfig.METHOD_NAME).isEmpty()

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertNotNull(unitTestConfig)
    assertTrue(unitTestConfig!!.isRunAsTest)
  }

  @Throws(Exception::class)
  fun testCanCreateMultipleTestConfigurationFromCommonTestMethod() {
    loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    // make sure that we do both set up Android Run configs and unit test Run config.
    val methods = myFixture.findClass("com.example.kmpfirstlib.KmpCommonFirstLibClassTest")
      .findMethodsByName("testThatPasses", false)
    assertThat(methods).hasLength(1)
    val runConfigs = TestConfigurationTesting.createConfigurationsFromPsiElement(project, methods[0])

    assertNotNull(runConfigs)
    assertTrue(runConfigs!!.isNotEmpty())
    assertThat(runConfigs.size).isEqualTo(2)
    val configurations = runConfigs.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertNotNull(androidRunConfig)
    assertThat(androidRunConfig!!.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_METHOD)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty()
    assertThat(androidRunConfig.CLASS_NAME).isEqualTo("com.example.kmpfirstlib.KmpCommonFirstLibClassTest")
    assertThat(androidRunConfig.METHOD_NAME).isEqualTo("testThatPasses")

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertNotNull(unitTestConfig)
    assertTrue(unitTestConfig!!.isRunAsTest)
  }

  @Throws(Exception::class)
  fun testCreateMultiplatformCommonAllInPackageTest() {
    loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    val element = TestConfigurationTesting.getPsiElement(project, "kmpFirstLib/src/commonTest/kotlin/com/example/kmpfirstlib", true)
    val runConfigs = TestConfigurationTesting.createConfigurationsFromPsiElement(project, element)

    assertNotNull(runConfigs)
    assertTrue(runConfigs!!.isNotEmpty())
    assertThat(runConfigs.size).isEqualTo(2)
    val configurations = runConfigs.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertNotNull(androidRunConfig)
    assertEmpty(androidRunConfig!!.checkConfiguration(myAndroidFacet))
    assertThat(androidRunConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEqualTo("com.example.kmpfirstlib")
    assertThat(androidRunConfig.CLASS_NAME).isEmpty()
    assertThat(androidRunConfig.METHOD_NAME).isEmpty()

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertNotNull(unitTestConfig)
    assertTrue(unitTestConfig!!.isRunAsTest)
  }
}

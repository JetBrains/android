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

import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Class
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.createConfigurations
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.getPsiElement
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Directory
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Method
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Test for {@link KotlinMultiplatformAndroidTestConfigurationProducer}
 */
@RunsInEdt
class KotlinMultiplatformAndroidTestConfigurationProducerTest {
  val projectRule = AndroidGradleProjectRule()
  @get:Rule
  val rule = projectRule.onEdt()

  @Before
  fun assumeNotWindows() {
    Assume.assumeFalse(SystemInfo.isWindows)
  }

  @Test
  fun testCanCreateMultipleTestConfigurationFromCommonTestDirectory() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    // make sure that we do both set up Android Run configs and unit test Run config.
    val element = projectRule.project.getPsiElement(Directory("kmpFirstLib/src/commonTest/kotlin"))
    val runConfigs = element.createConfigurations()

    assertThat(runConfigs).isNotNull()
    assertThat(runConfigs).hasSize(2)
    val configurations = runConfigs!!.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertThat(androidRunConfig).isNotNull()
    assertThat(androidRunConfig!!.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_MODULE)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty()
    assertThat(androidRunConfig.CLASS_NAME).isEmpty()
    assertThat(androidRunConfig.METHOD_NAME).isEmpty()
    assertThat(androidRunConfig.TEST_NAME_REGEX).isEmpty()
    assertThat(androidRunConfig.suggestedName()).isEqualTo("All Tests")

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertThat(unitTestConfig).isNotNull()
    assertThat(unitTestConfig!!.isRunAsTest).isTrue()
  }

  @Test
  fun testCanCreateMultipleTestConfigurationFromCommonTestClass() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    // make sure that we do both set up Android Run configs and unit test Run config.
    val element = projectRule.project.getPsiElement(Class("com.example.kmpfirstlib.KmpCommonFirstLibClassTest"))
    val runConfigs = element.createConfigurations()

    assertThat(runConfigs).isNotNull()
    assertThat(runConfigs).hasSize(2)
    val configurations = runConfigs!!.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertThat(androidRunConfig).isNotNull()
    assertThat(androidRunConfig!!.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_CLASS)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty()
    assertThat(androidRunConfig.CLASS_NAME).isEqualTo("com.example.kmpfirstlib.KmpCommonFirstLibClassTest")
    assertThat(androidRunConfig.METHOD_NAME).isEmpty()

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertThat(unitTestConfig).isNotNull()
    assertThat(unitTestConfig!!.isRunAsTest).isTrue()
  }

  @Test
  fun testCanCreateMultipleTestConfigurationFromCommonTestMethod() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    val element = projectRule.project.getPsiElement(Method("com.example.kmpfirstlib.KmpCommonFirstLibClassTest", "testThatPasses"))
    val runConfigs = element.createConfigurations()

    assertThat(runConfigs).isNotNull()
    assertThat(runConfigs).hasSize(2)
    val configurations = runConfigs!!.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertThat(androidRunConfig).isNotNull()
    assertThat(androidRunConfig!!.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_METHOD)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEmpty()
    assertThat(androidRunConfig.CLASS_NAME).isEqualTo("com.example.kmpfirstlib.KmpCommonFirstLibClassTest")
    assertThat(androidRunConfig.METHOD_NAME).isEqualTo("testThatPasses")

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertThat(unitTestConfig).isNotNull()
    assertThat(unitTestConfig!!.isRunAsTest).isTrue()
  }

  @Test
  fun testCreateMultiplatformCommonAllInPackageTest() {
    projectRule.loadProject(ANDROID_KOTLIN_MULTIPLATFORM)
    val element = projectRule.project.getPsiElement(Directory("kmpFirstLib/src/commonTest/kotlin/com/example/kmpfirstlib"))
    val runConfigs = element.createConfigurations()

    assertThat(runConfigs).isNotNull()
    assertThat(runConfigs).hasSize(2)
    val configurations = runConfigs!!.map { it.configuration }
    // Check we have a AndroidRunConfiguration created from this context.
    val androidRunConfig = configurations.find { it is AndroidTestRunConfiguration } as? AndroidTestRunConfiguration

    assertThat(androidRunConfig).isNotNull()
    assertThat(androidRunConfig!!.checkConfiguration(projectRule.androidTestAndroidFacet(":kmpFirstLib"))).isEmpty()
    assertThat(androidRunConfig.TESTING_TYPE).isEqualTo(AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE)
    assertThat(androidRunConfig.INSTRUMENTATION_RUNNER_CLASS).isEmpty()
    assertThat(androidRunConfig.PACKAGE_NAME).isEqualTo("com.example.kmpfirstlib")
    assertThat(androidRunConfig.CLASS_NAME).isEmpty()
    assertThat(androidRunConfig.METHOD_NAME).isEmpty()

    // Check that we also have a unit test Run config created too.
    val unitTestConfig = configurations.find { it is GradleRunConfiguration } as? GradleRunConfiguration
    assertThat(unitTestConfig).isNotNull()
    assertThat(unitTestConfig!!.isRunAsTest).isTrue()
  }
}

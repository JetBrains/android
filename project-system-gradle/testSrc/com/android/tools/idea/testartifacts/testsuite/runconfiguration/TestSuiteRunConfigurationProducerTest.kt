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
package com.android.tools.idea.testartifacts.testsuite.runconfiguration

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeTestSuite
import com.android.tools.idea.gradle.model.impl.IdeJUnitEngineInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteTargetImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteVariantTargetImpl
import com.android.tools.idea.testartifacts.testsuite.TestSuiteTestUtils.createAssetsTestSuiteSource
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RuleChain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class TestSuiteRunConfigurationProducerTest {

  val projectRule =
    AndroidProjectRule.withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        selectedBuildVariant = "debug",
        projectBuilder =
          AndroidProjectBuilder(
            projectType = { IdeAndroidProjectType.PROJECT_TYPE_APP },
            namespace = { "com.example.app" },
            mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
            testSuites = {
              listOf(
                IdeTestSuiteImpl(
                  name = "myTestSuite",
                  sources = listOf(
                    createAssetsTestSuiteSource(
                      testSuitePath = moduleBasePath.resolve("src/myTestSuite")
                    )
                  ),
                  junitEngineInfo = IdeJUnitEngineInfoImpl(includedEngines = setOf("engine1")),
                  targetedVariants = listOf("debug")
                )
              )
            },
            testSuiteArtifactsStub = {
              listOf(IdeTestSuiteVariantTargetImpl(suiteName = "myTestSuite", targetedVariantName = "debug", targets = listOf(
                IdeTestSuiteTargetImpl(targetName = "connectedTest", testTaskName = "myTestSuiteTaskName", targetedDevices = listOf()))))
            }
          ),
      ),
    )

  @get:Rule
  val ruleChain =
    RuleChain(
      projectRule,
      FlagRule(StudioFlags.AGP_TEST_SUITES_ENABLED, true),
    )

  private lateinit var producer: TestSuiteRunConfigurationProducer
  private lateinit var mockContext: ConfigurationContext
  private lateinit var mockTestSuite: IdeTestSuite
  private lateinit var testSuiteModule: Module

  @Before
  fun setUp() {
    producer = TestSuiteRunConfigurationProducer()

    val testFile = projectRule.fixture.addFileToProject("app/src/myTestSuite/assets/test.xml", "")
    testSuiteModule =
      ModuleUtilCore.findModuleForFile(testFile.virtualFile, projectRule.project)!!

    // Mock the context and dependencies
    val mockPsiElement = mock<PsiElement> {
      on { project } doReturn projectRule.project
      on { containingFile } doReturn testFile
    }
    val mockLocation = mock<Location<PsiElement>> {
      on { virtualFile } doReturn testFile.virtualFile
    }
    mockContext = mock {
      on { location } doReturn mockLocation
      on { psiLocation } doReturn mockPsiElement
      on { module } doReturn testSuiteModule
    }
    mockTestSuite = mock {
      on { name } doReturn "myTestSuite"
      on { junitEngineInfo } doReturn IdeJUnitEngineInfoImpl(includedEngines = setOf("engine1"))
    }
  }

  @Test
  fun setupConfigurationFromContext_success() {
    val configuration = TestSuiteRunConfiguration(projectRule.project, producer.configurationFactory, "")

    val result = producer.setupConfigurationFromContext(configuration, mockContext, Ref(mockContext.psiLocation))

    assertTrue(result)
    assertEquals("All myTestSuite tests", configuration.name)
    assertEquals("myTestSuiteTaskName", configuration.getTaskName())
    assertEquals(setOf("engine1"), configuration.getTestEngineIds())
  }

  @Test
  fun setupConfigurationFromContext_flagDisabled() {
    StudioFlags.AGP_TEST_SUITES_ENABLED.override(false)
    val configuration = TestSuiteRunConfiguration(projectRule.project, producer.configurationFactory, "")

    val result = producer.setupConfigurationFromContext(configuration, mockContext, Ref(mockContext.psiLocation))

    assertFalse(result)
  }

  @Test
  fun isConfigurationFromContext_match() {
    val configuration = TestSuiteRunConfiguration(projectRule.project, producer.configurationFactory, "All myTestSuite tests")
    configuration.setTaskName("myTestSuiteTaskName")
    configuration.setTestEngineIds(setOf("engine1"))
    configuration.settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(testSuiteModule)

    val result = producer.isConfigurationFromContext(configuration, mockContext)

    assertTrue(result)
  }

  @Test
  fun isConfigurationFromContext_match_whenUserRenamesConfiguration() {
    val configuration = TestSuiteRunConfiguration(projectRule.project, producer.configurationFactory, "User updated name")
    configuration.setTaskName("myTestSuiteTaskName")
    configuration.setTestEngineIds(setOf("engine1"))
    configuration.settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(testSuiteModule)

    val result = producer.isConfigurationFromContext(configuration, mockContext)

    assertTrue(result)
  }

  @Test
  fun isConfigurationFromContext_noMatch_wrongTask() {
    val configuration = TestSuiteRunConfiguration(projectRule.project, producer.configurationFactory, "All myTestSuite tests")
    configuration.setTaskName("wrongTask")
    configuration.setTestEngineIds(setOf("engine1"))
    configuration.settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(testSuiteModule)

    val result = producer.isConfigurationFromContext(configuration, mockContext)

    assertFalse(result)
  }

  @Test
  fun isConfigurationFromContext_noMatch_whenWrongModule() {
    val configuration = TestSuiteRunConfiguration(projectRule.project, producer.configurationFactory, "All myTestSuite tests")
    configuration.setTaskName("myTestSuiteTaskName")
    configuration.setTestEngineIds(setOf("engine1"))
    // Configure the top-level module path rather than the test suite module path
    configuration.settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(projectRule.module)

    val result = producer.isConfigurationFromContext(configuration, mockContext)

    assertFalse(result)
  }

}
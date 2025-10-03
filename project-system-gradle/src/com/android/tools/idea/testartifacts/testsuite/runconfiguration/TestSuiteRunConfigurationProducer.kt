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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeTestSuite
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

class TestSuiteRunConfigurationProducer : LazyRunConfigurationProducer<TestSuiteRunConfiguration>() {

  override fun getConfigurationFactory(): ConfigurationFactory =
    runConfigurationType<TestSuiteRunConfigurationType>().configurationFactories[0]

  public override fun setupConfigurationFromContext(
    configuration: TestSuiteRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>,
  ): Boolean {
    if (!StudioFlags.AGP_TEST_SUITES_ENABLED.get()) {
      return false
    }

    val file = context.location?.virtualFile ?: return false
    val module = context.module ?: return false
    val androidModel = GradleAndroidModel.get(module) ?: return false

    // Test suite root directories are only supported for now
    if (!file.isDirectory || TestSuiteUtils.getTestSuiteAtRoot(androidModel.testSuites, file) == null) {
      return false
    }

    val testSuiteContext = getTestSuiteContext(file, module, androidModel) ?: return false
    configuration.name = generateConfigurationName(testSuiteContext.testSuite)
    configuration.settings.externalProjectPath = testSuiteContext.externalProjectPath
    // TODO(b/446654477): Allow user to select the target
    configuration.addTaskName(testSuiteContext.targets.first().testTaskName)
    configuration.setTestEngineIds(testSuiteContext.testSuite.junitEngineInfo.includedEngines)

    return true
  }

  override fun isConfigurationFromContext(
    configuration: TestSuiteRunConfiguration,
    context: ConfigurationContext,
  ): Boolean {
    if (!StudioFlags.AGP_TEST_SUITES_ENABLED.get()) {
      return false
    }

    val file = context.location?.virtualFile ?: return false
    val module = context.module ?: return false
    val androidModel = GradleAndroidModel.get(module) ?: return false

    // Test suite root directories are only supported for now
    if (!file.isDirectory || TestSuiteUtils.getTestSuiteAtRoot(androidModel.testSuites, file) == null) {
      return false
    }

    val testSuiteContext = getTestSuiteContext(file, module, androidModel) ?: return false
    val targetTaskNames = testSuiteContext.targets.map { it.testTaskName }.toSet()
    return configuration.settings.externalProjectPath == testSuiteContext.externalProjectPath &&
           configuration.getTaskNames().any { it in targetTaskNames } &&
           configuration.getTestEngineIds() == testSuiteContext.testSuite.junitEngineInfo.includedEngines
  }

  private data class TestSuiteContext(
    val externalProjectPath: String,
    val testSuite: IdeTestSuite,
    val targets: List<TestSuiteUtils.TestSuiteTarget>,
  )

  private fun getTestSuiteContext(file: VirtualFile, module: Module, androidModel: GradleAndroidModel): TestSuiteContext? {
    val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    val testSuite =
      TestSuiteUtils.getTestSuiteAtRoot(androidModel.testSuites, file)
      ?: TestSuiteUtils.getTestSuiteContainingFile(androidModel.testSuites, file)
      ?: return null
    val targets = TestSuiteUtils.getTestSuiteTargets(androidModel.selectedVariant, testSuite.name)
    if (targets.isEmpty()) {
      return null
    }

    return TestSuiteContext(externalProjectPath, testSuite, targets)
  }

  private fun generateConfigurationName(testSuite: IdeTestSuite): String {
    return "All ${testSuite.name} tests"
  }
}

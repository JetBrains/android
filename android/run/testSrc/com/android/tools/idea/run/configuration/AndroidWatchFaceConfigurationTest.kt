/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.execution.common.DeployableToDevice
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeId
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.Rule
import org.junit.Test

class AndroidWatchFaceConfigurationTest {

  @get:Rule
  val projectRule = ProjectRule()

  val project: Project
    get() = projectRule.project

  private val projectFacetManager = newProjectFacetManager()

  @Test
  fun testProgramRunnerAvailable() {
    project.replaceService(ProjectFacetManager::class.java, projectFacetManager, projectRule.disposable)

    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run watch face", AndroidWatchFaceConfigurationType().configurationFactories.single())

    val runnerForRun = ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, configSettings.configuration)
    assertThat(runnerForRun).isNotNull()

    val runnerForDebug = ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, configSettings.configuration)
    assertThat(runnerForDebug).isNotNull()
  }

  @Test
  fun testDeploysToLocalDevice() {
    project.replaceService(ProjectFacetManager::class.java, projectFacetManager, projectRule.disposable)

    val configSettings = RunManager.getInstance(project).createConfiguration(
      "run watch face", AndroidWatchFaceConfigurationType().configurationFactories.single())

    assertThat(DeployableToDevice.deploysToLocalDevice(configSettings.configuration)).isTrue()
  }

  private fun newProjectFacetManager(): ProjectFacetManager {
    return object : ProjectFacetManager() {
      override fun hasFacets(typeId: FacetTypeId<*>): Boolean {
        return true
      }

      override fun <F : Facet<*>?> getFacets(typeId: FacetTypeId<F>, modules: Array<Module>): List<F> {
        return emptyList()
      }

      override fun <F : Facet<*>?> getFacets(typeId: FacetTypeId<F>): List<F> {
        return emptyList()
      }

      override fun getModulesWithFacet(typeId: FacetTypeId<*>): List<Module> {
        return emptyList()
      }

      override fun <C : FacetConfiguration?> createDefaultConfiguration(facetType: FacetType<*, C>): C? {
        return null
      }

      override fun <C : FacetConfiguration?> setDefaultConfiguration(facetType: FacetType<*, C>, configuration: C & Any) {
      }
    }
  }
}
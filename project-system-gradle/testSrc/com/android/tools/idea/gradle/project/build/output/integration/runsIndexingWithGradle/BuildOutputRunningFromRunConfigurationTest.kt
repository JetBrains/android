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
package com.android.tools.idea.gradle.project.build.output.integration.runsIndexingWithGradle

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.getPsiElement
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.ibm.icu.impl.Assert
import com.intellij.build.BuildViewManager
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.jetbrains.rd.util.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

class BuildOutputRunningFromRunConfigurationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun `run gradle unit test`() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)

    preparedProject.open { project ->
      val runnerAndConfigurationSettings = runInEdtAndGet {
        val psiElement = project.getPsiElement(
          TestConfigurationTestingUtil.File("app/src/test/java/google/simpleapplication/UnitTest.java"))
        createRunnerAndConfigurationSettingsFromPsiElement(project, psiElement)
      }
      val latch = CountDownLatch(1)
      val processStartError = AtomicReference<Throwable?>(null)

      val executor = DefaultRunExecutor.getRunExecutorInstance()
      val executionEnvironment = ExecutionEnvironmentBuilder.create(executor, runnerAndConfigurationSettings)
        .build(callback = object : ProgramRunner.Callback {
          override fun processStarted(descriptor: RunContentDescriptor?) = Unit

          override fun processNotStarted(error: Throwable?) {
            processStartError.getAndSet(error)
            latch.countDown()
          }
        })

      val output = mutableListOf<String>()
      // Need to replace with real BuildViewManager so that all listeners work as in prod.
      val buildViewManager = BuildViewManager(project)

      project.replaceService(BuildViewManager::class.java, buildViewManager, project)
      buildViewManager.addListener({ buildId, event ->
                                     if (event is OutputBuildEvent) {
                                       if (event.parentId == buildId) {
                                         output.add(event.message)
                                       }
                                     }
                                     if (event is FinishBuildEvent) {
                                       latch.countDown()
                                     }
                                   }, buildViewManager)

      runWriteActionAndWait {
        ProgramRunnerUtil.executeConfiguration(executionEnvironment, false, true)
      }

      latch.await(3, TimeUnit.MINUTES)
      processStartError.get()?.also { Assert.fail(Exception(it)) }

      // Build output verifications:
      try {
        // Verify no duplications in build output windows b/301232493
        Truth.assertWithMessage("Expected single 'Executing tasks: ' line in build output (see log for full output)")
          .that(output.count { it.contains("Executing tasks: ") })
          .isEqualTo(1)
        // Verify test information is not passed via build output b/441472103
        Truth.assertWithMessage("Expected no '<ijLog>' entries in build output (see log for full output)")
          .that(output.count { it.contains("<ijLog>") })
          .isEqualTo(0)
      }
      catch (e : AssertionError) {
        val buildOutput = output.joinToString(separator = "").prependIndent(indent = "|")
        println(buildOutput)
        throw e
      }
    }
  }

  private fun createRunnerAndConfigurationSettingsFromPsiElement(project: Project, psiElement: PsiElement) : RunnerAndConfigurationSettings {
    val context = TestConfigurationTestingUtil.createContext(project, psiElement)
    val settings = requireNotNull(context.configuration)

    val runManager = RunManager.getInstance(project)
    runManager.addConfiguration(settings)
    runManager.selectedConfiguration = settings

    return settings
  }
}
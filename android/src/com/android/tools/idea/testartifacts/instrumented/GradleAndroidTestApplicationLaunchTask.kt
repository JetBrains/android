/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project

/**
 * LaunchTask for delegating instrumentation tests to AGP from AS.
 */
class GradleAndroidTestApplicationLaunchTask private constructor(
  private val project: Project,
  private val androidModuleModel: AndroidModuleModel,
  private val taskId: String,
  private val waitForDebugger: Boolean,
  private val processHandler: ProcessHandler,
  private val consolePrinter: ConsolePrinter,
  private val device: IDevice,
  private val testPackageName: String,
  private val testClassName: String,
  private val testMethodName: String,
  private val myGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
  private val retentionConfiguration: RetentionConfiguration) : AppLaunchTask() {

  companion object {
    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for all-in-module test.
     */
    @JvmStatic
    fun allInModuleTest(
      project: Project,
      androidModuleModel: AndroidModuleModel,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration) : GradleAndroidTestApplicationLaunchTask {
        return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                      taskId, waitForDebugger, processHandler, consolePrinter, device, "",
                                                      "", "", gradleConnectedAndroidTestInvoker, retentionConfiguration)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for all-in-package test.
     */
    @JvmStatic
    fun allInPackageTest(
      project: Project,
      androidModuleModel: AndroidModuleModel,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testPackageName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, processHandler, consolePrinter, device,
                                                    testPackageName, "", "", gradleConnectedAndroidTestInvoker,
                                                    retentionConfiguration)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for a single class test.
     */
    @JvmStatic
    fun classTest(
      project: Project,
      androidModuleModel: AndroidModuleModel,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testClassName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger,
                                                    processHandler, consolePrinter, device, "", testClassName, "",
                                                    gradleConnectedAndroidTestInvoker, retentionConfiguration)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for a single method test.
     */
    @JvmStatic
    fun methodTest(
      project: Project,
      androidModuleModel: AndroidModuleModel,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testClassName: String,
      testMethodName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel, taskId,
                                                    waitForDebugger, processHandler, consolePrinter, device, "",
                                                    testClassName, testMethodName, gradleConnectedAndroidTestInvoker, retentionConfiguration)
    }
  }

  override fun run(launchContext: LaunchContext): LaunchResult {
    if (!checkAndroidGradlePluginVersion()) {
      return LaunchResult.error("ANDROID_TEST_AGP_VERSION_TOO_OLD", "checking the Android Gradle plugin version")
    }
    myGradleConnectedAndroidTestInvoker.schedule(
      project, taskId, processHandler, consolePrinter, androidModuleModel,
      waitForDebugger, testPackageName, testClassName, testMethodName, device, retentionConfiguration)
    return LaunchResult.success()
  }

  private fun checkAndroidGradlePluginVersion(): Boolean {
    val version = androidModuleModel.modelVersion
    return if (version != null && version.major >= 7) {
      true
    } else {
      consolePrinter.stderr("The minimum required Android Gradle plugin version is 7.0.0 but it was ${version ?: "unknown"}.")
      false
    }
  }

  override fun getId(): String = "GRADLE_ANDROID_TEST_APPLICATION_LAUNCH_TASK"
  override fun getDescription(): String = "Launching a connectedAndroidTest for selected devices"
  override fun getDuration(): Int = LaunchTaskDurations.LAUNCH_ACTIVITY
}
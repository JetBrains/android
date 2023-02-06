/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.intellij.openapi.project.Project

/**
 * LaunchTask for delegating instrumentation tests to AGP from AS.
 */
class GradleAndroidTestApplicationLaunchTask private constructor(
  private val project: Project,
  private val androidModuleModel: GradleAndroidModel,
  private val taskId: String,
  private val waitForDebugger: Boolean,
  private val device: IDevice,
  private val testPackageName: String,
  private val testClassName: String,
  private val testMethodName: String,
  private val testRegex: String,
  private val myGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
  private val retentionConfiguration: RetentionConfiguration,
  private val extraInstrumentationOptions: String) : AppLaunchTask() {

  companion object {
    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for all-in-module test.
     */
    @JvmStatic
    fun allInModuleTest(
      project: Project,
      androidModuleModel: GradleAndroidModel,
      taskId: String,
      waitForDebugger: Boolean,
      device: IDevice,
      testRegex: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, device, "",
                                                    "", "", testRegex, gradleConnectedAndroidTestInvoker,
                                                    retentionConfiguration, extraInstrumentationOptions)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for all-in-package test.
     */
    @JvmStatic
    fun allInPackageTest(
      project: Project,
      androidModuleModel: GradleAndroidModel,
      taskId: String,
      waitForDebugger: Boolean,
      device: IDevice,
      testPackageName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, device, testPackageName,
                                                    "", "", "", gradleConnectedAndroidTestInvoker,
                                                    retentionConfiguration, extraInstrumentationOptions)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for a single class test.
     */
    @JvmStatic
    fun classTest(
      project: Project,
      androidModuleModel: GradleAndroidModel,
      taskId: String,
      waitForDebugger: Boolean,
      device: IDevice,
      testClassName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, device, "",
                                                    testClassName, "", "", gradleConnectedAndroidTestInvoker,
                                                    retentionConfiguration, extraInstrumentationOptions)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for a single method test.
     */
    @JvmStatic
    fun methodTest(
      project: Project,
      androidModuleModel: GradleAndroidModel,
      taskId: String,
      waitForDebugger: Boolean,
      device: IDevice,
      testClassName: String,
      testMethodName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel, taskId,
                                                    waitForDebugger, device, "",
                                                    testClassName, testMethodName, "", gradleConnectedAndroidTestInvoker,
                                                    retentionConfiguration, extraInstrumentationOptions)
    }
  }

  override fun run(launchContext: LaunchContext) {
    myGradleConnectedAndroidTestInvoker.schedule(
      project, taskId, launchContext.processHandler, launchContext.consoleView, androidModuleModel,
      waitForDebugger, testPackageName, testClassName, testMethodName, testRegex,
      device, retentionConfiguration, extraInstrumentationOptions)
  }

  override fun getId(): String = "GRADLE_ANDROID_TEST_APPLICATION_LAUNCH_TASK"
  override fun getDescription(): String = "Launching a connectedAndroidTest for selected devices"
  override fun getDuration(): Int = LaunchTaskDurations.LAUNCH_ACTIVITY
}
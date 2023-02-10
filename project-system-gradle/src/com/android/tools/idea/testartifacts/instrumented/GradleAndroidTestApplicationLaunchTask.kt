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
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project

/**
 * Delegates instrumentation tests to AGP from AS.
 */
class GradleAndroidTestApplicationLaunchTask private constructor(
  private val project: Project,
  private val androidModuleModel: GradleAndroidModel,
  private val taskId: String,
  private val waitForDebugger: Boolean,
  private val testPackageName: String,
  private val testClassName: String,
  private val testMethodName: String,
  private val testRegex: String,
  private val myGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
  private val retentionConfiguration: RetentionConfiguration,
  private val extraInstrumentationOptions: String) {

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
      testRegex: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, "", "",
                                                    "", testRegex, gradleConnectedAndroidTestInvoker, retentionConfiguration,
                                                    extraInstrumentationOptions)
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
      testPackageName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, testPackageName, "",
                                                    "", "", gradleConnectedAndroidTestInvoker, retentionConfiguration,
                                                    extraInstrumentationOptions)
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
      testClassName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, "", testClassName,
                                                    "", "", gradleConnectedAndroidTestInvoker, retentionConfiguration,
                                                    extraInstrumentationOptions)
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
      testClassName: String,
      testMethodName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker,
      retentionConfiguration: RetentionConfiguration,
      extraInstrumentationOptions: String): GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel, taskId,
                                                    waitForDebugger, "", testClassName,
                                                    testMethodName, "", gradleConnectedAndroidTestInvoker, retentionConfiguration,
                                                    extraInstrumentationOptions)
    }
  }

  fun run(devices: List<IDevice>, processHandler: ProcessHandler, androidTestSuiteView: AndroidTestSuiteView) {
    myGradleConnectedAndroidTestInvoker.runGradleTask(
      project, devices, taskId, processHandler, androidTestSuiteView, androidModuleModel,
      waitForDebugger, testPackageName, testClassName, testMethodName, testRegex,
      retentionConfiguration, extraInstrumentationOptions)
  }
}
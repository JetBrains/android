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
import com.android.tools.idea.Projects.getBaseDirPath
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.GradleTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.utp.TaskOutputProcessor
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.io.File

/**
 * LaunchTask for delegating instrumentation tests to AGP from AS
 */
class GradleAndroidTestApplicationLaunchTask private constructor(
  private val project: Project,
  private val taskId: String,
  private val waitForDebugger: Boolean,
  private val processHandler: ProcessHandler,
  private val consolePrinter: ConsolePrinter,
  private val device: IDevice,
  private val testPackageName: String,
  private val testClassName: String,
  private val testMethodName: String,
  private val myGradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : AppLaunchTask(){

  companion object {

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for all-in-module test.
     */
    @JvmStatic
    fun allInModuleTest(
      project: Project,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
        return GradleAndroidTestApplicationLaunchTask(project, taskId, waitForDebugger, processHandler, consolePrinter, device, "",
                                                      "", "", gradleConnectedAndroidTestInvoker)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for all-in-package test.
     */
    @JvmStatic
    fun allInPackageTest(
      project: Project,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testPackageName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, taskId, waitForDebugger, processHandler, consolePrinter, device, testPackageName,
                                                    "", "", gradleConnectedAndroidTestInvoker)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for a single class test.
     */
    @JvmStatic
    fun classTest(
      project: Project,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testClassName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, taskId, waitForDebugger,
                                                    processHandler, consolePrinter, device, "", testClassName, "", gradleConnectedAndroidTestInvoker)
    }

    /**
     * Creates [GradleAndroidTestApplicationLaunchTask] for a single method test.
     */
    @JvmStatic
    fun methodTest(
      project: Project,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      testClassName: String,
      testMethodName: String,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, taskId, waitForDebugger, processHandler, consolePrinter, device, "",
                                                    testClassName, testMethodName, gradleConnectedAndroidTestInvoker)
    }
  }

  override fun run(launchContext: LaunchContext): LaunchResult? {
    consolePrinter.stdout("Running tests\n")

    if (myGradleConnectedAndroidTestInvoker.run(device)) {
      val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)
      val adapters = myGradleConnectedAndroidTestInvoker.getDevices().map {
        val adapter = GradleTestResultAdapter(it, taskId, androidTestResultListener)
        adapter.device.id to adapter
      }.toMap()

      val path: File = getBaseDirPath(project)
      val taskNames: List<String> = listOf("connectedAndroidTest")
      val externalTaskId: ExternalSystemTaskId = ExternalSystemTaskId.create(ProjectSystemId(taskId),
                                                                             ExternalSystemTaskType.EXECUTE_TASK, project)
      val taskOutputProcessor = TaskOutputProcessor(adapters)
      val listener: ExternalSystemTaskNotificationListenerAdapter = object : ExternalSystemTaskNotificationListenerAdapter() {
        override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
          super.onTaskOutput(id, text, stdOut)

          val processedText = if (stdOut) {
            taskOutputProcessor.process(text)
          } else {
            text
          }
          consolePrinter.stdout(processedText)
        }

        override fun onEnd(id: ExternalSystemTaskId,) {
          super.onEnd(id)
          processHandler.detachProcess()
        }
      }
      AndroidGradleTaskManager().executeTasks(externalTaskId, taskNames, path.path, getGradleExecutionSettings(), null, listener)
    }
    return LaunchResult.success()
  }

  override fun getId(): String = "GRADLE_ANDROID_TEST_APPLICATION_LAUNCH_TASK"
  override fun getDescription(): String = "Launching a connectedAndroidTest for selected devices"
  override fun getDuration(): Int = LaunchTaskDurations.LAUNCH_ACTIVITY

  @VisibleForTesting
  fun getGradleExecutionSettings(): GradleExecutionSettings {
    var gradleExecutionSettings: GradleExecutionSettings = getOrCreateGradleExecutionSettings(project)
    var deviceSerials = ""
    var devices = myGradleConnectedAndroidTestInvoker.getDevices()
    for (i in devices.indices) {
      deviceSerials += if (i == (devices.size - 1)) {
        "${devices[i].serialNumber}"
      } else {
        "${devices[i].serialNumber},"
      }
    }
    var map: HashMap<String, String> = hashMapOf("ANDROID_SERIAL" to deviceSerials)
    gradleExecutionSettings = gradleExecutionSettings.withEnvironmentVariables(map) as GradleExecutionSettings
    var arguments = ArrayList<String>()
    if (testPackageName != "" || testClassName != "") {
      var testTypeArgs = "-Pandroid.testInstrumentationRunnerArguments"
      if (testPackageName != "") {
        testTypeArgs += ".package=$testPackageName"
      } else if (testClassName != "") {
        testTypeArgs += ".class=$testClassName"
        if (testMethodName != "") {
          testTypeArgs += "#$testMethodName"
        }
      }
      arguments.add(testTypeArgs)
    }
    if (waitForDebugger) {
      arguments.add("-Pandroid.testInstrumentationRunnerArguments.debug=true")
    }
    gradleExecutionSettings = gradleExecutionSettings.withArguments(arguments) as GradleExecutionSettings
    return gradleExecutionSettings
  }
}
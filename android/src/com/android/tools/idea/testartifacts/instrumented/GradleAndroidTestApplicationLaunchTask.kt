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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.gradle.util.GradleUtil.getOrCreateGradleExecutionSettings
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.tasks.AppLaunchTask
import com.android.tools.idea.run.tasks.LaunchContext
import com.android.tools.idea.run.tasks.LaunchResult
import com.android.tools.idea.run.tasks.LaunchTaskDurations
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.GradleTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.utp.GradleAndroidProjectResolverExtension.Companion.ENABLE_UTP_TEST_REPORT_PROPERTY
import com.android.tools.utp.TaskOutputLineProcessor
import com.android.tools.utp.TaskOutputProcessor
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
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
  private val androidModuleModel: AndroidModuleModel,
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
      androidModuleModel: AndroidModuleModel,
      taskId: String,
      waitForDebugger: Boolean,
      processHandler: ProcessHandler,
      consolePrinter: ConsolePrinter,
      device: IDevice,
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
        return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                      taskId, waitForDebugger, processHandler, consolePrinter, device, "",
                                                      "", "", gradleConnectedAndroidTestInvoker)
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
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger, processHandler, consolePrinter, device,
                                                    testPackageName, "", "", gradleConnectedAndroidTestInvoker)
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
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel,
                                                    taskId, waitForDebugger,
                                                    processHandler, consolePrinter, device, "", testClassName, "",
                                                    gradleConnectedAndroidTestInvoker)
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
      gradleConnectedAndroidTestInvoker: GradleConnectedAndroidTestInvoker) : GradleAndroidTestApplicationLaunchTask {
      return GradleAndroidTestApplicationLaunchTask(project, androidModuleModel, taskId,
                                                    waitForDebugger, processHandler, consolePrinter, device, "",
                                                    testClassName, testMethodName, gradleConnectedAndroidTestInvoker)
    }
  }

  override fun run(launchContext: LaunchContext): LaunchResult {
    if (!checkAndroidGradlePluginVersion()) {
      return LaunchResult.error("ANDROID_TEST_AGP_VERSION_TOO_OLD", "checking the Android Gradle plugin version")
    }
    if (myGradleConnectedAndroidTestInvoker.run(device)) {
      consolePrinter.stdout("Running tests\n")
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
        val outputLineProcessor = TaskOutputLineProcessor(object:TaskOutputLineProcessor.LineProcessor {
          override fun processLine(line: String) {
            val processedText = taskOutputProcessor.process(line)
            if (!(processedText.isBlank() && line != processedText)) {
              consolePrinter.stdout(processedText)
            }
          }
        })
        override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
          super.onTaskOutput(id, text, stdOut)
          if (stdOut) {
            outputLineProcessor.append(text)
          } else {
            processHandler.notifyTextAvailable(text, ProcessOutputTypes.STDERR)
          }
        }

        override fun onEnd(id: ExternalSystemTaskId) {
          super.onEnd(id)
          outputLineProcessor.close()
          processHandler.detachProcess()
        }
      }

      processHandler.addProcessListener(object: ProcessAdapter() {
        override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
          if (willBeDestroyed) {
            AndroidGradleTaskManager().cancelTask(externalTaskId, listener)
          }
        }
      })
      AndroidGradleTaskManager().executeTasks(externalTaskId, taskNames, path.path, getGradleExecutionSettings(), null, listener)
    }
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

  @VisibleForTesting
  fun getGradleExecutionSettings(): GradleExecutionSettings {
    return getOrCreateGradleExecutionSettings(project).apply {
      // Add an environmental variable to filter connected devices for selected devices.
      val deviceSerials = myGradleConnectedAndroidTestInvoker.getDevices().joinToString(",") { device ->
        device.serialNumber
      }
      withEnvironmentVariables(mapOf(("ANDROID_SERIAL" to deviceSerials)))

      // Enable UTP in Gradle. This is required for Android Studio integration.
      withArgument("-Pandroid.experimental.androidTest.useUnifiedTestPlatform=true")

      // Enable UTP test results reporting by embedded XML tag in stdout.
      withArgument("-P${ENABLE_UTP_TEST_REPORT_PROPERTY}=true")

      // Add a test filter.
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
        withArgument(testTypeArgs)
      }

      // Enable debug flag for run with debugger.
      if (waitForDebugger) {
        withArgument("-Pandroid.testInstrumentationRunnerArguments.debug=true")
      }
    }
  }
}
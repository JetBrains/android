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
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.GradleTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.utp.GradleAndroidProjectResolverExtension
import com.android.tools.utp.TaskOutputLineProcessor
import com.android.tools.utp.TaskOutputProcessor
import com.android.utils.usLocaleCapitalize
import com.google.common.base.Throwables
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.io.File
import java.lang.Exception
import java.util.concurrent.Future

/**
 * Gradle task invoker to run ConnectedAndroidTask for selected devices at once.
 *
 * @param selectedDevices number of total selected devices to run instrumentation tests
 */
class GradleConnectedAndroidTestInvoker(
  private val selectedDevices: Int,
  private val backgroundTaskExecutor: (Runnable) -> Future<*> = ApplicationManager.getApplication()::executeOnPooledThread,
  private val gradleTaskManagerFactory: () -> GradleTaskManager = { GradleTaskManager() }
) {

  companion object {
    const val RETENTION_ENABLE_PROPERTY = "android.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures"
    const val RETENTION_COMPRESS_SNAPSHOT_PROPERTY = "android.experimental.testOptions.emulatorSnapshots.compressSnapshots"
  }

  private val scheduledDeviceList: MutableList<IDevice> = mutableListOf()

  /**
   * Schedules a given device. Once the number of scheduled devices matches to [selectedDevices],
   * it invokes "connectedAndroidTest" Gradle task.
   */
  fun schedule(project: Project,
               taskId: String,
               processHandler: ProcessHandler,
               consolePrinter: ConsolePrinter,
               androidModuleModel: AndroidModuleModel,
               waitForDebugger: Boolean,
               testPackageName: String,
               testClassName: String,
               testMethodName: String,
               device: IDevice,
               retentionConfiguration: RetentionConfiguration) {
    scheduledDeviceList.add(device)
    if (scheduledDeviceList.size == selectedDevices) {
      runGradleTask(project,
                    taskId,
                    processHandler,
                    consolePrinter,
                    androidModuleModel,
                    waitForDebugger,
                    testPackageName,
                    testClassName,
                    testMethodName,
                    retentionConfiguration)
    }
  }

  /**
   * Runs connectedAndroidTest Gradle task asynchronously.
   */
  private fun runGradleTask(
    project: Project,
    taskId: String,
    processHandler: ProcessHandler,
    consolePrinter: ConsolePrinter,
    androidModuleModel: AndroidModuleModel,
    waitForDebugger: Boolean,
    testPackageName: String,
    testClassName: String,
    testMethodName: String,
    retentionConfiguration: RetentionConfiguration
  ) {
    consolePrinter.stdout("Running tests\n")

    val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)
    val adapters = scheduledDeviceList.map {
      val adapter = GradleTestResultAdapter(it, taskId, androidModuleModel.artifactForAndroidTest, androidTestResultListener)
      adapter.device.id to adapter
    }.toMap()

    val path: File = Projects.getBaseDirPath(project)
    val taskNames: List<String> = getTaskNames(androidModuleModel)
    val externalTaskId: ExternalSystemTaskId = ExternalSystemTaskId.create(ProjectSystemId(taskId),
                                                                           ExternalSystemTaskType.EXECUTE_TASK, project)
    val taskOutputProcessor = TaskOutputProcessor(adapters)
    val listener: ExternalSystemTaskNotificationListenerAdapter = object : ExternalSystemTaskNotificationListenerAdapter() {
      val outputLineProcessor = TaskOutputLineProcessor(object: TaskOutputLineProcessor.LineProcessor {
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

      override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        super.onFailure(id, e)
        Logger.getInstance(GradleConnectedAndroidTestInvoker::class.java).error(e)
        processHandler.notifyTextAvailable(Throwables.getStackTraceAsString(e), ProcessOutputTypes.STDERR)
      }

      override fun onEnd(id: ExternalSystemTaskId) {
        super.onEnd(id)
        outputLineProcessor.close()
        processHandler.detachProcess()
        adapters.values.forEach(GradleTestResultAdapter::onGradleTaskFinished)
      }
    }

    processHandler.addProcessListener(object: ProcessAdapter() {
      override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
        if (willBeDestroyed) {
          AndroidGradleTaskManager().cancelTask(externalTaskId, listener)
        }
      }
    })

    val gradleExecutionSettings = getGradleExecutionSettings(
      project, waitForDebugger, testPackageName, testClassName, testMethodName, retentionConfiguration)

    backgroundTaskExecutor {
      gradleTaskManagerFactory().executeTasks(
        externalTaskId,
        taskNames,
        path.path,
        gradleExecutionSettings,
        null,
        listener)
    }
  }

  private fun getGradleExecutionSettings(
    project: Project,
    waitForDebugger: Boolean,
    testPackageName: String,
    testClassName: String,
    testMethodName: String,
    retentionConfiguration: RetentionConfiguration
  ): GradleExecutionSettings {
    return GradleUtil.getOrCreateGradleExecutionSettings(project).apply {
      // Add an environmental variable to filter connected devices for selected devices.
      val deviceSerials = scheduledDeviceList.joinToString(",") { device ->
        device.serialNumber
      }
      withEnvironmentVariables(mapOf(("ANDROID_SERIAL" to deviceSerials)))

      // Enable UTP in Gradle. This is required for Android Studio integration.
      withArgument("-Pandroid.experimental.androidTest.useUnifiedTestPlatform=true")

      // Enable UTP test results reporting by embedded XML tag in stdout.
      withArgument("-P${GradleAndroidProjectResolverExtension.ENABLE_UTP_TEST_REPORT_PROPERTY}=true")

      if (retentionConfiguration.enabled == EnableRetention.YES) {
        withArgument("-P${RETENTION_ENABLE_PROPERTY}=${retentionConfiguration.maxSnapshots}")
        withArgument("-P${RETENTION_COMPRESS_SNAPSHOT_PROPERTY}=${retentionConfiguration.compressSnapshots}")
      } else if (retentionConfiguration.enabled == EnableRetention.NO) {
        withArgument("-P${RETENTION_ENABLE_PROPERTY}=0")
      }

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

  private fun getTaskNames(androidModuleModel: AndroidModuleModel): List<String> {
    return listOf("connected${androidModuleModel.selectedVariantName.usLocaleCapitalize()}AndroidTest")
  }
}
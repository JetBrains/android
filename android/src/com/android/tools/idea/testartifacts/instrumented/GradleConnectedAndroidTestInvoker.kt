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

import com.android.builder.model.PROPERTY_BUILD_ABI
import com.android.builder.model.PROPERTY_BUILD_API
import com.android.builder.model.PROPERTY_BUILD_API_CODENAME
import com.android.builder.model.PROPERTY_BUILD_DENSITY
import com.android.ddmlib.IDevice
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.run.createSpec
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager.ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE
import com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.run.ConsolePrinter
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.GradleTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.utp.GradleAndroidProjectResolverExtension
import com.android.tools.utp.TaskOutputLineProcessor
import com.android.tools.utp.TaskOutputProcessor
import com.android.utils.usLocaleCapitalize
import com.google.common.base.Joiner
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.io.File
import java.util.concurrent.Future

/**
 * Gradle task invoker to run ConnectedAndroidTask for selected devices at once.
 *
 * @param selectedDevices number of total selected devices to run instrumentation tests
 */
class GradleConnectedAndroidTestInvoker(
  private val selectedDevices: Int,
  private val executionEnvironment: ExecutionEnvironment,
  private val uninstallIncompatibleApks: Boolean = false,
  private val backgroundTaskExecutor: (Runnable) -> Future<*> = ApplicationManager.getApplication()::executeOnPooledThread,
  private val gradleTaskManagerFactory: () -> GradleTaskManager = { GradleTaskManager() },
  private val gradleTestResultAdapterFactory: (IDevice, String, IdeAndroidArtifact?, AndroidTestResultListener) -> GradleTestResultAdapter
  = { iDevice, testSuiteDisplayName, artifact, listener ->
    GradleTestResultAdapter(iDevice, testSuiteDisplayName, artifact, listener)
  },
) {

  companion object {
    const val RETENTION_ENABLE_PROPERTY = "android.experimental.testOptions.emulatorSnapshots.maxSnapshotsForTestFailures"
    const val RETENTION_COMPRESS_SNAPSHOT_PROPERTY = "android.experimental.testOptions.emulatorSnapshots.compressSnapshots"
    const val UNINSTALL_INCOMPATIBLE_APKS_PROPERTY = "android.experimental.testOptions.uninstallIncompatibleApks"
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
    val adapters = scheduledDeviceList.associate {
      val adapter = gradleTestResultAdapterFactory(it, taskId, androidModuleModel.artifactForAndroidTest, androidTestResultListener)
      adapter.device.id to adapter
    }

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

      override fun onEnd(id: ExternalSystemTaskId) {
        super.onEnd(id)
        outputLineProcessor.close()
        adapters.values.forEach(GradleTestResultAdapter::onGradleTaskFinished)

        // If there is an APK installation error due to incompatible APKs installed on device,
        // display a popup and ask a user to rerun the Gradle task with UNINSTALL_INCOMPATIBLE_APKS
        // option.
        var isRerunRequested = false
        val rerunDevices = adapters.values.filter {
          it.needRerunWithUninstallIncompatibleApkOption()
        }
        if (rerunDevices.isNotEmpty()) {
          ApplicationManager.getApplication().invokeAndWait {
            isRerunRequested = rerunDevices.first().showRerunWithUninstallIncompatibleApkOptionDialog(project)
          }
        }
        if (isRerunRequested) {
          // rerunInvoker will call detachProcess().
          val rerunInvoker = GradleConnectedAndroidTestInvoker(
            rerunDevices.size,
            executionEnvironment,
            uninstallIncompatibleApks = true,
            backgroundTaskExecutor,
            gradleTaskManagerFactory,
            gradleTestResultAdapterFactory,
          )
          rerunDevices.forEach {
            androidTestResultListener.onRerunScheduled(it.device)
            rerunInvoker.schedule(
              project,
              taskId,
              processHandler,
              consolePrinter,
              androidModuleModel,
              waitForDebugger,
              testPackageName,
              testClassName,
              testMethodName,
              it.iDevice,
              retentionConfiguration
            )
          }
        } else {
          processHandler.detachProcess()
        }
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

      withArguments(getDeviceSpecificArguments())

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

      if (uninstallIncompatibleApks) {
        withArgument("-P${UNINSTALL_INCOMPATIBLE_APKS_PROPERTY}=true")
      }

      // Don't switch focus to build tool window even after build failure because
      // if there is a test failure, AGP handles it as a build failure and it hides
      // the test result panel if this option is not set.
      putUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE, true)
    }
  }

  private fun getTaskNames(androidModuleModel: AndroidModuleModel): List<String> {
    var modulePrefix = androidModuleModel.moduleName.replace(".", ":")
    val index = modulePrefix.indexOf(":")
    modulePrefix = modulePrefix.substring(index)
    return listOf("${modulePrefix}:connected${androidModuleModel.selectedVariantName.usLocaleCapitalize()}AndroidTest")
  }

  // TODO: This method is copied from com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider#getDeviceSpecificArguments.
  //       which lives in a different module and this class doesn't have access. Factor out the common method into
  //       a shared utility class and them it from both places.
  private fun getDeviceSpecificArguments(): List<String> {
    val deviceFutures = executionEnvironment.getCopyableUserData(DeviceFutures.KEY)?.devices ?: emptyList()
    val deviceSpec = createSpec(deviceFutures) ?: return emptyList()

    val deviceSpecificArguments = mutableListOf<String>()
    deviceSpec.commonVersion?.let { version ->
      deviceSpecificArguments.add(createProjectProperty(PROPERTY_BUILD_API, version.apiLevel.toString()))
      version.codename?.let { codename ->
        deviceSpecificArguments.add(createProjectProperty(PROPERTY_BUILD_API_CODENAME, codename))
      }
    }

    deviceSpec.density?.let { density ->
      deviceSpecificArguments.add(createProjectProperty(PROPERTY_BUILD_DENSITY, density.resourceValue))
    }
    if (deviceSpec.abis.isNotEmpty()) {
      deviceSpecificArguments.add(createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(deviceSpec.abis)))
    }

    return deviceSpecificArguments
  }
}
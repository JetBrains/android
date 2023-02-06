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

import com.android.builder.model.PROPERTY_BUILD_ABI
import com.android.builder.model.PROPERTY_BUILD_API
import com.android.builder.model.PROPERTY_BUILD_API_CODENAME
import com.android.ddmlib.IDevice
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags.API_OPTIMIZATION_ENABLE
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.run.createSpec
import com.android.tools.idea.gradle.task.ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE
import com.android.tools.idea.gradle.task.AndroidGradleTaskManager
import com.android.tools.idea.gradle.util.AndroidGradleSettings.createProjectProperty
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.execution.println
import com.android.tools.idea.run.editor.AndroidTestExtraParam.Companion.parseFromString
import com.android.tools.idea.testartifacts.instrumented.testsuite.adapter.GradleTestResultAdapter
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.utp.GradleAndroidProjectResolverExtension
import com.android.tools.utp.TaskOutputLineProcessor
import com.android.tools.utp.TaskOutputProcessor
import com.android.utils.usLocaleCapitalize
import com.google.common.base.Joiner
import com.intellij.build.BuildContentManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradlePath
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gradle task invoker to run ConnectedAndroidTask for selected devices at once.
 *
 * @param selectedDevices number of total selected devices to run instrumentation tests
 */
class GradleConnectedAndroidTestInvoker(
  private val selectedDevices: Int,
  private val executionEnvironment: ExecutionEnvironment,
  private val moduleData: ModuleData,
  private val uninstallIncompatibleApks: Boolean = false,
  private val backgroundTaskExecutor: (Runnable) -> Future<*> = ApplicationManager.getApplication()::executeOnPooledThread,
  private val gradleTaskManagerFactory: () -> GradleTaskManager = { GradleTaskManager() },
  private val gradleTestResultAdapterFactory: (IDevice, String, IdeAndroidArtifact?, AndroidTestResultListener) -> GradleTestResultAdapter
  = { iDevice, testSuiteDisplayName, artifact, listener ->
    GradleTestResultAdapter(iDevice, testSuiteDisplayName, artifact, listener)
  },
  private val buildToolWindowProvider: (Project) -> ToolWindow = { BuildContentManager.getInstance(it).orCreateToolWindow },
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
               consolePrinter: ConsoleView,
               androidModuleModel: GradleAndroidModel,
               waitForDebugger: Boolean,
               testPackageName: String,
               testClassName: String,
               testMethodName: String,
               testRegex: String,
               device: IDevice,
               retentionConfiguration: RetentionConfiguration,
               extraInstrumentationOptions: String) {
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
                    testRegex,
                    retentionConfiguration,
                    extraInstrumentationOptions)
    }
  }

  /**
   * Runs connectedAndroidTest Gradle task asynchronously.
   */
  private fun runGradleTask(
    project: Project,
    taskId: String,
    processHandler: ProcessHandler,
    consolePrinter: ConsoleView,
    androidModuleModel: GradleAndroidModel,
    waitForDebugger: Boolean,
    testPackageName: String,
    testClassName: String,
    testMethodName: String,
    testRegex: String,
    retentionConfiguration: RetentionConfiguration,
    extraInstrumentationOptions: String
  ) {
    consolePrinter.println("Running tests")

    val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)
    val adapters = scheduledDeviceList.associate {
      val adapter = gradleTestResultAdapterFactory(it, taskId, androidModuleModel.getArtifactForAndroidTest(), androidTestResultListener)
      adapter.device.id to adapter
    }

    val path: File = Projects.getBaseDirPath(project)
    val taskNames: List<String> = getTaskNames(androidModuleModel)
    val externalTaskId: ExternalSystemTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID,
                                                                           ExternalSystemTaskType.EXECUTE_TASK, project)
    val taskOutputProcessor = TaskOutputProcessor(adapters)
    val listener: ExternalSystemTaskNotificationListenerAdapter = object : ExternalSystemTaskNotificationListenerAdapter() {
      val outputLineProcessor = TaskOutputLineProcessor(object: TaskOutputLineProcessor.LineProcessor {
        override fun processLine(line: String) {
          val processedText = taskOutputProcessor.process(line)
          if (!(processedText.isBlank() && line != processedText)) {
            consolePrinter.println(processedText)
          }
        }
      })

      val testRunIsCancelled = AtomicBoolean(false)
      val onEndIsCalled = AtomicBoolean(false)

      override fun onCancel(id: ExternalSystemTaskId) {
        super.onCancel(id)
        testRunIsCancelled.set(true)
      }

      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        super.onTaskOutput(id, text, stdOut)
        if (stdOut) {
          outputLineProcessor.append(text)
        } else {
          processHandler.notifyTextAvailable(text, ProcessOutputTypes.STDERR)
        }
      }

      override fun onEnd(id: ExternalSystemTaskId) {
        if (onEndIsCalled.getAndSet(true)) {
          return
        }

        super.onEnd(id)

        val testSuiteStartedOnAnyDevice = adapters.values.any(GradleTestResultAdapter::testSuiteStarted)

        outputLineProcessor.close()
        adapters.values.forEach(GradleTestResultAdapter::onGradleTaskFinished)

        // If there is an APK installation error due to incompatible APKs installed on device,
        // display a popup and ask a user to rerun the Gradle task with UNINSTALL_INCOMPATIBLE_APKS
        // option.
        var isRerunRequested = false
        val rerunDevices = adapters.values.filter {
          it.needRerunWithUninstallIncompatibleApkOption().needRerunWithUninstallIncompatibleApkOption
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
            moduleData,
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
              testRegex,
              it.iDevice,
              retentionConfiguration,
              extraInstrumentationOptions
            )
          }
        } else {
          // If Gradle task run finished before the test suite starts, show error
          // in the Build output tool window.
          if(!testSuiteStartedOnAnyDevice && !testRunIsCancelled.get()) {
            ApplicationManager.getApplication().invokeLater({
              val toolWindow = buildToolWindowProvider(project)
              if (toolWindow.isAvailable && !toolWindow.isVisible) {
                toolWindow.show()
              }
            }, ModalityState.NON_MODAL, project.disposed)
          }

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
      project, waitForDebugger, testPackageName, testClassName, testMethodName, testRegex,
      retentionConfiguration, extraInstrumentationOptions)

    backgroundTaskExecutor {
      try {
        gradleTaskManagerFactory().executeTasks(
          externalTaskId,
          taskNames,
          path.path,
          gradleExecutionSettings,
          null,
          listener)
      } catch (e: ExternalSystemException) {
        // No-op.
        // If there is a failing test case, the test task finished in failed state
        // that ends up with ExternalSystemException to be thrown on Windows.
        // On Linux and Mac OS, GradleTaskManager doesn't throw ExternalSystemException
        // for failed task and it calls listener.onFailure() callback instead.
      } finally {
        // When a Gradle task fails, GradleTaskManager.executeTasks method may throw
        // an ExternalSystemException without calling onEnd() or onFailure() callback.
        // This often happens on Windows.
        listener.onEnd(externalTaskId)
      }
    }
  }

  private fun getGradleExecutionSettings(
    project: Project,
    waitForDebugger: Boolean,
    testPackageName: String,
    testClassName: String,
    testMethodName: String,
    testRegex: String,
    retentionConfiguration: RetentionConfiguration,
    extraInstrumentationOptions: String
  ): GradleExecutionSettings {
    return GradleUtil.getOrCreateGradleExecutionSettings(project).apply {
      // Add an environmental variable to filter connected devices for selected devices.
      val deviceSerials = scheduledDeviceList.joinToString(",") { device ->
        device.serialNumber
      }
      withEnvironmentVariables(mapOf(("ANDROID_SERIAL" to deviceSerials)))

      withArguments(getDeviceSpecificArguments())

      // Enable UTP test results reporting by embedded XML tag in stdout.
      withArgument("-P${GradleAndroidProjectResolverExtension.ENABLE_UTP_TEST_REPORT_PROPERTY}=true")

      if (retentionConfiguration.enabled == EnableRetention.YES) {
        withArgument("-P$RETENTION_ENABLE_PROPERTY=${retentionConfiguration.maxSnapshots}")
        withArgument("-P$RETENTION_COMPRESS_SNAPSHOT_PROPERTY=${retentionConfiguration.compressSnapshots}")
      } else if (retentionConfiguration.enabled == EnableRetention.NO) {
        withArgument("-P$RETENTION_ENABLE_PROPERTY=0")
      }

      // Add a test filter.
      if (testRegex.isNotBlank()) {
        withArgument("-Pandroid.testInstrumentationRunnerArguments.tests_regex=$testRegex")
      } else if (testPackageName != "" || testClassName != "") {
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
        withArgument("-P$UNINSTALL_INCOMPATIBLE_APKS_PROPERTY=true")
      }

      // Extra instrumentation params are stored as a String with the format "-e name1 value1 -e name2 value2...". To use these arguments
      // in a Gradle test, each argument needs to be in the format "-Pandroid.testInstrumentationRunnerArguments.name=value".
      val extraInstrumentationOptionsList = parseFromString(extraInstrumentationOptions).toList()
      for (param in extraInstrumentationOptionsList) {
        withArgument("-Pandroid.testInstrumentationRunnerArguments.${param.NAME}=${param.VALUE.trim()}")
      }

      // Don't switch focus to build tool window even after build failure because
      // if there is a test failure, AGP handles it as a build failure and it hides
      // the test result panel if this option is not set.
      // TODO(b/233356642): Replace with direct GradleBuildInvoker usage.
      @Suppress("DEPRECATION")
      putUserData(ANDROID_GRADLE_TASK_MANAGER_DO_NOT_SHOW_BUILD_OUTPUT_ON_FAILURE, true)
    }
  }

  private fun getTaskNames(androidModuleModel: GradleAndroidModel): List<String> {
    return listOf(
      "${moduleData.getGradlePath().trimEnd(':')}:connected${androidModuleModel.selectedVariantName.usLocaleCapitalize()}AndroidTest"
    )
  }

  // TODO: This method is copied from com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider#getDeviceSpecificArguments.
  //       which lives in a different module and this class doesn't have access. Factor out the common method into
  //       a shared utility class and them it from both places.
  private fun getDeviceSpecificArguments(): List<String> {
    val deviceFutures = executionEnvironment.getCopyableUserData(DeviceFutures.KEY)?.devices ?: emptyList()
    val deviceSpec = createSpec(deviceFutures) ?: return emptyList()

    val deviceSpecificArguments = mutableListOf<String>()
    deviceSpec.commonVersion?.let { version ->
      val deviceApiOptimization = API_OPTIMIZATION_ENABLE.get() && GradleExperimentalSettings.getInstance().ENABLE_GRADLE_API_OPTIMIZATION
      if (deviceApiOptimization) {
        deviceSpecificArguments.add(createProjectProperty(PROPERTY_BUILD_API, version.apiLevel.toString()))
        version.codename?.let { codename ->
          deviceSpecificArguments.add(createProjectProperty(PROPERTY_BUILD_API_CODENAME, codename))
        }
      }
    }
    if (deviceSpec.abis.isNotEmpty()) {
      deviceSpecificArguments.add(createProjectProperty(PROPERTY_BUILD_ABI, Joiner.on(',').join(deviceSpec.abis)))
    }

    return deviceSpecificArguments
  }
}
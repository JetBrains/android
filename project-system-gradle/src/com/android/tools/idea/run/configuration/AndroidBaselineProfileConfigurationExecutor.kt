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

import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.getProcessHandlersForDevices
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.track
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.run.configuration.execution.createRunContentDescriptor
import com.android.tools.idea.run.configuration.execution.getApplicationIdAndDevices
import com.android.tools.idea.run.configuration.execution.println
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidBaselineProfileConfigurationExecutor(
  val env: ExecutionEnvironment,
  private val deviceFutures: DeviceFutures
) : AndroidConfigurationExecutor {
  override val configuration = env.runProfile as AndroidBaselineProfileRunConfiguration

  private val LOG = Logger.getInstance(this::class.java)
  private val NOTIFICATION_GROUP_ID = "Baseline Profile"
  private val notificationGroup =
    NotificationGroup.findRegisteredGroup(NOTIFICATION_GROUP_ID)
      ?: NotificationGroup(
        NOTIFICATION_GROUP_ID,
        NotificationDisplayType.BALLOON,
        true,
        "Baseline Profile",
        null
      )
  private val project = env.project
  private val stats = RunStats.from(env)
  private val applicationIdProvider =
    configuration.applicationIdProvider
      ?: throw RuntimeException("Can't get ApplicationIdProvider for AndroidTestRunConfiguration")

  override fun run(indicator: ProgressIndicator): RunContentDescriptor = runBlockingCancellable {
    LOG.info("Generate Baseline Profile(s)")

    val (applicationId, devices) = getApplicationIdAndDevices(env, deviceFutures, applicationIdProvider, indicator)

    env.runnerAndConfigurationSettings?.getProcessHandlersForDevices(project, devices)?.forEach { it.destroyProcess() }

    stats.setPackage(applicationId)
    try {
      RunStats.from(env).track("BASELINE_PROFILE_LAUNCH_TASK_CREATE") {
        runGradleTask(indicator, devices)
      }
    } finally {
      devices.forEach {
        // Notify listeners of the deployment.
        project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).launchingTest(it.serialNumber, project)
      }
    }
  }

  private suspend fun runGradleTask(indicator: ProgressIndicator, devices: List<IDevice>): RunContentDescriptor {
    val console = createConsole()
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
    console.println("$date: Launching ${configuration.name} on '${env.executionTarget.displayName}.")
    indicator.text = "Start baseline profile gradle task"

    val externalTaskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)

    val handler = object : ProcessHandler() {
      override fun destroyProcessImpl() {
        notifyProcessTerminated(if (GradleBuildInvoker.getInstance(project).stopBuild(externalTaskId)) 0 else -1)
      }

      override fun detachProcessImpl() {
        notifyProcessDetached()
      }

      override fun detachIsDefault(): Boolean = false
      override fun getProcessInput(): OutputStream? = null
    }

    val listener = object: ExternalSystemTaskNotificationListenerAdapter() {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        console.print(text, if (stdOut) ConsoleViewContentType.NORMAL_OUTPUT else ConsoleViewContentType.ERROR_OUTPUT)
      }

      override fun onSuccess(id: ExternalSystemTaskId) {
        val notification = notificationGroup
          .createNotification("Baseline profile for the project \"${project.name}\" has been generated.", NotificationType.INFORMATION)
          .setTitle("Baseline profile added")
          .setImportant(true)
        notification.addAction(object : AnAction("Open Run tool window") {
          override fun actionPerformed(e: AnActionEvent) {
            RunContentManager.getInstance(project).toFrontRunContent(DefaultRunExecutor.getRunExecutorInstance(), handler)
            notification.expire()
          }
        })
        Notifications.Bus.notify(notification, project)
      }

      override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        val notification = notificationGroup
          .createNotification("Baseline profile generation for the project \"${project.name}\" has failed.", NotificationType.ERROR)
          .setTitle("Baseline profile error")
          .setImportant(true)
        Notifications.Bus.notify(notification, project)
      }

      override fun onEnd(id: ExternalSystemTaskId) {
        if (id == externalTaskId) {
          RunContentManager.getInstance(project).allDescriptors.find {
            it.executionId == env.executionId
          }?.let {
            it.processHandler?.detachProcess()
          }
        }
      }
    }

    GradleBuildInvoker.Request(
      project,
      externalTaskId,
      GradleBuildInvoker.Request.RequestData(
        BuildMode.DEFAULT_BUILD_MODE,
        File(configuration.getPath()!!), // TODO(b/306255267) : Use GradleTaskFinder instead of hardcoding task name
        configuration.getTaskNames(),
        executionSettings = getGradleExecutionSettings(project, devices)),
      isWaitForCompletion = false,
      doNotShowBuildOutputOnFailure = false,
      listener = listener)
      .let {
        GradleBuildInvoker.getInstance(project).executeTasks(it)
      }.let {
        it.addListener({
                         if (it.isDone) {
                           handler.detachProcess()
                         }
                       }, AppExecutorUtil.getAppExecutorService())
      }

    return createRunContentDescriptor(handler, console, env)
  }

  private fun getGradleExecutionSettings(
    project: Project,
    devices: List<IDevice>,
  ): GradleExecutionSettings {
    val initScriptFile = File.createTempFile("initScript", ".gradle.kts", Path.of(FileUtil.getTempDirectory()).toFile()).apply {
      deleteOnExit()
    }
    initScriptFile.writeText("""
      afterProject {
        pluginManager.withPlugin("androidx.baselineprofile") {
          val baselineExt = extensions.findByName("baselineProfile") ?: return@withPlugin
          val extClass: Class<*> = try {
            baselineExt.javaClass.classLoader.loadClass(
                "androidx.baselineprofile.gradle.producer.BaselineProfileProducerExtension"
            )
          } catch (_: Exception) { null } ?: return@withPlugin

          val baselineExtWithType = extensions.findByType(extClass) ?: return@withPlugin

          val setterMethod = try {
            extClass.getMethod("setUseConnectedDevices", Boolean::class.java)
          } catch (_: Exception) { null } ?: return@withPlugin

          try {
            // Enable useConnectedDevices flag.
            setterMethod(baselineExtWithType, true)
          } catch (_: Exception) { }

          // Clear managed devices.
          val getterMethodForManagedDevices = try {
            extClass.getMethod("getManagedDevices")
          } catch (_: Exception) { null } ?: return@withPlugin

          val managedDevices = try {
            getterMethodForManagedDevices(baselineExtWithType) as? MutableList<*>
          } catch (_: Exception) { null } ?: return@withPlugin
          managedDevices.clear()
        }
      }
    """.trimIndent())
    return GradleProjectSystemUtil.getOrCreateGradleExecutionSettings(project).apply {
      // Add an environmental variable to filter connected devices for selected devices.
      val deviceSerials = devices.joinToString(",") { device -> device.serialNumber }
      withEnvironmentVariables(mapOf(("ANDROID_SERIAL" to deviceSerials)))
      configuration.getFilterArgument()?.let { withArgument(it) }
      withArgument("--init-script=${initScriptFile.absolutePath}")
    }
  }

  override fun debug(indicator: ProgressIndicator): RunContentDescriptor {
    throw RuntimeException("Unsupported operation")
  }

  private fun createConsole(): ConsoleView {
    val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
    Disposer.register(project, console)
    return console
  }
}

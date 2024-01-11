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
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.getProcessHandlersForDevices
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.track
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
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
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.file.Path

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
    console.println("$date: Launching ${configuration.name} on '${env.executionTarget.displayName}'.")
    indicator.text = "Start baseline profile gradle task"

    val gradleInitScriptFile = getGradleInitScriptFile()
    val deviceSerials = devices.joinToString(",") { device -> device.serialNumber }
    val envVar = mapOf(("ANDROID_SERIAL" to deviceSerials))
    val argList = mutableListOf<String>().apply {
      add("--init-script=${gradleInitScriptFile.absolutePath}")
      configuration.getFilterArgument()?.let { add(it) }
    }

    val taskId = ExternalSystemTaskId.create(GradleProjectSystemUtil.GRADLE_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)

    val handler = object : ProcessHandler() {
      override fun destroyProcessImpl() {
        notifyProcessTerminated(if (GradleBuildInvoker.getInstance(project).stopBuild(taskId)) 0 else -1)
      }

      override fun detachProcessImpl() {
        notifyProcessDetached()
      }

      override fun detachIsDefault(): Boolean = false
      override fun getProcessInput(): OutputStream? = null
    }

    GradleBuildInvoker.getInstance(project).generateBaselineProfileSources(taskId, configuration.modules, envVar, argList, configuration.generateAllVariants).apply {
      addListener({
        if (this.get().isBuildSuccessful) {
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
        } else if (!this.get().isBuildCancelled)  {
          val notification = notificationGroup
            .createNotification("Baseline profile generation for the project \"${project.name}\" has failed.", NotificationType.ERROR)
            .setTitle("Baseline profile error")
            .setImportant(true)
          Notifications.Bus.notify(notification, project)
        }

        // This listener only gets called after the build finishes, so it should be safe to detach here.
        handler.detachProcess()
      }, AppExecutorUtil.getAppExecutorService())
    }

    AndroidSessionInfo.create(handler, devices, "Baseline Profile Task: ${taskId.id}")
    return createRunContentDescriptor(handler, console, env)
  }

  private fun getGradleInitScriptFile(): File {
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
    return initScriptFile
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

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
package com.android.tools.idea.editors.literals.internal

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val PROBLEM_NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Live Literal Problem Notification")

private fun LiveLiteralsMonitorHandler.Problem.Severity.toNotificationSeverity() = when(this) {
  LiveLiteralsMonitorHandler.Problem.Severity.INFO -> NotificationType.INFORMATION
  LiveLiteralsMonitorHandler.Problem.Severity.ERROR -> NotificationType.ERROR
  LiveLiteralsMonitorHandler.Problem.Severity.WARNING -> NotificationType.WARNING
}

@VisibleForTesting
@Service
class LiveLiteralsDeploymentReportService private constructor(private val project: Project,
                                                              listenerExecutor: Executor?) : LiveLiteralsMonitorHandler, ModificationTracker, Disposable {
  private val listenerExecutor = listenerExecutor ?: AppExecutorUtil.createBoundedApplicationPoolExecutor("Listener executor",
                                                                                                          AppExecutorUtil.getAppExecutorService(),
                                                                                                          1, this)

  constructor(project: Project) : this(project, null)

  /**
   * Interface for listeners of literals deployment notifications.
   */
  interface Listener {
    /**
     * Called when literals start being monitored.
     */
    fun onMonitorStarted(deviceId: String)

    /**
     * Called when literals stopped being monitored.
     */
    fun onMonitorStopped(deviceId: String)

    /**
     * Called when literals are done being deployed.
     */
    fun onLiveLiteralsPushed(deviceId: String)
  }

  private val log = Logger.getInstance(LiveLiteralsDeploymentReportService::class.java)

  private val modificationTracker = SimpleModificationTracker()
  private val serviceLock = ReentrantReadWriteLock()

  /** Map containing when a given device id is currently deploying literals. */
  @GuardedBy("serviceLock")
  private val activeDevices = mutableMapOf<String, LiveLiteralsMonitorHandler.DeviceType>()

  /** Map containing deployment problems recorded for a given device id. */
  @GuardedBy("serviceLock")
  private val problemsMap = mutableMapOf<String, Collection<LiveLiteralsMonitorHandler.Problem>>()

  /** Map containing deployment problems recorded for a given device id. */
  @GuardedBy("serviceLock")
  private val pushedStarted = mutableMapOf<String, Long>()

  override fun getModificationCount(): Long = modificationTracker.modificationCount

  /**
   * Returns if there are any recorded problems with any deployment.
   */
  val hasProblems: Boolean
    get() {
      serviceLock.read {
        return problemsMap.any { it.value.isNotEmpty() }
      }
    }

  /**
   * Returns if any devices are currently with monitoring on.
   */
  val hasActiveDevices: Boolean
    get() {
      serviceLock.read {
        return activeDevices.isNotEmpty()
      }
    }

  /**
   * Returns true if any of the device types are of the given [deviceType].
   */
  fun hasActiveDeviceOfType(vararg deviceType: LiveLiteralsMonitorHandler.DeviceType): Boolean =
    serviceLock.read { deviceType.any { activeDevices.containsValue(it) } }

  val problems: Collection<Pair<String, LiveLiteralsMonitorHandler.Problem>>
    get() {
      serviceLock.read {
        return problemsMap.entries
          .flatMap { entry -> entry.value.map { entry.key to it } }
      }
    }

  /**
   * Call this method when the deployment for [deviceId] has started. This will clear all current registered
   * Problems for that device.
   */
  override fun liveLiteralsMonitorStarted(deviceId: String, deviceType: LiveLiteralsMonitorHandler.DeviceType) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveLiterals) return

    var started: Boolean
    serviceLock.write {
      started = activeDevices.putIfAbsent(deviceId, deviceType) == null
      if (started) {
        log.debug { "Monitor started for '$deviceId' deviceCount=${activeDevices.size}"}
        problemsMap.remove(deviceId)
        pushedStarted[deviceId] = System.currentTimeMillis()
      }
    }

    if (started) {
      modificationTracker.incModificationCount()
      listenerExecutor.execute {
        project.messageBus.syncPublisher(LITERALS_DEPLOYED_TOPIC).onMonitorStarted(deviceId)
      }
    }
    LiveLiteralsDiagnosticsManager.getWriteInstance(project).liveLiteralsMonitorStarted(deviceId, deviceType)
  }

  /**
   * Call this method when the monitoring for [deviceId] has stopped. For example, if the application has stopped.
   */
  override fun liveLiteralsMonitorStopped(deviceId: String) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveLiterals) return

    var stopped: Boolean
    modificationTracker.incModificationCount()
    serviceLock.write {
      stopped = activeDevices.remove(deviceId) != null
      problemsMap.remove(deviceId)

      if (stopped) {
        log.debug { "Monitor stopped for '$deviceId' deviceCount=${activeDevices.size}"}
      }
    }

    if (stopped) {
      listenerExecutor.execute {
        project.messageBus.syncPublisher(LITERALS_DEPLOYED_TOPIC).onMonitorStopped(deviceId)
      }
    }
    LiveLiteralsDiagnosticsManager.getWriteInstance(project).liveLiteralsMonitorStopped(deviceId)
  }

  override fun liveLiteralPushStarted(deviceId: String, pushId: String) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveLiterals) return
    log.debug("Device '$deviceId' pushed started.")
    LiveLiteralsDiagnosticsManager.getWriteInstance(project).liveLiteralPushStarted(deviceId, pushId)
  }

  /**
   * Call this method when the deployment for [deviceId] has finished. [problems] includes a list
   * of the problems found while deploying literals.
   */
  override fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<LiveLiteralsMonitorHandler.Problem>) {
    if (!LiveEditApplicationConfiguration.getInstance().isLiveLiterals) return

    var isActive: Boolean
    serviceLock.write {
      isActive = activeDevices.contains(deviceId)
      if (isActive) {
        problemsMap[deviceId] = problems.toList()
      }
      else {
        log.warn("Device $deviceId is not active, liveLiteralPushed ignored.")
      }
    }

    if (isActive) {
      LiveLiteralsDiagnosticsManager.getWriteInstance(project).liveLiteralPushed(deviceId, pushId, problems)

      // Log all the problems to the event log
      problems.forEach {
        PROBLEM_NOTIFICATION_GROUP.createNotification("[${it.severity}] ${it.content}", it.severity.toNotificationSeverity())
          .notify(project)
      }
      listenerExecutor.execute {
        project.messageBus.syncPublisher(LITERALS_DEPLOYED_TOPIC).onLiveLiteralsPushed(deviceId)
      }
    }
  }

  fun stopAllMonitors() {
    serviceLock.read { activeDevices.keys.toMutableList() }.forEach { liveLiteralsMonitorStopped(it) }
  }

  /**
   * Subscribe to notification of literals deployment.
   */
  fun subscribe(parentDisposable: Disposable, listener: Listener) =
    project.messageBus.connect(parentDisposable).subscribe(LITERALS_DEPLOYED_TOPIC, listener)

  companion object {
    @Topic.ProjectLevel
    private val LITERALS_DEPLOYED_TOPIC = Topic("Live Literals Deployed", Listener::class.java)

    fun getInstance(project: Project): LiveLiteralsDeploymentReportService =
      project.getService(LiveLiteralsDeploymentReportService::class.java)

    @TestOnly
    fun getInstanceForTesting(project: Project, listenerExecutor: Executor): LiveLiteralsDeploymentReportService =
      LiveLiteralsDeploymentReportService(project, listenerExecutor)
  }

  override fun dispose() {
    serviceLock.write {
      problemsMap.clear()
    }
  }
}
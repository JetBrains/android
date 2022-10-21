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

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.analytics.Percentiles
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.editors.literals.LiveLiteralsMonitorHandler
import com.google.common.cache.CacheBuilder
import com.google.common.collect.EvictingQueue
import com.google.common.math.Quantiles
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.LiveLiteralsEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.rd.util.getOrCreate
import org.jetbrains.annotations.TestOnly
import java.util.EnumMap
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Default time that will be used to report events to the Studio stats server.
 */
private const val DEFAULT_REPORT_TIME_INTERVAL = 30L
private val DEFAULT_REPORT_TIME_INTERVAL_UNIT = TimeUnit.SECONDS

@WorkerThread
private fun reportToServer(event: LiveLiteralsEvent) {
  val studioEvent = AndroidStudioEvent.newBuilder()
    .setCategory(AndroidStudioEvent.EventCategory.DEPLOYMENT)
    .setKind(AndroidStudioEvent.EventKind.LIVE_LITERALS_EVENT)
    .setLiveLiteralsEvent(event)
  UsageTracker.log(studioEvent)
}

fun LiveLiteralsMonitorHandler.DeviceType.protoType(): LiveLiteralsEvent.LiveLiteralsDeviceType = when(this) {
  LiveLiteralsMonitorHandler.DeviceType.EMULATOR -> LiveLiteralsEvent.LiveLiteralsDeviceType.EMULATOR
  LiveLiteralsMonitorHandler.DeviceType.PHYSICAL -> LiveLiteralsEvent.LiveLiteralsDeviceType.PHYSICAL
  LiveLiteralsMonitorHandler.DeviceType.PREVIEW -> LiveLiteralsEvent.LiveLiteralsDeviceType.PREVIEW
  LiveLiteralsMonitorHandler.DeviceType.UNKNOWN -> LiveLiteralsEvent.LiveLiteralsDeviceType.UNKNOWN_DEVICE_TYPE
}

/**
 * Record of the deployment of a literal containing the time it took and the number of problems if any.
 * @param deviceId device id to which the literal was pushed to.
 * @param pushId unique push id for a given device to track the literal deployment.
 * @param timeMs time in milliseconds that took to deploy the literal.
 * @param problemCount number of problems found while deploying or 0 if it was successful.
 */
data class DeployRecord(val deviceId: String, val pushId: String, val timeMs: Long, val problemCount: Int)

/**
 * Stats for a subset of the [DeployRecord]s.
 */
class DeployRecordStats internal constructor(val records: Collection<DeployRecord>) {
  /**
   * Returns the [percentile] percentile for the deployment time.
   */
  @Suppress("UnstableApiUsage")
  fun deployTime(percentile: Int): Long = if (records.isNotEmpty())
    Quantiles.percentiles().index(percentile).compute(lastDeploymentTimesMs()).toLong()
  else
    -1

  /**
   * Returns a number of the last deployment times for this subset.
   */
  fun lastDeploymentTimesMs(): List<Long> = records.map { it.timeMs }
}

/**
 * Interface for reading Live Literals diagnostics.
 */
interface LiveLiteralsDiagnosticsRead {
  /**
   * Total count of literal deployments [successfulDeploymentCount] + [failedDeploymentCount].
   */
  fun deploymentCount(): Long = successfulDeploymentCount() + failedDeploymentCount()

  /**
   * Number of successfully deployed literals.
   */
  fun successfulDeploymentCount(): Long

  /**
   * Number of successfully failed literals.
   */
  fun failedDeploymentCount(): Long

  /**
   * A set of the last executed deployments.
   */
  fun lastDeployments(): List<DeployRecord>

  /**
   * Returns the list of devices ids that recently deployed literals.
   */
  fun lastRecordedDevices(): Collection<String> = lastDeployments().map { it.deviceId }.distinct()

  /**
   * Returns [DeployRecordStats] for recent deployed literals.
   */
  fun lastDeploymentStats(): DeployRecordStats = DeployRecordStats(lastDeployments())

  /**
   * Returns [DeployRecordStats] for recent deployed literals for a specific device.
   */
  fun lastDeploymentStatsForDevice(deviceId: String): DeployRecordStats = DeployRecordStats(
    lastDeployments().filter { it.deviceId == deviceId })
}

/**
 * Interface for clients providing stats about Live Literals.
 */
interface LiveLiteralsDiagnosticsWrite : LiveLiteralsMonitorHandler

/**
 * Interface for metrics that are not tied to the project.
 */
interface LiveLiteralsApplicationDiagnosticsWrite {
  /**
   * The user has manually changed the state of Live Literals.
   */
  fun userChangedLiveLiteralsState(enabled: Boolean)
}

private class RemoteStatsCollector(msProvider: () -> Long) {
  companion object {
    private val ESTIMATION_TARGETS = doubleArrayOf(0.5, 0.9, 0.99)
    private const val NUM_RAW_SAMPLES = 80
  }

  private val created = msProvider()
  val deployTimeMsPercentiles = Percentiles(ESTIMATION_TARGETS, NUM_RAW_SAMPLES)
  val problemsPercentiles = Percentiles(ESTIMATION_TARGETS, NUM_RAW_SAMPLES)
  var successfulDeployments = 0
  var failedDeployments = 0
  var activeDevices = 0

  fun isOlderThan(time: Long, unit: TimeUnit, msProvider: () -> Long) = (msProvider() - created) > unit.toMillis(time)

  fun buildProto(deviceType: LiveLiteralsMonitorHandler.DeviceType): LiveLiteralsEvent.LiveLiteralsDeployStats = LiveLiteralsEvent.LiveLiteralsDeployStats.newBuilder()
    .setDeviceType(deviceType.protoType())
    .setDeploymentTimeMs(deployTimeMsPercentiles.export())
    .setNumberOfProblems(problemsPercentiles.export())
    .setSuccessfulDeployments(successfulDeployments)
    .setFailedDeployments(failedDeployments)
    .setDevicesCount(activeDevices)
    .build()
}

private fun pushKey(deviceId: String, pushId: String) = "$deviceId:$pushId"

private class LiveLiteralsDiagnosticsRemoteReporterImpl(private val msProvider: () -> Long,
                                                        private val onReport: (LiveLiteralsEvent) -> Unit,
                                                        reportScheduler: ScheduledExecutorService,
                                                        reportInterval: Long,
                                                        reportIntervalUnit: TimeUnit) : LiveLiteralsDiagnosticsWrite, Disposable {
  private val deploymentStartTimes = CacheBuilder.newBuilder()
    .expireAfterAccess(10, TimeUnit.SECONDS)
    .build<String, Long>()
  private val activeDevices = mutableMapOf<String, LiveLiteralsMonitorHandler.DeviceType>()
  private var currentStatsCollector = EnumMap<LiveLiteralsMonitorHandler.DeviceType, RemoteStatsCollector>(
    LiveLiteralsMonitorHandler.DeviceType::class.java)
  private var scheduledReport: Future<*> = reportScheduler.scheduleWithFixedDelay(::reportToServer,
                                                                                  reportInterval,
                                                                                  reportInterval,
                                                                                  reportIntervalUnit)

  @Synchronized
  override fun liveLiteralsMonitorStarted(deviceId: String, deviceType: LiveLiteralsMonitorHandler.DeviceType) {
    activeDevices[deviceId] = deviceType
    val numberOfDevices = activeDevices.count { it.value == deviceType }
    val stats = currentStatsCollector.getOrCreate(deviceType) { RemoteStatsCollector(msProvider) }
    stats?.activeDevices = numberOfDevices.coerceAtLeast(stats.activeDevices)
    onReport(LiveLiteralsEvent.newBuilder()
               .setEventType(LiveLiteralsEvent.LiveLiteralsEventType.START)
               .setDeviceType(deviceType.protoType())
               .build())
  }

  @Synchronized
  override fun liveLiteralsMonitorStopped(deviceId: String) {
    val deviceType = activeDevices.remove(deviceId) ?: return
    onReport(LiveLiteralsEvent.newBuilder()
               .setEventType(LiveLiteralsEvent.LiveLiteralsEventType.STOP)
               .setDeviceType(deviceType.protoType())
               .build())
  }

  @Synchronized
  override fun liveLiteralPushStarted(deviceId: String, pushId: String) {
    deploymentStartTimes.put(pushKey(deviceId, pushId), msProvider())
  }

  @Synchronized
  fun reportToServer() {
    currentStatsCollector.forEach { (typeToReport, statsToReport) ->
      val statsProto = statsToReport.buildProto(typeToReport)

      onReport(LiveLiteralsEvent.newBuilder()
                 .setEventType(LiveLiteralsEvent.LiveLiteralsEventType.DEPLOY_STATS)
                 .setDeviceType(typeToReport.protoType())
                 .addAllDeployStats(listOf(statsProto))
                 .build())
    }
    currentStatsCollector.clear()
  }

  @Synchronized
  override fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<LiveLiteralsMonitorHandler.Problem>) {
    val key = pushKey(deviceId, pushId)
    val timeMs = deploymentStartTimes.getIfPresent(key)?.let {
      msProvider() - it
    }
    val deviceType = activeDevices[deviceId] ?: LiveLiteralsMonitorHandler.DeviceType.UNKNOWN
    val stats = currentStatsCollector.getOrCreate(deviceType) { RemoteStatsCollector(msProvider) }

    if (problems.isEmpty()) {
      stats.successfulDeployments++
    }
    else {
      stats.failedDeployments++
    }

    timeMs?.let { stats.deployTimeMsPercentiles.addSample(it.toDouble()) }
    stats.problemsPercentiles.addSample(problems.size.toDouble())
  }

  @Synchronized
  override fun dispose() {
    scheduledReport.cancel(true)
  }
}

@Suppress("UnstableApiUsage")
private class LiveLiteralsDiagnosticsLocalImpl(private val msProvider: () -> Long)
  : LiveLiteralsDiagnosticsRead, LiveLiteralsDiagnosticsWrite {
  private val deploymentStartTimes = CacheBuilder.newBuilder()
    .expireAfterAccess(10, TimeUnit.SECONDS)
    .build<String, Long>()

  private val lastDeployments = EvictingQueue.create<DeployRecord>(100)
  private var successfulDeployments = 0L
  private var failedDeployments = 0L

  @Synchronized
  override fun successfulDeploymentCount(): Long = successfulDeployments

  @Synchronized
  override fun failedDeploymentCount(): Long = failedDeployments

  @Synchronized
  override fun lastDeployments(): List<DeployRecord> = lastDeployments.toList()

  override fun liveLiteralsMonitorStarted(deviceId: String, deviceType: LiveLiteralsMonitorHandler.DeviceType) {
  }

  override fun liveLiteralsMonitorStopped(deviceId: String) {
  }

  @Synchronized
  override fun liveLiteralPushStarted(deviceId: String, pushId: String) {
    deploymentStartTimes.put(pushKey(deviceId, pushId), msProvider())
  }

  @Synchronized
  override fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<LiveLiteralsMonitorHandler.Problem>) {
    val key = pushKey(deviceId, pushId)
    val timeMs = deploymentStartTimes.getIfPresent(key)?.let {
      msProvider() - it
    } ?: return

    deploymentStartTimes.invalidate(key)

    if (problems.isEmpty())
      successfulDeployments++
    else
      failedDeployments++

    lastDeployments.add(DeployRecord(deviceId, pushId, timeMs, problems.size))
  }
}

private class LiveLiteralsDiagnosticsImpl(
  private val remoteReporterImpl: LiveLiteralsDiagnosticsRemoteReporterImpl,
  private val localReporterImpl: LiveLiteralsDiagnosticsLocalImpl?)
  : LiveLiteralsDiagnosticsRead, LiveLiteralsDiagnosticsWrite {
  override fun successfulDeploymentCount(): Long = localReporterImpl?.successfulDeploymentCount() ?: -1
  override fun failedDeploymentCount(): Long = localReporterImpl?.failedDeploymentCount() ?: -1
  override fun lastDeployments(): List<DeployRecord> = localReporterImpl?.lastDeployments() ?: emptyList()

  override fun liveLiteralsMonitorStarted(deviceId: String, deviceType: LiveLiteralsMonitorHandler.DeviceType) {
    remoteReporterImpl.liveLiteralsMonitorStarted(deviceId, deviceType)
    localReporterImpl?.liveLiteralsMonitorStarted(deviceId, deviceType)
  }

  override fun liveLiteralsMonitorStopped(deviceId: String) {
    remoteReporterImpl.liveLiteralsMonitorStopped(deviceId)
    localReporterImpl?.liveLiteralsMonitorStopped(deviceId)
  }

  override fun liveLiteralPushStarted(deviceId: String, pushId: String) {
    remoteReporterImpl.liveLiteralPushStarted(deviceId, pushId)
    localReporterImpl?.liveLiteralPushStarted(deviceId, pushId)
  }

  override fun liveLiteralPushed(deviceId: String, pushId: String, problems: Collection<LiveLiteralsMonitorHandler.Problem>) {
    remoteReporterImpl.liveLiteralPushed(deviceId, pushId, problems)
    localReporterImpl?.liveLiteralPushed(deviceId, pushId, problems)
  }

}

object LiveLiteralsDiagnosticsManager {
  private val KEY = Key.create<LiveLiteralsDiagnosticsImpl>("LiveLiteralsDiagnosticsManager")

  private fun getImpl(project: Project,
                      msProvider: () -> Long = { System.currentTimeMillis() },
                      onReport: (LiveLiteralsEvent) -> Unit = ::reportToServer,
                      reportScheduler: ScheduledExecutorService = AppExecutorUtil.getAppScheduledExecutorService(),
                      reportInterval: Long = DEFAULT_REPORT_TIME_INTERVAL,
                      reportIntervalUnit: TimeUnit = DEFAULT_REPORT_TIME_INTERVAL_UNIT): LiveLiteralsDiagnosticsImpl {
    val diagnostics = project.getUserData(KEY)
    if (diagnostics != null) return diagnostics

    val remoteReporter = LiveLiteralsDiagnosticsRemoteReporterImpl(msProvider, onReport, reportScheduler, reportInterval, reportIntervalUnit)
    val localReporter = if (ApplicationManager.getApplication().isInternal)
      LiveLiteralsDiagnosticsLocalImpl(msProvider)
    else
      null

    return LiveLiteralsDiagnosticsImpl(remoteReporter, localReporter).also {
      project.putUserData(KEY, it)
    }
  }

  @JvmStatic
  fun getReadInstance(project: Project): LiveLiteralsDiagnosticsRead = getImpl(project)

  @JvmStatic
  fun getWriteInstance(project: Project): LiveLiteralsDiagnosticsWrite = getImpl(project)

  private fun getApplicationWriteInstance(onReport: (LiveLiteralsEvent) -> Unit,
                                          executor: Executor): LiveLiteralsApplicationDiagnosticsWrite = object : LiveLiteralsApplicationDiagnosticsWrite {
    override fun userChangedLiveLiteralsState(enabled: Boolean) {
      executor.execute {
        onReport(LiveLiteralsEvent.newBuilder()
                   .setEventType(
                     if (enabled) LiveLiteralsEvent.LiveLiteralsEventType.USER_ENABLE else LiveLiteralsEvent.LiveLiteralsEventType.USER_DISABLE)
                   .build())
      }
    }
  }

  @JvmStatic
  fun getApplicationWriteInstance(): LiveLiteralsApplicationDiagnosticsWrite = getApplicationWriteInstance(::reportToServer,
                                                                                                           AppExecutorUtil.getAppExecutorService())

  @TestOnly
  @JvmStatic
  fun getWriteInstanceForTest(project: Project,
                              msProvider: () -> Long,
                              onReport: (LiveLiteralsEvent) -> Unit,
                              reportScheduler: ScheduledExecutorService,
                              reportInterval: Long,
                              reportIntervalUnit: TimeUnit): LiveLiteralsDiagnosticsWrite =
    getImpl(project, msProvider, onReport, reportScheduler, reportInterval, reportIntervalUnit)

  @TestOnly
  @JvmStatic
  fun getApplicationWriteInstanceForTest(onReport: (LiveLiteralsEvent) -> Unit,
                                         executor: Executor): LiveLiteralsApplicationDiagnosticsWrite =
    getApplicationWriteInstance(onReport, executor)
}
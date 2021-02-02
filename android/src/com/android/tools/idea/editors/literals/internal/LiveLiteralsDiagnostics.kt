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

import com.google.common.cache.CacheBuilder
import com.google.common.collect.EvictingQueue
import com.google.common.math.Quantiles
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

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
  fun lastDeploymentStatsForDevice(deviceId: String): DeployRecordStats = DeployRecordStats(lastDeployments().filter { it.deviceId == deviceId })
}

/**
 * Interface for clients providing stats about Live Literals.
 */
interface LiveLiteralsDiagnosticsWrite {
  /**
   * Record that a literal push has started.
   *
   * @param deviceId the device id of the device to which the literal was pushed to.
   * @param pushId a unique id to correlate this call with the [recordPushFinished].
   */
  fun recordPushStarted(deviceId: String, pushId: String)

  /**
   * Record that a literal push has completed.
   *
   * @param deviceId the device id of the device to which the literal was pushed to.
   * @param pushId a unique id to correlate this call with the [recordPushStarted].
   * @param problems number of problems found while deploying or 0 if it was successful.
   */
  fun recordPushFinished(deviceId: String, pushId: String, problems: Int)
}

/**
 * A NOP implementation of [LiveLiteralsDiagnosticsRead] and [LiveLiteralsDiagnosticsWrite].
 */
private object NopLiveLiteralsDiagnostics: LiveLiteralsDiagnosticsRead, LiveLiteralsDiagnosticsWrite {
  override fun successfulDeploymentCount(): Long = 0
  override fun failedDeploymentCount(): Long = 0
  override fun lastDeployments(): List<DeployRecord> = listOf()
  override fun recordPushStarted(deviceId: String, pushId: String) {}
  override fun recordPushFinished(deviceId: String, pushId: String, problems: Int) {}
}

@Suppress("UnstableApiUsage")
private class LiveLiteralsDiagnosticsImpl(private val msProvider: () -> Long): LiveLiteralsDiagnosticsRead, LiveLiteralsDiagnosticsWrite {
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

  @Synchronized
  override fun recordPushStarted(deviceId: String, pushId: String) {
    deploymentStartTimes.put(pushKey(deviceId, pushId), msProvider())
  }

  private fun pushKey(deviceId: String, pushId: String) = "$deviceId:$pushId"

  @Synchronized
  override fun recordPushFinished(deviceId: String, pushId: String, problems: Int) {
    val key = pushKey(deviceId, pushId)
    val timeMs = deploymentStartTimes.getIfPresent(key)?.let {
      msProvider() - it
    } ?: return

    deploymentStartTimes.invalidate(key)

    if (problems < 1)
      successfulDeployments++
    else
      failedDeployments++

    lastDeployments.add(DeployRecord(deviceId, pushId, timeMs, problems))
  }
}

object LiveLiteralsDiagnosticsManager {
  private val KEY = Key.create<LiveLiteralsDiagnosticsImpl>("LiveLiteralsDiagnosticsManager")

  private fun getImpl(project: Project, msProvider: () -> Long = { System.currentTimeMillis() }): LiveLiteralsDiagnosticsImpl {
    val diagnostics = project.getUserData(KEY)
    if (diagnostics != null) return diagnostics

    return LiveLiteralsDiagnosticsImpl(msProvider).also {
      project.putUserData(KEY, it)
    }
  }

  @JvmStatic
  fun getReadInstance(project: Project): LiveLiteralsDiagnosticsRead {
    if (!ApplicationManager.getApplication().isInternal) return NopLiveLiteralsDiagnostics

    return getImpl(project)
  }

  @JvmStatic
  fun getWriteInstance(project: Project): LiveLiteralsDiagnosticsWrite {
    if (!ApplicationManager.getApplication().isInternal) return NopLiveLiteralsDiagnostics

    return getImpl(project)
  }

  @TestOnly
  @JvmStatic
  fun getWriteInstanceForTest(project: Project, msProvider: () -> Long): LiveLiteralsDiagnosticsWrite {
    return getImpl(project, msProvider)
  }
}
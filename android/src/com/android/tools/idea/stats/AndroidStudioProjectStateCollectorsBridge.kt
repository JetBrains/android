/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.android.tools.idea.concurrency.createCoroutineScope
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.startup.ProjectActivity
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.android.AndroidPluginDisposable

/**
 * Bridges FUS state collectors to [AndroidStudioEventLogger].
 *
 * The platform's [com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger] resolves the FUS logger via
 * [com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider], which filters out
 * [AndroidStudioStatisticsEventLoggerProvider] due to the `isDevelopedExclusivelyByJetBrains` check. As a result, state collector metrics
 * never reach [AndroidStudioEventLogger].
 *
 * This activity collects metrics from the relevant [ProjectUsagesCollector]s and forwards them directly to [AndroidStudioEventLogger],
 * bypassing the provider resolution chain.
 */
@Suppress("UnstableApiUsage")
class AndroidStudioProjectStateCollectorsBridge : ProjectActivity {
  override suspend fun execute(project: Project) {
    AndroidPluginDisposable.getProjectInstance(project).createCoroutineScope().launch {
      project.waitForSmartMode()
      delay(INITIAL_DELAY)
      while (true) {
        collectAndForwardMetrics(project)
        delay(INTERVAL)
      }
    }
  }

  private suspend fun collectAndForwardMetrics(project: Project) {
    if (!StatisticsUploadAssistant.isCollectAllowed()) return

    val logger = AndroidStudioEventLogger.getInstance()
    for (bean in UsageCollectors.PROJECT_EP_NAME.extensionList) {
      val collector = bean.collector as? ProjectUsagesCollector ?: continue
      val group = collector.group ?: continue
      if (group.id !in FORWARDED_GROUPS) continue

      try {
        val metrics = collector.collect(project)
        if (project.isDisposed) return

        val projectData = FeatureUsageData(group.recorder).addProject(project)
        for (metric in metrics) {
          val merged = FUStateUsagesLogger.mergeWithEventData(projectData, metric.data)
          val eventData = merged?.build() ?: emptyMap()
          logger.logAsync(group, metric.eventId, eventData, true)
        }
      } catch (t: Throwable) {
        LOG.error("Failed to collect metrics for '${group.id}'", t)
      }
    }
  }

  companion object {
    private val LOG = logger<AndroidStudioProjectStateCollectorsBridge>()
  }
}

private val INITIAL_DELAY = 6.minutes
private val INTERVAL = 12.hours

/**
 * Group IDs of [ProjectUsagesCollector]s whose metrics should be forwarded to [AndroidStudioEventLogger]. Only groups that the logger
 * actually handles need to be listed here.
 */
private val FORWARDED_GROUPS = setOf("file.types", "kotlin.project.configuration")

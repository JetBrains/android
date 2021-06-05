// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.stats

import com.intellij.concurrency.JobScheduler
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import java.util.Collections
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * AndroidStudioStatisticsJobScheduler schedules a periodic task to log project
 * related events to our usage tracking system
 *
 * This is copied from IntelliJ's com.intellij.internal.statistic.update.StatisticsJobScheduler
 * with the IntelliJ specific code removed. This class will be removed once IntelliJ's scheduler
 * is split into multiple classes.
 */

@InternalIgnoreDependencyViolation
internal object AndroidStudioStatisticsJobScheduler : ApplicationInitializedListener {
  private const val LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN = 10
  private const val LOG_APPLICATION_STATES_DELAY_IN_MIN = 24 * 60
  private const val LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN = 1
  private const val LOG_PROJECTS_STATES_DELAY_IN_MIN = 12 * 60
  private val persistStatisticsSessionsMap
    = Collections.synchronizedMap(HashMap<Project, Future<*>>())

  override fun componentsInitialized() {
    if (!StatisticsUploadAssistant.isSendAllowed()) return
    JobScheduler.getScheduler().scheduleWithFixedDelay(
      { FUStateUsagesLogger.create().logApplicationStates() },
      LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN.toLong(),
      LOG_APPLICATION_STATES_DELAY_IN_MIN.toLong(), TimeUnit.MINUTES)

    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        val scheduledFuture = JobScheduler.getScheduler().schedule(
          {
            //wait until initial indexation will be finished
            DumbService.getInstance(project).runWhenSmart {
              val future = JobScheduler.getScheduler()
                .scheduleWithFixedDelay(
                  {
                    FUStateUsagesLogger.create().logProjectStates(project, EmptyProgressIndicator())
                  },
                  0, LOG_PROJECTS_STATES_DELAY_IN_MIN.toLong(), TimeUnit.MINUTES)
              persistStatisticsSessionsMap[project] = future
            }
          }, LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN.toLong(), TimeUnit.MINUTES)
        persistStatisticsSessionsMap[project] = scheduledFuture
      }

      override fun projectClosed(project: Project) {
        val future = persistStatisticsSessionsMap.remove(project)
        future?.cancel(true)
      }
    })
  }
}


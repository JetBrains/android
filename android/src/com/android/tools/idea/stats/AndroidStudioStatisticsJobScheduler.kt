// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.stats

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * AndroidStudioStatisticsJobScheduler schedules a periodic task to log project
 * related events to our usage tracking system
 *
 * This is copied from IntelliJ's com.intellij.internal.statistic.update.StatisticsJobScheduler
 * with the IntelliJ specific code removed. This class will be removed once IntelliJ's scheduler
 * is split into multiple classes.
 */
@InternalIgnoreDependencyViolation
private class AndroidStudioStatisticsJobScheduler : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch {
      if (!StatisticsUploadAssistant.isSendAllowed()) {
        return@launch
      }

      delay(10.minutes)
      while (true) {
        FUStateUsagesLogger.create().logApplicationStates()
        delay((24 * 60).minutes)
      }
    }
  }

  class MyStartupActivity : ProjectPostStartupActivity {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      coroutineScope {
        delay(1.minutes)
        // wait until initial indexation will be finished
        DumbService.getInstance(project).runWhenSmart {
          launch {
            while (true) {
              FUStateUsagesLogger.create().logProjectStates(project, EmptyProgressIndicator())
              delay((12 * 60).minutes)
            }
          }
        }

        awaitCancellation()
      }
    }
  }
}


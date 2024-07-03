/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildOutputWindowStats
import com.intellij.build.BuildViewManager
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.JavacOutputParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.execution.build.output.GradleBuildScriptErrorParser

class BuildOutputParserManager @TestOnly constructor(
  private val project: Project,
  val buildOutputParsers: List<BuildOutputParser>
) {
  // TODO(b/143478291): with linked projects, there will be multiple build tasks with the same project. buildOutputParsers should be updated to a map from task id to list of BuildOutputParser.
  @Suppress("unused")
  private constructor(project: Project) : this(project,
                                               listOf(GradleBuildOutputParser(),
                                                      ClangOutputParser(),
                                                      CmakeOutputParser(),
                                                      XmlErrorOutputParser(),
                                                      JavaLanguageLevelDeprecationOutputParser(),
                                                      AndroidGradlePluginOutputParser(),
                                                      DataBindingOutputParser(),
                                                      JavacOutputParser(),
                                                      KotlincWithQuickFixesParser(),
                                                      ConfigurationCacheErrorParser(),
                                                      TomlErrorParser(),
                                                      GradleBuildScriptErrorParser()).map { BuildOutputParserWrapper(it) })

  fun onBuildStart(externalSystemTaskId: ExternalSystemTaskId) {
    val disposable = Disposer.newDisposable("syncViewListenerDisposable")
    Disposer.register(project, disposable)
    val errorsListener = BuildOutputErrorsListener(externalSystemTaskId, disposable) { buildErrorMessages ->
      try {
        // It is possible that buildErrorMessages is empty when build failed, which means the error message is not handled
        // by any of the parsers. Log failure event with empty error message in this case.
        val buildOutputWindowStats = BuildOutputWindowStats.newBuilder().addAllBuildErrorMessages(buildErrorMessages).build()
        UsageTracker.log(
          AndroidStudioEvent.newBuilder().withProjectId(project)
            .setKind(AndroidStudioEvent.EventKind.BUILD_OUTPUT_WINDOW_STATS)
            .setBuildOutputWindowStats(buildOutputWindowStats)
        )
      }
      catch (e: Exception) {
        Logger.getInstance("BuildFailureMetricsReporting").error("Failed to send metrics", e)
      }
    }
    project.getService(BuildViewManager::class.java).addListener(errorsListener, disposable)
  }
}
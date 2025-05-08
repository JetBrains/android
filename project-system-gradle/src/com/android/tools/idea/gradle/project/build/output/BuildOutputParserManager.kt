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
import com.android.tools.idea.gradle.project.build.output.tomlParser.TomlErrorParser
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildOutputWindowStats
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.StartEvent
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import org.apache.commons.lang3.ClassUtils
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

class BuildOutputParserManager(private val project: Project) {

  private val tasksEventIds: MutableMap<String, Any> = ConcurrentHashMap()

  fun getBuildOutputParsers(buildId: ExternalSystemTaskId): List<BuildOutputParser> {
    val failureHandlers = listOf(
      ConfigurationCacheErrorParser(),
      DeclarativeErrorParser(),
      TomlErrorParser()
    )
    val buildOutputParsers: List<BuildOutputParser> = listOf(
      GradleBuildOutputParser(),
      ClangOutputParser(),
      CmakeOutputParser(),
      XmlErrorOutputParser(),
      JavaLanguageLevelDeprecationOutputParser(),
      AndroidGradlePluginOutputParser(),
      DataBindingOutputParser(),
      FilteringJavacOutputParser(),
      FilteringGradleCompilationReportParser(),
      KotlincWithQuickFixesParser(),
      GradleBuildMultipleFailuresParser(failureHandlers),
      GradleBuildSingleFailureParser(failureHandlers)
    )
    return buildOutputParsers.map { BuildOutputParserWrapper(it, buildId) }
      .map { FixingParentIdParserWrapper(buildId, it) }
  }

  // Temporal fix until we properly support this case handling in GradleOutputDispatcherFactory
  // TODO (b/414343360): remove once fix is ready in the platform code
  private inner class FixingParentIdParserWrapper(val buildId: ExternalSystemTaskId, val parser: BuildOutputParser) : BuildOutputParser {
    var needFixId = false
    override fun parse(line: String,
                       reader: BuildOutputInstantReader,
                       messageConsumer: Consumer<in BuildEvent>?): Boolean {
      if (line.startsWith("FAILURE: Build completed with ")) {
        needFixId = true
      }
      val fixedReader = reader.takeUnless { needFixId && it.parentEventId != buildId } ?:
        object : BuildOutputInstantReader {
          override fun getParentEventId(): Any = buildId
          override fun readLine(): @NlsSafe String? = reader.readLine()
          override fun pushBack() = reader.pushBack()
          override fun pushBack(numberOfLines: Int) = reader.pushBack(numberOfLines)
        }
      return parser.parse(line, fixedReader) { event ->
        var buildEvent = event
        if (needFixId) {
          val parentId = buildEvent.parentId
          if (parentId != buildId && parentId is String) {
            val taskEventId = tasksEventIds[parentId]
            if (taskEventId != null) {
              buildEvent = BuildEventInvocationHandler.wrap(event, taskEventId)
            }
          }
        }
        messageConsumer?.accept(buildEvent)
      }
    }
  }

  fun onBuildStart(externalSystemTaskId: ExternalSystemTaskId) {
    val disposable = Disposer.newDisposable("buildViewListenerDisposable")
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
    project.getService(BuildViewManager::class.java).apply {
      addListener(errorsListener, disposable)
      addListener(object : BuildProgressListener {
        override fun onEvent(buildId: Any, event: BuildEvent) {
          if (buildId != externalSystemTaskId) return
          if (event is StartEvent) {
            val eventId = event.id
            tasksEventIds[event.message] = eventId
          }
        }
      }, disposable)
    }
    Disposer.register(disposable) { tasksEventIds.clear() }
  }
}

// Copied from platform's GradleOutputDispatcherFactory.kt to support temp solution.
private class BuildEventInvocationHandler(
  private val buildEvent: BuildEvent,
  private val parentEventId: Any
) : InvocationHandler {
  override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
    if (method?.name.equals("getParentId")) return parentEventId
    return method?.invoke(buildEvent, *args ?: arrayOfNulls<Any>(0))
  }

  companion object {
    fun wrap(buildEvent: BuildEvent, parentEventId: Any): BuildEvent {
      val classLoader = buildEvent.javaClass.classLoader
      val interfaces = ClassUtils.getAllInterfaces(buildEvent.javaClass)
        .filterIsInstance(Class::class.java)
        .toTypedArray()
      val invocationHandler = BuildEventInvocationHandler(buildEvent, parentEventId)
      return Proxy.newProxyInstance(classLoader, interfaces, invocationHandler) as BuildEvent
    }
  }
}
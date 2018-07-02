/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.daemon.analysis.PsAndroidModuleAnalyzer
import com.android.tools.idea.gradle.structure.daemon.analysis.PsJavaModuleAnalyzer
import com.android.tools.idea.gradle.structure.daemon.analysis.PsModuleAnalyzer
import com.android.tools.idea.gradle.structure.model.*
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.UPDATE
import com.android.tools.idea.gradle.structure.model.PsIssueType.LIBRARY_UPDATES_AVAILABLE
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyVersionQuickFixPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

private val LOG = Logger.getInstance(PsAnalyzerDaemon::class.java)

class PsAnalyzerDaemon(context: PsContext, libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon) : PsDaemon(context) {
  override val mainQueue: MergingUpdateQueue = createQueue("Project Structure Daemon Analyzer", null)
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure Analysis Results Updater", ANY_COMPONENT)
  val issues: PsIssueCollection = PsIssueCollection()

  private val modelAnalyzers: Map<Class<*>, PsModuleAnalyzer<out PsModule>> =
    analyzersMapOf(PsAndroidModuleAnalyzer(context), PsJavaModuleAnalyzer(context))
  private val running = AtomicBoolean(true)

  private val issuesUpdatedEventDispatcher = EventDispatcher.create(IssuesUpdatedListener::class.java)

  init {
    libraryUpdateCheckerDaemon.add({ addApplicableUpdatesAsIssues() }, this)
  }

  fun recreateUpdateIssues() {
    removeIssues(LIBRARY_UPDATES_AVAILABLE)
    addApplicableUpdatesAsIssues()
  }

  private fun addApplicableUpdatesAsIssues() {
    val context = context
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      context.project.forEachModule (Consumer { module ->
        var updatesFound = false
        if (module is PsAndroidModule) {
          module.dependencies.forEachLibraryDependency { dependency ->
            val found = checkForUpdates(dependency)
            if (found) {
              updatesFound = true
            }
          }
        }
        else if (module is PsJavaModule) {
          module.dependencies.forEachLibraryDependency { dependency ->
            val found = checkForUpdates(dependency)
            if (found) {
              updatesFound = true
            }
          }
        }

        if (updatesFound) {
          resultsUpdaterQueue.queue(IssuesComputed(module))
        }
      })
    })
  }

  private fun checkForUpdates(dependency: PsLibraryDependency): Boolean {
    val context = context
    val results = context.libraryUpdateCheckerDaemon.getAvailableUpdates()
    val spec = dependency.spec
    val update = results.findUpdateFor(spec)
    if (update != null) {
      val text = String.format("Newer version available: <b>%1\$s</b> (%2\$s)", update.version, update.repository)

      val mainPath = PsLibraryDependencyNavigationPath(dependency)
      val issue = PsGeneralIssue(text, mainPath, LIBRARY_UPDATES_AVAILABLE, UPDATE,
                                 PsLibraryDependencyVersionQuickFixPath(dependency, update.version, "[Update]"))

      issues.add(issue)
      return true
    }
    return false
  }

  fun add(listener: (PsModel) -> Unit, parentDisposable: Disposable) {
    issuesUpdatedEventDispatcher.addListener(object : IssuesUpdatedListener {
      override fun issuesUpdated(model: PsModel) = listener(model)
    }, parentDisposable)
  }

  override val isRunning: Boolean get() = running.get()

  fun queueCheck(model: PsModel) {
    mainQueue.queue(AnalyzeStructure(model))
  }

  private fun doCheck(model: PsModel) {
    running.set(true)
    val analyzer = modelAnalyzers[model.javaClass]
    if (analyzer == null) {
      LOG.info("Failed to find analyzer for model of type " + model.javaClass.name)
      return
    }
    if (!isStopped) {
      analyzer.analyze(model, issues)
    }
    resultsUpdaterQueue.queue(IssuesComputed(model))
  }

  fun removeIssues(type: PsIssueType) {
    issues.remove(type)
    resultsUpdaterQueue.queue(IssuesComputed(context.project))
  }

  private inner class AnalyzeStructure internal constructor(private val myModel: PsModel) : Update(myModel) {

    override fun run() {
      try {
        doCheck(myModel)
      }
      catch (e: Throwable) {
        LOG.error("Failed to analyze $myModel", e)
      }

    }
  }

  private inner class IssuesComputed(private val myModel: PsModel) : Update(myModel) {

    override fun run() {
      if (isStopped) {
        running.set(false)
        return
      }
      issuesUpdatedEventDispatcher.multicaster.issuesUpdated(myModel)
      running.set(false)
    }
  }

  private interface IssuesUpdatedListener : EventListener {
    fun issuesUpdated(model: PsModel)
  }
}

private fun analyzersMapOf(vararg analyzers: PsModuleAnalyzer<out PsModule>): Map<Class<*>, PsModuleAnalyzer<out PsModule>> =
  analyzers.associateBy { it.supportedModelType }

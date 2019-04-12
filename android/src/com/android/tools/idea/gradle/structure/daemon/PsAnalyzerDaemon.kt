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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.structure.daemon.analysis.PsModelAnalyzer
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.UPDATE
import com.android.tools.idea.gradle.structure.model.PsIssueCollection
import com.android.tools.idea.gradle.structure.model.PsIssueType
import com.android.tools.idea.gradle.structure.model.PsIssueType.LIBRARY_UPDATES_AVAILABLE
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyVersionQuickFixPath
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.util.EventListener
import java.util.function.Consumer

private val LOG = Logger.getInstance(PsAnalyzerDaemon::class.java)

class PsAnalyzerDaemon(
  parentDisposable: Disposable,
  private val project: PsProject,
  private val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon,
  private val modelAnalyzers: Map<Class<*>, PsModelAnalyzer<out PsModule>>
) :
  PsDaemon(parentDisposable) {
  override val mainQueue: MergingUpdateQueue = createQueue("Project Structure Daemon Analyzer", null)
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure Analysis Results Updater", ANY_COMPONENT)
  val issues: PsIssueCollection = PsIssueCollection()

  private val onRunningEventDispatcher = EventDispatcher.create(IssuesUpdatedListener::class.java)
  private val issuesUpdatedEventDispatcher = EventDispatcher.create(IssuesUpdatedListener::class.java)

  init {
    libraryUpdateCheckerDaemon.add({ addApplicableUpdatesAsIssues() }, this)
  }

  @UiThread
  fun recreateUpdateIssues() {
    removeIssues(LIBRARY_UPDATES_AVAILABLE, now = true)
    addApplicableUpdatesAsIssues()
  }

  @UiThread
  private fun addApplicableUpdatesAsIssues() {
    project.forEachModule(Consumer { module ->
      var updatesFound = false
      if ((module is PsAndroidModule) || (module is PsJavaModule)) {
        module.dependencies.forEachLibraryDependency { dependency ->
          val found = checkForUpdates(dependency)
          if (found) {
            updatesFound = true
          }
        }
      }
      if (updatesFound) {
        notifyUpdated(now = false)
      }
    })
    notifyRunning()
  }

  @UiThread
  private fun checkForUpdates(dependency: PsDeclaredLibraryDependency): Boolean {
    val results = libraryUpdateCheckerDaemon.getAvailableUpdates()
    val spec = dependency.spec
    val update = results.findUpdateFor(spec)
    if (update != null) {
      val text = String.format("Newer version available: <b>%1\$s</b> (%2\$s)", update.version, update.repository)

      val mainPath = dependency.path
      val versionValue = dependency.versionProperty.bind(Unit).getParsedValue().value
      val valueIsReference = versionValue is ParsedValue.Set.Parsed && versionValue.dslText is DslText.Reference
      val issue = PsGeneralIssue(
        text,
        "",
        mainPath,
        LIBRARY_UPDATES_AVAILABLE, UPDATE,
        if (!valueIsReference)
          listOf(PsLibraryDependencyVersionQuickFixPath(dependency, update.version.orEmpty()))
        else
          listOf(
            PsLibraryDependencyVersionQuickFixPath(dependency, update.version.orEmpty(), updateVariable = true),
            PsLibraryDependencyVersionQuickFixPath(dependency, update.version.orEmpty(), updateVariable = false)
          ))
      issues.add(issue)
      return true
    }
    return false
  }

  fun onIssuesChange(parentDisposable: Disposable, @UiThread listener: () -> Unit) {
    issuesUpdatedEventDispatcher.addListener(object : IssuesUpdatedListener {
      override fun issuesUpdated() = listener()
    }, parentDisposable)
  }

  /**
   * Registers a listener which is notified when the running state has changed (but may also be called in other cases).
   * NOTE: Current implementation may miss some cases when the state changes to not-running. However, it should be enough to handle both
   *       [onRunningChange] and [onIssuesChange].
   */
  fun onRunningChange(parentDisposable: Disposable, @UiThread listener: () -> Unit) {
    onRunningEventDispatcher.addListener(object : IssuesUpdatedListener {
      override fun issuesUpdated() = listener()
    }, parentDisposable)
  }

  @UiThread
  fun queueCheck(model: PsModule) {
    removeIssues(PROJECT_ANALYSIS, byPath = model.path, now = false)
    mainQueue.queue(AnalyzeModuleStructure(model))
    notifyRunning()
  }

  /**
   * Runs validation-essential analysis (must be invoked on EDT).
   */
  fun validate(model: PsModel): Sequence<PsIssue> =
    modelAnalyzers[model.javaClass]?.cast<PsModelAnalyzer<PsModel>>()?.analyze(model) ?: sequenceOf()

  private fun doAnalyzeStructure(model: PsModel) {
    val analyzer = modelAnalyzers[model.javaClass]?.cast<PsModelAnalyzer<PsModel>>()
    if (analyzer == null) {
      LOG.info("Failed to find analyzer for model of type " + model.javaClass.name)
      return
    }
    if (!isStopped) {
      analyzer.analyze(model, issues)
    }
    resultsUpdaterQueue.queue(IssuesComputed())
  }

  fun removeIssues(type: PsIssueType, byPath: PsPath? = null, now: Boolean) {
    issues.remove(type, byPath)
    notifyUpdated(now)
  }

  @UiThread
  fun addAll(newIssues: List<PsIssue>, now: Boolean = true) {
    newIssues.forEach(issues::add)
    notifyUpdated(now)
  }

  @AnyThread
  private fun notifyUpdated(now: Boolean) {
    if (now) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      issuesUpdatedEventDispatcher.multicaster.issuesUpdated()
    }
    else resultsUpdaterQueue.queue(IssuesComputed())
  }

  @UiThread
  private fun notifyRunning() {
    onRunningEventDispatcher.multicaster.issuesUpdated()
  }

  private inner class AnalyzeModuleStructure internal constructor(private val myModel: PsModule) : Update(myModel) {

    override fun run() {
      try {
        if (!isDisposed && !isStopped) {
          doAnalyzeStructure(myModel)
        }
      }
      catch (e: Throwable) {
        LOG.error("Failed to analyze $myModel", e)
      }

    }
  }

  private inner class IssuesComputed() : Update(IssuesComputed::class.java) {

    @UiThread
    override fun run() {
      issuesUpdatedEventDispatcher.multicaster.issuesUpdated()
    }
  }

  private interface IssuesUpdatedListener : EventListener {
    fun issuesUpdated()
  }
}


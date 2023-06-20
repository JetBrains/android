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
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.ERROR
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.INFO
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.UPDATE
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING
import com.android.tools.idea.gradle.structure.model.PsIssueCollection
import com.android.tools.idea.gradle.structure.model.PsIssueType
import com.android.tools.idea.gradle.structure.model.PsIssueType.LIBRARY_UPDATES_AVAILABLE
import com.android.tools.idea.gradle.structure.model.PsIssueType.PLAY_SDK_INDEX_ISSUE
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyVersionQuickFixPath
import com.android.tools.idea.gradle.structure.quickfix.SdkIndexLinkQuickFix
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import java.io.File
import java.util.EventListener

private val LOG = Logger.getInstance(PsAnalyzerDaemon::class.java)

class PsAnalyzerDaemon(
  parentDisposable: Disposable,
  private val project: PsProject,
  private val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon,
  private val sdkIndexCheckerDaemon: PsSdkIndexCheckerDaemon,
  private val modelAnalyzers: Map<Class<*>, PsModelAnalyzer<out PsModule>>
) :
  PsDaemon(parentDisposable) {
  override val mainQueue: MergingUpdateQueue = createQueue("Project Structure Daemon Analyzer", null)
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure Analysis Results Updater", ANY_COMPONENT)

  val issues: PsIssueCollection = PsIssueCollection()

  private val onRunningEventDispatcher = EventDispatcher.create(IssuesUpdatedListener::class.java)
  private val issuesUpdatedEventDispatcher = EventDispatcher.create(IssuesUpdatedListener::class.java)

  init {
    libraryUpdateCheckerDaemon.add({ recreateUpdatesAsIssues() }, this)
    sdkIndexCheckerDaemon.add({ recreateSdkIndexIssues() }, this)
  }

  @UiThread
  fun recreateIssues() {
    libraryUpdateCheckerDaemon.queueUpdateCheck()
    sdkIndexCheckerDaemon.queueCheck()
  }

  @UiThread
  private fun recreateUpdatesAsIssues() {
    removeIssues(LIBRARY_UPDATES_AVAILABLE, now = true)
    addAll(project.modules.flatMap { module -> module.dependencies.libraries.mapNotNull { getAvailableUpdatesFor(it) } }, now = false)
    notifyRunning()
  }

  @UiThread
  private fun getAvailableUpdatesFor(dependency: PsDeclaredLibraryDependency): PsGeneralIssue? {
    val results = libraryUpdateCheckerDaemon.availableLibraryUpdateStorage
    val spec = dependency.spec
    val versionToUpdateTo = results.findUpdatedVersionFor(spec) ?: return null
    val text = "Newer version available: <b>$versionToUpdateTo<b>"

    val mainPath = dependency.path
    val versionValue = dependency.versionProperty.bind(Unit).getParsedValue().value
    val valueIsReference = versionValue is ParsedValue.Set.Parsed && versionValue.dslText is DslText.Reference
    return PsGeneralIssue(
        text,
        "",
        mainPath,
        LIBRARY_UPDATES_AVAILABLE, UPDATE,
        if (!valueIsReference)
          listOf(PsLibraryDependencyVersionQuickFixPath(dependency, versionToUpdateTo.toString()))
        else
          listOf(
            PsLibraryDependencyVersionQuickFixPath(dependency, versionToUpdateTo.toString(), updateVariable = true),
            PsLibraryDependencyVersionQuickFixPath(dependency, versionToUpdateTo.toString(), updateVariable = false)
          ))
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
  @Suppress("UNCHECKED_CAST")
  fun validate(model: PsModel): Sequence<PsIssue> =
    (modelAnalyzers[model.javaClass] as? PsModelAnalyzer<PsModel>)?.analyze(model) ?: sequenceOf()

  private fun doAnalyzeStructure(model: PsModel) {
    @Suppress("UNCHECKED_CAST")
    val analyzer = modelAnalyzers[model.javaClass] as? PsModelAnalyzer<PsModel>
    if (analyzer == null) {
      LOG.info("Failed to find analyzer for model of type " + model.javaClass.name)
      return
    }
    if (!isStopped) {
      assert(analyzer.supportedModelType.isInstance(model))
      invokeAndWaitIfNeeded(ModalityState.any()) {
        val newIssues =
          if (!isStopped && !analyzer.disposed) analyzer.analyze(analyzer.supportedModelType.cast(model)).toList() else emptyList()
        addAll(newIssues, now = false)
      }
    }
    resultsUpdaterQueue.queue(IssuesComputed())
  }

  private fun removeIssues(type: PsIssueType, byPath: PsPath? = null, now: Boolean) {
    issues.remove(type, byPath)
    notifyUpdated(now)
  }

  @UiThread
  fun addAll(newIssues: List<PsIssue>, now: Boolean) {
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

  private inner class AnalyzeModuleStructure(private val myModel: PsModule): Update(myModel) {
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

  private inner class IssuesComputed: Update(IssuesComputed::class.java) {
    @UiThread
    override fun run() {
      issuesUpdatedEventDispatcher.multicaster.issuesUpdated()
    }
  }

  private interface IssuesUpdatedListener : EventListener {
    fun issuesUpdated()
  }

  @UiThread
  private fun recreateSdkIndexIssues() {
    removeIssues(PLAY_SDK_INDEX_ISSUE, now = true)
    var numErrors = 0
    var numWarnings = 0
    var numInfo = 0
    var numUpdates = 0
    var numOther = 0
    addAll(project.modules.flatMap { module ->
      module.dependencies.libraries.mapNotNull {
        getSdkIndexIssueFor(it.spec, it.path, it.parent.rootDir)?.also { issue->
          when (issue.severity) {
            ERROR -> numErrors++
            WARNING -> numWarnings++
            INFO -> numInfo++
            UPDATE -> numUpdates++
            // Currently not needed but here to catch when new severities are added
            else -> numOther++
          }
        }
      }
    }, now = false)
    LOG.debug("Issues recreated: $numErrors errors, $numWarnings warnings, $numInfo information, $numUpdates updates, $numOther other")
    notifyRunning()
  }
}

/**
 * Checks if the dependency has issues in the Google Play SDK Index and if it does, returns the one with higher severity.
 *
 * @param dependencySpec: dependency being checked
 * @param libraryPath: path of the library dependency, used generating the issues
 * @param parentModuleRootDir: root dir of the parent module of this dependency
 *
 * @return The issue with the higher severity from the SDK index, or null if there are no issues
 */
fun getSdkIndexIssueFor(dependencySpec: PsArtifactDependencySpec, libraryPath: PsPath, parentModuleRootDir: File?): PsGeneralIssue? {
  val sdkIndex = IdeGooglePlaySdkIndex
  val groupId = dependencySpec.group ?: return null
  val versionString = dependencySpec.version ?: return null
  val artifactId = dependencySpec.name

  if (sdkIndex.isLibraryNonCompliant(groupId, artifactId, versionString, parentModuleRootDir)) {
    val message = sdkIndex.generatePolicyMessage(groupId, artifactId, versionString)
    return createIndexIssue(message, groupId, artifactId, versionString, libraryPath, ERROR)
  }
  val isBlocking = sdkIndex.hasLibraryBlockingIssues(groupId, artifactId, versionString)
  if (isBlocking) {
    if (sdkIndex.hasLibraryCriticalIssues(groupId, artifactId, versionString, parentModuleRootDir)) {
      val message = sdkIndex.generateBlockingCriticalMessage(groupId, artifactId, versionString)
      return createIndexIssue(message, groupId, artifactId, versionString, libraryPath, ERROR)
    }
    if (sdkIndex.isLibraryOutdated(groupId, artifactId, versionString, parentModuleRootDir)) {
      val message = sdkIndex.generateBlockingOutdatedMessage(groupId, artifactId, versionString)
      return createIndexIssue(message, groupId, artifactId, versionString, libraryPath, ERROR)
    }
  }
  else {
    if (sdkIndex.isLibraryOutdated(groupId, artifactId, versionString, parentModuleRootDir)) {
      val message = sdkIndex.generateOutdatedMessage(groupId, artifactId, versionString)
      return createIndexIssue(message, groupId, artifactId, versionString, libraryPath, WARNING)
    }
    if (sdkIndex.hasLibraryCriticalIssues(groupId, artifactId, versionString, parentModuleRootDir)) {
      val message = sdkIndex.generateCriticalMessage(groupId, artifactId, versionString)
      return createIndexIssue(message, groupId, artifactId, versionString, libraryPath, INFO)
    }
  }
  return null
}

private fun createIndexIssue(
  message: String,
  groupId: String,
  artifactId: String,
  versionString: String,
  mainPath: PsPath,
  severity: PsIssue.Severity
): PsGeneralIssue {
  val sdkIndex = IdeGooglePlaySdkIndex
  val url = sdkIndex.getSdkUrl(groupId, artifactId)
  val fixes = if (url != null) {
    listOf(SdkIndexLinkQuickFix("View details on Google Play SDK Index", url, groupId, artifactId, versionString))
  }
  else {
    listOf()
  }
  return PsGeneralIssue(
    message,
    "",
    mainPath,
    PLAY_SDK_INDEX_ISSUE,
    severity,
    fixes
  )
}

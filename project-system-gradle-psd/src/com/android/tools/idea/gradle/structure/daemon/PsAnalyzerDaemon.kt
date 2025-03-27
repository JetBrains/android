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
import com.android.ide.common.gradle.Version
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
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyVersionQuickFixPath
import com.android.tools.idea.gradle.structure.quickfix.SdkIndexLinkQuickFix
import com.android.tools.idea.gradle.structure.quickfix.SdkIndexLinkQuickFixNoLog
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex
import com.android.tools.lint.checks.GooglePlaySdkIndex
import com.android.tools.lint.detector.api.TextFormat
import com.android.utils.SdkUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.VisibleForTesting
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
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure Analysis Results Updater", MergingUpdateQueue.ANY_COMPONENT)

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
      ThreadingAssertions.assertEventDispatchThread()
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
      module.dependencies.libraries.map {
        getSdkIndexIssueFor(it, availableUpdates = AvailableLibraryUpdateStorage.getInstance(project.ideProject))
      }.flatten()
        .onEach { issue ->
          when (issue.severity) {
            ERROR -> numErrors++
            WARNING -> numWarnings++
            INFO -> numInfo++
            UPDATE -> numUpdates++
            // Currently not needed but here to catch when new severities are added
            else -> numOther++
          }
        }
    }, now = false)
    LOG.debug("Issues recreated: $numErrors errors, $numWarnings warnings, $numInfo information, $numUpdates updates, $numOther other")
    notifyRunning()
  }
}

/**
 * Returns the list of issues from the Google Play SDK Index that the given library has.
 *
 * @param dependency: dependency being checked
 *
 * @return The list of issues from the SDK index for the given library, empty if no issues are present
 */
fun getSdkIndexIssueFor(dependency: PsDeclaredLibraryDependency,
                        availableUpdates: AvailableLibraryUpdateStorage? = null): List<PsGeneralIssue> {
  val updateFixes = generateUpdateFixesForSdkIndex(dependency, availableUpdates)
  return getSdkIndexIssueFor(dependency.spec, dependency.path, dependency.parent.rootDir, updateFixes)
}

private fun generateUpdateFixesForSdkIndex(dependency: PsDeclaredLibraryDependency,
                                   availableUpdates: AvailableLibraryUpdateStorage?): List<PsQuickFix> {
  val sdkIndex = IdeGooglePlaySdkIndex
  val dependencySpec = dependency.spec
  val groupId = dependencySpec.group?: return listOf()
  val artifactId = dependencySpec.name
  val versionFromStorage = availableUpdates?.findUpdatedVersionFor(dependencySpec)
  val versionFromIndex = sdkIndex.getLatestVersion(groupId, artifactId)?.let {
    if (sdkIndex.hasLibraryErrorOrWarning(groupId, artifactId, it)) {
      null
    }
    else {
      val parsedVersion = Version.parse(it)
      if (parsedVersion.isPreview) {
        null
      }
      else {
        parsedVersion
      }
    }
  }

  val versionToUpdateTo = if (versionFromIndex == null) {
    versionFromStorage
  }
  else {
    if (versionFromStorage == null) {
      versionFromIndex
    }
    else {
      if (versionFromIndex < versionFromStorage) {
        versionFromStorage
      }
      else {
        versionFromIndex
      }
    }
  }

  return if (versionToUpdateTo != null) {
    val versionValue = dependency.versionProperty.bind(Unit).getParsedValue().value
    val valueIsReference = versionValue is ParsedValue.Set.Parsed && versionValue.dslText is DslText.Reference
    val onUpdateCallback: (() -> Unit)? = if (dependencySpec.version != null) ({
      sdkIndex.logUpdateLibraryVersionFixApplied(groupId, artifactId, dependency.version.toString(), versionToUpdateTo.toString(), null)
    })
    else {
      null
    }
    if (valueIsReference) {
      listOf(
        PsLibraryDependencyVersionQuickFixPath(dependency, versionToUpdateTo.toString(), updateVariable = true, addVersionInText = true, onUpdate = onUpdateCallback),
        PsLibraryDependencyVersionQuickFixPath(dependency, versionToUpdateTo.toString(), updateVariable = false, addVersionInText = true, onUpdate = onUpdateCallback),
      )
    }
    else {
      listOf(
        PsLibraryDependencyVersionQuickFixPath(dependency, versionToUpdateTo.toString(), addVersionInText = true, onUpdate = onUpdateCallback),
      )
    }
  }
  else {
    listOf()
  }
}

@VisibleForTesting
fun getSdkIndexIssueFor(dependencySpec: PsArtifactDependencySpec,
                        libraryPath: PsPath,
                        parentModuleRootDir: File?,
                        updateFixes: List<PsQuickFix> = listOf(),
                        sdkIndex: GooglePlaySdkIndex = IdeGooglePlaySdkIndex,
): List<PsGeneralIssue> {
  val groupId = dependencySpec.group ?: return emptyList()
  val versionString = dependencySpec.version ?: return emptyList()
  val artifactId = dependencySpec.name

  // Report all SDK Index issues without grouping them(b/316038712):
  val foundIssues: MutableList<PsGeneralIssue> = mutableListOf()
  val isBlocking = sdkIndex.hasLibraryBlockingIssues(groupId, artifactId, versionString)
  foundIssues.addIfNotNull(generateDeprecatedLibraryIssue(groupId, artifactId, versionString, isBlocking, parentModuleRootDir, libraryPath, sdkIndex))
  foundIssues.addAll(generatePolicyIssues(groupId, artifactId, versionString, isBlocking, parentModuleRootDir, libraryPath, updateFixes, sdkIndex))
  var criticalIssue = generateCriticalIssue(groupId, artifactId, versionString, isBlocking, parentModuleRootDir, libraryPath, updateFixes, sdkIndex)
  if (isBlocking) {
    // Critical issues are added before vulnerability issues if they are blocking
    foundIssues.addIfNotNull(criticalIssue)
    // Set to null so it is not added multiple times
    criticalIssue = null
  }
  foundIssues.addAll(generateVulnerabilityIssues(groupId, artifactId, versionString, isBlocking, parentModuleRootDir, libraryPath, updateFixes, sdkIndex))
  foundIssues.addIfNotNull(generateOutdatedIssue(groupId, artifactId, versionString, isBlocking, parentModuleRootDir, libraryPath, updateFixes, sdkIndex))
  foundIssues.addIfNotNull(criticalIssue)
  return foundIssues
}

private fun generateDeprecatedLibraryIssue(
  groupId: String,
  artifactId: String,
  versionString: String,
  isBlocking: Boolean,
  file: File?,
  path: PsPath,
  index: GooglePlaySdkIndex): PsGeneralIssue? {
  if (!index.isLibraryDeprecated(groupId, artifactId, versionString, file)) {
    return null
  }
  val severity = if (isBlocking) ERROR else WARNING
  val message = index.generateDeprecatedMessage(groupId, artifactId)
  return createIndexIssue(message, groupId, artifactId, versionString, path, severity, index, listOf())
}

private fun generatePolicyIssues(
  groupId: String,
  artifactId: String,
  versionString: String,
  isBlocking: Boolean,
  file: File?,
  path: PsPath,
  updateFixes: List<PsQuickFix>,
  index: GooglePlaySdkIndex): List<PsGeneralIssue> {
  if (!index.isLibraryNonCompliant(groupId, artifactId, versionString, file)) {
    return listOf()
  }
  val severity = if (isBlocking) ERROR else WARNING
  val messages = if (isBlocking) index.generateBlockingPolicyMessages(groupId, artifactId, versionString) else index.generatePolicyMessages(groupId, artifactId, versionString)
  return messages.map { message->
    createIndexIssue(message, groupId, artifactId, versionString, path, severity, index, updateFixes)
  }
}

private fun generateCriticalIssue(
  groupId: String,
  artifactId: String,
  versionString: String,
  isBlocking: Boolean,
  file: File?,
  path: PsPath,
  updateFixes: List<PsQuickFix>,
  index: GooglePlaySdkIndex): PsGeneralIssue? {
  if (!index.hasLibraryCriticalIssues(groupId, artifactId, versionString, file)) {
    return null
  }
  val severity = if (isBlocking) ERROR else INFO
  val message = if (isBlocking) index.generateBlockingCriticalMessage(groupId, artifactId, versionString) else index.generateCriticalMessage(groupId, artifactId, versionString)
  return createIndexIssue(message, groupId, artifactId, versionString, path, severity, index, updateFixes)
}

fun generateVulnerabilityIssues(
  groupId: String,
  artifactId: String,
  versionString: String,
  isBlocking: Boolean,
  file: File?,
  path: PsPath,
  updateFixes: List<PsQuickFix>,
  index: GooglePlaySdkIndex): List<PsGeneralIssue> {
  if (!index.hasLibraryVulnerabilityIssues(groupId, artifactId, versionString, file)) {
    return listOf()
  }
  val severity = if (isBlocking) ERROR else WARNING
  val messages = index.generateVulnerabilityMessages(groupId, artifactId, versionString)
  return messages.map { message->
    val fixes = mutableListOf<PsQuickFix>()
    fixes.addAll(updateFixes)
    createVulnerabilityQuickFix(message)?.let { fixes.add(it) }
    createIndexIssue(message.description, groupId, artifactId, versionString, path, severity, index, fixes)
  }
}

private fun generateOutdatedIssue(
  groupId: String,
  artifactId: String,
  versionString: String,
  isBlocking: Boolean,
  file: File?,
  path: PsPath,
  updateFixes: List<PsQuickFix>,
  index: GooglePlaySdkIndex): PsGeneralIssue? {
  if (!index.isLibraryOutdated(groupId, artifactId, versionString, file)) {
    return null
  }
  val severity = if (isBlocking) ERROR else WARNING
  val message = if (isBlocking) index.generateBlockingOutdatedMessage(groupId, artifactId, versionString) else index.generateOutdatedMessage(groupId, artifactId, versionString)
  return createIndexIssue(message, groupId, artifactId, versionString, path, severity, index, updateFixes)
}

private fun createIndexIssue(
  message: String,
  groupId: String,
  artifactId: String,
  versionString: String,
  mainPath: PsPath,
  severity: PsIssue.Severity,
  sdkIndex: GooglePlaySdkIndex,
  additionalFixes: List<PsQuickFix> = listOf()
): PsGeneralIssue {
  val url = sdkIndex.getSdkUrl(groupId, artifactId)
  val fixes = mutableListOf<PsQuickFix>()
  fixes.addAll(additionalFixes)
  if (url != null) {
    fixes.add(SdkIndexLinkQuickFix("View details", url, groupId, artifactId, versionString))
  }
  val formattedMessage = formatToPSD(message)
  return PsGeneralIssue(
    formattedMessage,
    "",
    mainPath,
    PLAY_SDK_INDEX_ISSUE,
    severity,
    fixes
  )
}

private fun createVulnerabilityQuickFix(vulnerability: GooglePlaySdkIndex.Companion.VulnerabilityDescription): PsQuickFix? {
  return if (vulnerability.link.isNullOrBlank()) {
    null
  }
  else {
    SdkIndexLinkQuickFixNoLog("Learn more", vulnerability.link!!)
  }
}

/**
 * The messages generated by GooglePlaySdkIndex use [TextFormat.RAW], but PSD only supports html tags.
 *
 * This function converts the text so PSD can display it by applying the following transformations (in order):
 * 1. Replace line break to double break to match look shown for lint issues (b/369997141)
 * 2. Wrap text to [maxWidth] characters per line
 * 3. Remove starting and ending blank characters
 * 4. Convert to HTML
 */
@VisibleForTesting
fun formatToPSD(message: String, maxWidth: Int = 55): String {
  return TextFormat.RAW.toHtml(SdkUtils.wrap(message.replace("\n", "\n\n"), maxWidth, maxWidth, /* no hanging*/null, /*no breaks*/false).trim())
}

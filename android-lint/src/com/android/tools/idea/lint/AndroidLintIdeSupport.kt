/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.SdkMavenRepository
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.STRONGLY_RECOMMEND
import com.android.tools.idea.gradle.project.upgrade.computeGradlePluginUpgradeState
import com.android.tools.idea.gradle.project.upgrade.findPluginInfo
import com.android.tools.idea.gradle.project.upgrade.performRecommendedPluginUpgrade
import com.android.tools.idea.gradle.project.upgrade.shouldRecommendPluginUpgrade
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintEditorResult
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.idea.lint.common.LintIdeSupport
import com.android.tools.idea.lint.common.LintResult
import com.android.tools.idea.lint.common.getModuleDir
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.requiresAndroidModel
import com.android.tools.idea.res.AndroidFileChangeListener
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioSdkUtil
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Platform
import com.google.wireless.android.sdk.stats.LintSession
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.facet.ProjectFacetManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import java.io.File
import java.util.EnumSet
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.plugins.gradle.config.isGradleFile
import org.toml.lang.psi.TomlFileType

class AndroidLintIdeSupport : LintIdeSupport() {
  override fun getIssueRegistry(): IssueRegistry {
    return AndroidLintIdeIssueRegistry()
  }

  override fun getBaselineFile(client: LintIdeClient, module: Module): File? {
    val model = GradleAndroidModel.get(module) ?: return null
    val version = model.agpVersion ?: return null
    if (version.isAtLeast(2, 3, 1)) {
      val options = model.androidProject.lintOptions
      try {
        val baselineFile = options.baselineFile
        if (baselineFile != null) {
          return baselineFile
        }
      } catch (unsupported: Throwable) {}
    }

    // Baselines can also be configured via lint.xml
    module.getModuleDir()?.let { dir ->
      client.getConfiguration(dir)?.baselineFile?.let { baseline ->
        return baseline
      }
    }

    return null
  }

  override fun getSeverityOverrides(module: Module): Map<String, Int>? {
    val model = GradleAndroidModel.get(module) ?: return null
    val version = model.agpVersion ?: return null
    if (version.isAtLeast(2, 3, 1)) {
      val options = model.androidProject.lintOptions
      try {
        return options.severityOverrides
      } catch (unsupported: Throwable) {}
    }
    return null
  }

  override fun askForAttributeValue(attributeName: String, context: PsiElement): String? {
    val facet = AndroidFacet.getInstance(context)
    val message = "Specify value of attribute '$attributeName'"
    val title = "Set Attribute Value"
    if (facet != null) {
      val srm = ModuleResourceManagers.getInstance(facet).frameworkResourceManager
      if (srm != null) {
        val attrDefs = srm.attributeDefinitions
        if (attrDefs != null) {
          val def = attrDefs.getAttrDefByName(attributeName)
          if (def != null) {
            val variants = def.values
            if (variants.isNotEmpty()) {
              return Messages.showEditableChooseDialog(
                message,
                title,
                Messages.getQuestionIcon(),
                variants,
                variants[0],
                null
              )
            }
          }
        }
      }
    }
    return Messages.showInputDialog(context.project, message, title, Messages.getQuestionIcon())
  }

  override fun canAnnotate(file: PsiFile, module: Module): Boolean {
    // Limit checks to Android modules
    val facet = AndroidFacet.getInstance(module)
    if (facet == null && !AndroidLintIdeProject.hasAndroidModule(module.project)) {
      return false
    }
    val fileType = file.fileType
    if (
      fileType === JavaFileType.INSTANCE ||
        fileType === KotlinFileType.INSTANCE ||
        fileType === PropertiesFileType.INSTANCE ||
        fileType === TomlFileType
    ) {
      return true
    }
    if (fileType === XmlFileType.INSTANCE) {
      return facet != null &&
        (ModuleResourceManagers.getInstance(facet)
          .localResourceManager
          .getFileResourceFolderType(file) != null || ANDROID_MANIFEST_XML == file.name)
    } else if (fileType === FileTypes.PLAIN_TEXT) {
      return super.canAnnotate(file, module)
    } else if (file.isGradleFile()) {
      // Ensure that we're listening to the PSI structure for Gradle file edit notifications
      val project = file.project
      if (project.requiresAndroidModel()) {
        AndroidFileChangeListener.getInstance(project)
      }
      return true
    }
    return false
  }

  override fun canAnalyze(project: Project): Boolean {
    // Only run in Android projects. This is relevant when the Android plugin is
    // enabled in IntelliJ.
    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return false
    }
    return true
  }

  // Projects
  override fun createProject(
    client: LintIdeClient,
    files: List<VirtualFile>?,
    vararg modules: Module
  ): List<com.android.tools.lint.detector.api.Project> {
    return AndroidLintIdeProject.create(client, files, *modules)
  }

  override fun createProjectForSingleFile(
    client: LintIdeClient,
    file: VirtualFile?,
    module: Module
  ): Pair<
    com.android.tools.lint.detector.api.Project, com.android.tools.lint.detector.api.Project
  > {
    return AndroidLintIdeProject.createForSingleFile(client, file, module)
  }

  override fun createClient(project: Project, lintResult: LintResult): LintIdeClient {
    return AndroidLintIdeClient(project, lintResult)
  }

  override fun createBatchClient(lintResult: LintBatchResult): LintIdeClient {
    return AndroidLintIdeClient(lintResult.project, lintResult)
  }

  override fun createEditorClient(lintResult: LintEditorResult): LintIdeClient {
    return AndroidLintIdeClient(lintResult.getModule().project, lintResult)
  }

  // Gradle
  override fun updateToLatest(module: Module, gc: GradleCoordinate) {
    // Based on UpgradeConstraintLayoutFix
    StudioSdkUtil.reloadRemoteSdkWithModalProgress()
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val progress = StudioLoggerProgressIndicator(AndroidLintIdeSupport::class.java)
    val p = SdkMavenRepository.findLatestVersion(gc, sdkHandler, null, progress)
    if (p != null) {
      val latest = SdkMavenRepository.getCoordinateFromSdkPath(p.path)
      if (latest != null) { // should always be the case unless the version suffix is somehow wrong
        module.getModuleSystem().updateLibrariesToVersion(listOf(latest))
        module.project
          .getProjectSystem()
          .getSyncManager()
          .syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_DEPENDENCY_UPDATED)
      }
    }
  }

  override fun recommendedAgpVersion(project: Project): AgpVersion? {
    val current = project.findPluginInfo()?.pluginVersion ?: return null
    val latestKnown = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val published = IdeGoogleMavenRepository.getAgpVersions()
    val state = computeGradlePluginUpgradeState(current, latestKnown, published)
    return when (state.importance) {
      RECOMMEND,
      STRONGLY_RECOMMEND -> state.target
      else -> null
    }
  }
  override fun shouldRecommendUpdateAgpToLatest(project: Project): Boolean {
    return shouldRecommendPluginUpgrade(project).upgrade
  }
  override fun updateAgpToLatest(project: Project) {
    ApplicationManager.getApplication().executeOnPooledThread {
      performRecommendedPluginUpgrade(project)
    }
  }

  override fun shouldOfferUpgradeAssistantForDeprecatedConfigurations(project: Project) = true

  override fun updateDeprecatedConfigurations(project: Project, element: PsiElement) {
    ApplicationManager.getApplication().executeOnPooledThread {
      project
        .getService(AssistantInvoker::class.java)
        .performDeprecatedConfigurationsUpgrade(project, element)
    }
  }

  override fun resolveDynamic(project: Project, gc: GradleCoordinate): String? {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    return RepositoryUrlManager.get().resolveDynamicCoordinateVersion(gc, project, sdkHandler)
  }

  override fun getPlatforms(): EnumSet<Platform> = Platform.ANDROID_SET

  // Analytics
  override fun canRequestFeedback(): Boolean = ProvideLintFeedbackPanel.canRequestFeedback()

  override fun requestFeedbackFix(issue: Issue): LocalQuickFix = ProvideLintFeedbackFix(issue.id)
  override fun requestFeedbackIntentionAction(issue: Issue): IntentionAction =
    ProvideLintFeedbackIntentionAction(issue.id)

  // Random number generator used by logSession below. We're using a seed of 0 because
  // we don't need true randomness, just an even distribution. This generator is
  // visible such that tests can reset the seed each time such that the test order
  // does not matter (and therefore we're using java.util.Random instead of kotlin.Random
  // to get access to setSeed()
  @VisibleForTesting val random: java.util.Random = java.util.Random(0)

  override fun logSession(lint: LintDriver, lintResult: LintEditorResult) {
    // Lint creates a LOT of session data (since it runs after every edit pause in the editor.
    // Let's only submit 1 out of every 100 reports; the results will still express trends and
    // relative importance of lint checks.
    if (random.nextDouble() < 0.01) { // nextDouble() ~20% faster than nextInt()
      val analytics = LintIdeAnalytics(lintResult.getModule().project)
      analytics.logSession(
        LintSession.AnalysisType.IDE_FILE,
        lint,
        lintResult.getModule(),
        lintResult.problems,
        null
      )
    }
  }

  override fun logSession(lint: LintDriver, module: Module?, lintResult: LintBatchResult) {
    val analytics = LintIdeAnalytics(lintResult.project)
    analytics.logSession(
      LintSession.AnalysisType.IDE_BATCH,
      lint,
      module,
      null,
      lintResult.problemMap
    )
  }

  override fun ensureNamespaceImported(
    file: XmlFile,
    namespaceUri: String,
    suggestedPrefix: String?
  ): String {
    return com.android.tools.idea.res.ensureNamespaceImported(file, namespaceUri, suggestedPrefix)
  }
}

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
import com.android.SdkConstants.EXT_GRADLE_DECLARATIVE
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.RECOMMEND
import com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgradeState.Importance.STRONGLY_RECOMMEND
import com.android.tools.idea.gradle.project.upgrade.computeGradlePluginUpgradeState
import com.android.tools.idea.gradle.project.upgrade.findPluginInfo
import com.android.tools.idea.gradle.repositories.IdeGoogleMavenRepository
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.lint.common.LintBatchResult
import com.android.tools.idea.lint.common.LintEditorResult
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.idea.lint.common.LintIdeSupport
import com.android.tools.idea.lint.common.LintResult
import com.android.tools.idea.lint.common.getModuleDir
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.util.CommonAndroidUtil
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Platform
import com.google.wireless.android.sdk.stats.LintSession
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.getQuestionIcon
import com.intellij.openapi.ui.Messages.showEditableChooseDialog
import com.intellij.openapi.ui.Messages.showInputDialog
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
  override fun getIssueRegistry() = AndroidLintIdeIssueRegistry()

  override fun getBaselineFile(client: LintIdeClient, module: Module): File? {
    val model = GradleAndroidModel.get(module) ?: return null
    if (model.agpVersion.isAtLeast(2, 3, 1)) {
      val options = model.androidProject.lintOptions
      try {
        options.baselineFile?.let {
          return it
        }
      } catch (_: Throwable) {}
    }

    // Baselines can also be configured via lint.xml
    return module.getModuleDir()?.let(client::getConfiguration)?.baselineFile
  }

  override fun getSeverityOverrides(module: Module): Map<String, Int>? {
    val model = GradleAndroidModel.get(module) ?: return null
    if (model.agpVersion.isAtLeast(2, 3, 1)) {
      try {
        return model.androidProject.lintOptions.severityOverrides
      } catch (_: Throwable) {}
    }
    return null
  }

  override fun askForAttributeValue(attributeName: String, context: PsiElement): String? {
    val message = "Specify value of attribute '$attributeName'"
    val title = "Set Attribute Value"
    val variants =
      AndroidFacet.getInstance(context)
        ?.let { ModuleResourceManagers.getInstance(it).frameworkResourceManager }
        ?.attributeDefinitions
        ?.getAttrDefByName(attributeName)
        ?.values
        ?.takeIf(Array<String>::isNotEmpty)

    return if (variants == null) showInputDialog(context.project, message, title, getQuestionIcon())
    else showEditableChooseDialog(message, title, getQuestionIcon(), variants, variants[0], null)
  }

  override fun canAnnotate(file: PsiFile, module: Module): Boolean {
    // Limit checks to Android modules and modules within Android projects.
    val facet = AndroidFacet.getInstance(module)
    if (facet == null && !CommonAndroidUtil.getInstance().isAndroidProject(module.project))
      return false

    if (
      StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.get() && file.name.endsWith(EXT_GRADLE_DECLARATIVE)
    )
      return true

    return when (file.fileType) {
      JavaFileType.INSTANCE,
      KotlinFileType.INSTANCE,
      PropertiesFileType.INSTANCE,
      TomlFileType -> true
      XmlFileType.INSTANCE ->
        facet != null &&
          (ModuleResourceManagers.getInstance(facet)
            .localResourceManager
            .getFileResourceFolderType(file) != null || ANDROID_MANIFEST_XML == file.name)
      FileTypes.PLAIN_TEXT -> super.canAnnotate(file, module)
      else -> file.isGradleFile()
    }
  }

  override fun canAnalyze(project: Project) =
    // Only run in Android projects. This is relevant when the Android plugin is
    // enabled in IntelliJ.
    CommonAndroidUtil.getInstance().isAndroidProject(project)

  // Projects
  override fun createProject(
    client: LintIdeClient,
    files: List<VirtualFile>?,
    vararg modules: Module,
  ): List<com.android.tools.lint.detector.api.Project> =
    AndroidLintIdeProject.create(client, files, *modules)

  override fun createProjectForSingleFile(
    client: LintIdeClient,
    file: VirtualFile?,
    module: Module,
  ): Pair<
    com.android.tools.lint.detector.api.Project,
    com.android.tools.lint.detector.api.Project,
  > {
    return AndroidLintIdeProject.createForSingleFile(client, file, module)
  }

  override fun createClient(project: Project, lintResult: LintResult) =
    AndroidLintIdeClient(project, lintResult)

  override fun createBatchClient(lintResult: LintBatchResult) =
    AndroidLintIdeClient(lintResult.project, lintResult)

  override fun createEditorClient(lintResult: LintEditorResult) =
    AndroidLintIdeClient(lintResult.getModule().project, lintResult)

  override fun recommendedAgpVersion(project: Project): AgpVersion? {
    val current = project.findPluginInfo()?.pluginVersion ?: return null
    val latestKnown = AgpVersions.latestKnown
    val published = IdeGoogleMavenRepository.getAgpVersions()
    val state = computeGradlePluginUpgradeState(current, latestKnown, published)
    return when (state.importance) {
      RECOMMEND,
      STRONGLY_RECOMMEND -> state.target
      else -> null
    }
  }

  override fun shouldRecommendUpdateAgpToLatest(project: Project) =
    project.getService(AssistantInvoker::class.java).shouldRecommendPluginUpgradeToLatest(project)

  override fun updateAgpToLatest(project: Project) {
    project.getService(AssistantInvoker::class.java).performRecommendedPluginUpgrade(project)
  }

  override fun shouldOfferUpgradeAssistantForDeprecatedConfigurations(project: Project) = true

  override fun updateDeprecatedConfigurations(project: Project, element: PsiElement) {
    ApplicationManager.getApplication().executeOnPooledThread {
      project
        .getService(AssistantInvoker::class.java)
        .performDeprecatedConfigurationsUpgrade(project, element)
    }
  }

  override fun resolveDynamicDependency(project: Project, dependency: Dependency): String? {
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    return RepositoryUrlManager.get().resolveDependencyRichVersion(dependency, project, sdkHandler)
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
        null,
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
      lintResult.problemMap,
    )
  }

  override fun logQuickFixInvocation(project: Project, issue: Issue, fixDescription: String) {
    val analytics = LintIdeAnalytics(project)
    analytics.logQuickFixInvocation(issue, fixDescription)
  }

  override fun logTooltipLink(url: String, issue: Issue, project: Project) {
    val analytics = LintIdeAnalytics(project)
    analytics.logTooltipLink(url, issue)
  }

  override fun ensureNamespaceImported(
    file: XmlFile,
    namespaceUri: String,
    suggestedPrefix: String?,
  ) = com.android.tools.idea.res.ensureNamespaceImported(file, namespaceUri, suggestedPrefix)
}

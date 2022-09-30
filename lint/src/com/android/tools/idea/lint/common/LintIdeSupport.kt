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
package com.android.tools.idea.lint.common

import com.android.SdkConstants.FN_ANDROID_PROGUARD_FILE
import com.android.SdkConstants.FN_PROJECT_PROGUARD_FILE
import com.android.SdkConstants.OLD_PROGUARD_FILE
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintClient.Companion.CLIENT_STUDIO
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Platform
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.plugins.gradle.config.isGradleFile
import java.io.File
import java.util.EnumSet

/**
 * Extension point for the general lint support to look up services it does not
 * directly depend upon.
 */
abstract class LintIdeSupport {
  init {
    LintClient.clientName = CLIENT_STUDIO
  }
  companion object {
    @JvmStatic
    fun get(): LintIdeSupport = getApplication().getService(LintIdeSupport::class.java)
                                ?: object : LintIdeSupport() {}
  }

  open fun getIssueRegistry(): IssueRegistry = LintIdeIssueRegistry()

  open fun getBaselineFile(client: LintIdeClient, module: Module): File? {
    val dir = module.getModuleDir() ?: return null
    client.getConfiguration(dir)?.baselineFile?.let { baseline -> return baseline }
    val lintBaseline = File(dir, "lint_baseline.xml")
    if (lintBaseline.exists()) {
      return lintBaseline
    }
    val baseline = File(dir, "baseline.xml")
    if (baseline.exists()) {
      return baseline
    }
    return null
  }

  open fun getPlatforms(): EnumSet<Platform> = Platform.JDK_SET
  open fun getSeverityOverrides(module: Module): Map<String, Int>? = null
  open fun askForAttributeValue(attributeName: String, context: PsiElement): String? = null
  /** Whether or not the given file should be annotated on the fly in the editor */
  open fun canAnnotate(file: PsiFile, module: Module): Boolean {
    val fileType = file.fileType
    if (fileType === JavaFileType.INSTANCE
        || fileType === KotlinFileType.INSTANCE
        || fileType === PropertiesFileType.INSTANCE) {
      return true
    }
    if (fileType === XmlFileType.INSTANCE) {
      return true
    }
    else if (fileType === FileTypes.PLAIN_TEXT) {
      val name = file.name
      return name == FN_PROJECT_PROGUARD_FILE || name == FN_ANDROID_PROGUARD_FILE || name == OLD_PROGUARD_FILE
    }
    else if (file.isGradleFile()) {
      return true
    }
    return false
  }

  /** Whether or not the given project should be analyzed in batch mode */
  open fun canAnalyze(project: Project): Boolean {
    return true
  }

  // Creating projects
  /** Creates a set of projects for the given IntelliJ modules */
  open fun createProject(client: LintIdeClient,
                         files: List<VirtualFile>?,
                         vararg modules: Module): List<com.android.tools.lint.detector.api.Project> {
    return LintIdeProject.create(client, files, *modules)
  }

  open fun createProjectForSingleFile(client: LintIdeClient,
                                      file: VirtualFile?,
                                      module: Module): Pair<com.android.tools.lint.detector.api.Project, com.android.tools.lint.detector.api.Project> {
    return LintIdeProject.createForSingleFile(client, file, module)
  }

  /**
   * Creates a lint client
   */
  open fun createClient(project: Project, lintResult: LintResult = LintIgnoredResult()): LintIdeClient {
    return LintIdeClient(project, lintResult)
  }

  /**
   * Creates a lint client for batch inspections
   */
  open fun createBatchClient(lintResult: LintBatchResult): LintIdeClient {
    return LintIdeClient(lintResult.project, lintResult)
  }

  /**
   * Creates a lint client used for in-editor single file lint analysis (e.g. background checking while user is editing.)
   */
  open fun createEditorClient(lintResult: LintEditorResult): LintIdeClient {
    return LintIdeClient(lintResult.getModule().project, lintResult)
  }

  // Gradle
  open fun updateToLatest(module: Module, gc: GradleCoordinate) {
  }
  open fun recommendedAgpVersion(project: Project): AgpVersion? = null
  open fun shouldRecommendUpdateAgpToLatest(project: Project): Boolean = false
  open fun updateAgpToLatest(project: Project) {
  }
  open fun shouldOfferUpgradeAssistantForDeprecatedConfigurations(project: Project) : Boolean = false
  open fun updateDeprecatedConfigurations(project: Project, element: PsiElement) {
  }

  open fun resolveDynamic(project: Project, gc: GradleCoordinate): String? = null
  // Analytics
  open fun canRequestFeedback(): Boolean = false

  open fun requestFeedbackFix(issue: Issue): LocalQuickFix = error("Not supported")
  open fun requestFeedbackIntentionAction(issue: Issue): IntentionAction = error("Not supported")
  // Editor session
  open fun logSession(lint: LintDriver, lintResult: LintEditorResult) {}

  open fun logSession(lint: LintDriver, module: Module?, lintResult: LintBatchResult) {}

  // XML processing
  open fun ensureNamespaceImported(file: XmlFile, namespaceUri: String, suggestedPrefix: String?): String = ""
}

fun Module.getModuleDir(): File? {
  return File(moduleFilePath).parentFile
}

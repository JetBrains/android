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

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.DOT_KTS
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FN_ANDROID_PROGUARD_FILE
import com.android.SdkConstants.FN_PROJECT_PROGUARD_FILE
import com.android.SdkConstants.OLD_PROGUARD_FILE
import com.android.tools.lint.checks.DeprecatedSinceApiDetector
import com.android.tools.lint.checks.DeprecationDetector
import com.android.tools.lint.checks.GradleDetector
import com.android.tools.lint.checks.WrongIdDetector
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintRequest
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.TextFormat.HTML
import com.android.tools.lint.detector.api.TextFormat.RAW
import com.google.common.collect.Sets
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.CustomEditInspectionToolsSettingsAction
import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.util.IncorrectOperationException
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.plugins.gradle.config.GradleFileType
import org.toml.lang.psi.TomlFileType
import java.util.EnumSet
import javax.swing.Icon

class LintExternalAnnotator : ExternalAnnotator<LintEditorResult, LintEditorResult>() {

  companion object {
    const val LINK_PREFIX = "#lint/" // Should match the codeInsight.linkHandler prefix specified in lint-plugin.xml.
    const val INCLUDE_IDEA_SUPPRESS_ACTIONS = false

    init {
      LintClient.clientName = LintClient.CLIENT_STUDIO
    }

    fun getIssuesFromInspections(project: Project, context: PsiElement?): Set<Issue> {
      val fullRegistry = LintIdeIssueRegistry.get()
      val issueList = fullRegistry.issues
      val result = Sets.newHashSetWithExpectedSize<Issue>(issueList.size + 10)
      for (issue in issueList) {
        val inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(project, issue) ?: continue
        val key = HighlightDisplayKey.find(inspectionShortName) ?: continue
        val profile: InspectionProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val enabled = if (context != null) profile.isToolEnabled(key, context) else profile.isToolEnabled(key)
        if (!enabled) continue
        if (!issue.isEnabledByDefault()) {
          // If an issue is marked as not enabled by default, lint won't run it, even if it's in the set
          // of issues provided by an issue registry. Since in the IDE we're enforcing the enabled-state via
          // inspection profiles, mark the issue as enabled to allow users to turn on a lint check directly
          // via the inspections UI.
          issue.setEnabledByDefault(true)
        }
        result.add(issue)
      }
      return result
    }

    fun getHighlightLevelAndInspection(
      project: Project,
      issue: Issue,
      context: PsiElement
    ): Pair<AndroidLintInspectionBase, HighlightDisplayLevel>? {
      val inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(project, issue) ?: return null
      val key = HighlightDisplayKey.find(inspectionShortName) ?: return null
      val profile: InspectionProfile = InspectionProjectProfileManager.getInstance(context.project).currentProfile
      if (!profile.isToolEnabled(key, context)) {
        if (!issue.isEnabledByDefault()) {
          // Lint will skip issues (and not report them) for issues that have been disabled,
          // except for those issues that are explicitly enabled via Gradle. Therefore, if
          // we get this far, lint has found this issue to be explicitly enabled, so we let
          // that setting override a local enabled/disabled state in the IDE profile.
        } else {
          return null
        }
      }
      val inspection = profile.getUnwrappedTool(inspectionShortName, context) as AndroidLintInspectionBase? ?: return null
      val errorLevel = profile.getErrorLevel(key, context)
      return Pair(inspection, errorLevel)
    }
  }

  override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): LintEditorResult? {
    return collectInformation(file)
  }

  override fun collectInformation(file: PsiFile): LintEditorResult? {
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return null
    return collectInformation(file, module)
  }

  private fun isRelevant(file: PsiFile, module: Module): Boolean {
    return LintIdeSupport.get().canAnnotate(file, module)
  }

  private fun collectInformation(file: PsiFile, module: Module): LintEditorResult? {
    val vFile = file.virtualFile ?: return null
    if (!isRelevant(file, module)) {
      return null
    }
    val issues = getIssuesFromInspections(file.project, file)
    return LintEditorResult(module, vFile, file.text, issues)
  }

  override fun doAnnotate(lintResult: LintEditorResult): LintEditorResult {
    val startTime = System.currentTimeMillis()
    val client = LintIdeSupport.get().createEditorClient(lintResult)
    try {
      var scope: EnumSet<Scope>
      val mainFile = lintResult.mainFile
      val fileType = mainFile.fileType
      val name = mainFile.name
      if (fileType === XmlFileType.INSTANCE) {
        if (name == ANDROID_MANIFEST_XML) {
          scope = Scope.MANIFEST_SCOPE
        } else if (name.endsWith(DOT_XML)) {
          scope = Scope.RESOURCE_FILE_SCOPE
        } else {
          // Something else, like svg
          return lintResult
        }
      } else if (fileType === JavaFileType.INSTANCE || fileType === KotlinFileType.INSTANCE) {
        scope = Scope.JAVA_FILE_SCOPE
        if (name.endsWith(DOT_KTS)) {
          scope = EnumSet.of(Scope.GRADLE_FILE, Scope.JAVA_FILE)
        }
      } else if (name == OLD_PROGUARD_FILE || name == FN_PROJECT_PROGUARD_FILE || name == FN_ANDROID_PROGUARD_FILE) {
        scope = EnumSet.of(Scope.PROGUARD_FILE)
      } else if (GradleFileType.isGradleFile(mainFile)) {
        scope = Scope.GRADLE_SCOPE
      } else if (fileType === PropertiesFileType.INSTANCE) {
        scope = Scope.PROPERTY_SCOPE
      } else if (fileType === TomlFileType) {
        scope = Scope.TOML_SCOPE
      } else {
        // #collectionInformation above should have prevented this
        assert(false) { fileType }
        return lintResult
      }
      val project = lintResult.getModule().project
      if (project.isDisposed) {
        return lintResult
      }
      if (DumbService.isDumb(project)) {
        return lintResult // Lint cannot run without indices.
      }
      val files = listOf(mainFile)
      val request: LintRequest = LintIdeRequest(client, project, files, listOf(lintResult.getModule()), true /* incremental */)
      request.setScope(scope)
      val lint = client.createDriver(request)
      lint.analyze()
      lint.analysisStartTime = startTime
      LintIdeSupport.get().logSession(lint, lintResult)
    } finally {
      Disposer.dispose(client)
    }
    return lintResult
  }

  override fun apply(file: PsiFile, lintResult: LintEditorResult, holder: AnnotationHolder) {
    if (lintResult.isDirty) {
      return
    }
    val project = file.project
    if (DumbService.isDumb(project)) return
    val fixProviders = LintIdeQuickFixProvider.EP_NAME.extensions
    val ideSupport = LintIdeSupport.get()
    for (problemData in lintResult.problems) {
      val issue = problemData.issue
      val rawMessage = problemData.message
      val range = problemData.textRange
      val quickfixData = problemData.quickfixData
      if (range.startOffset == range.endOffset) {
        continue
      }

      var (inspection, displayLevel) = getHighlightLevelAndInspection(project, issue, file) ?: continue
      val key = HighlightDisplayKey.find(inspection.shortName) ?: continue
      val startElement = file.findElementAt(range.startOffset)
      val endElement = file.findElementAt(range.endOffset - 1)
      if (startElement == null || endElement == null || inspection.isSuppressedFor(startElement)) {
        continue
      }

      val configuredSeverity = problemData.configuredSeverity
      if (configuredSeverity != null) {
        val configuredLevel = AndroidLintInspectionBase.toHighlightDisplayLevel(configuredSeverity)
        if (configuredLevel != null) {
          displayLevel = configuredLevel
        }
      }
      val severity = displayLevel.severity
      val type: ProblemHighlightType =
        if (issue === DeprecationDetector.ISSUE ||
            issue === GradleDetector.DEPRECATED ||
            issue === GradleDetector.DEPRECATED_CONFIGURATION ||
            issue === DeprecatedSinceApiDetector.ISSUE) {
          ProblemHighlightType.LIKE_DEPRECATED
        } else if (issue === WrongIdDetector.UNKNOWN_ID || issue === WrongIdDetector.UNKNOWN_ID_LAYOUT) {
          ProblemHighlightType.ERROR // like unknown symbol
        } else if (severity === HighlightSeverity.ERROR) {
          // In recent versions of IntelliJ, HighlightInfo.convertSeverityToProblemHighlight
          // maps HighlightSeverity.ERROR to ProblemHighlightType.ERROR which is now documented
          // to be like ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, which gives the wrong
          // impression for most errors; see https://issuetracker.google.com/159532832
          ProblemHighlightType.GENERIC_ERROR
        } else {
          HighlightInfo.convertSeverityToProblemHighlight(severity)
        }

      // Long warning messages can freeze the IDE due to text layout overhead in Swing.
      // See https://issuetracker.google.com/178779561 for an example (should be a very rare case).
      val message = if (rawMessage.length <= 1000) rawMessage else rawMessage.take(1000) + "... [truncated]"

      // This description link is not displayed. It is parsed by IDEA to
      // populate the "Show Inspection Description" action.
      val descriptionLink = "<a href=\"" + LINK_PREFIX + issue.id + "\"></a>"
      val tooltip = XmlStringUtil.wrapInHtml(descriptionLink + RAW.convertTo(message, HTML))
      var builder = holder
        .newAnnotation(severity, message)
        .highlightType(type)
        .range(range)
        .tooltip(tooltip)
      val fixes = inspection.getAllFixes(startElement, endElement, message, quickfixData, fixProviders, issue)
      for (fix in fixes) {
        if (fix.isApplicable(startElement, endElement, AndroidQuickfixContexts.EditorContext.TYPE)) {
          val smartRange = fix.range
                           ?: SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, range)
          builder = builder.withFix(MyFixingIntention(fix, smartRange))
        }
      }
      for (intention in inspection.getIntentions(startElement, endElement)) {
        builder = builder.withFix(intention)
      }
      if (ideSupport.canRequestFeedback()) {
        builder = builder.withFix(ideSupport.requestFeedbackIntentionAction(issue))
      }
      val id = key.id
      builder = builder.withFix(SuppressLintIntentionAction(id, startElement))
      if (INCLUDE_IDEA_SUPPRESS_ACTIONS) {
        builder = builder.withFix(MyDisableInspectionFix(key))
        builder = builder.withFix(MyEditInspectionToolsSettingsAction(key, inspection))
      }
      if (INCLUDE_IDEA_SUPPRESS_ACTIONS) {
        val suppressActions = inspection.getBatchSuppressActions(startElement)
        for (action in suppressActions) {
          if (action.isAvailable(project, startElement)) {
            val descriptor = InspectionManager.getInstance(project).createProblemDescriptor(
              startElement, endElement, message, type, true, *LocalQuickFix.EMPTY_ARRAY
            )
            builder = builder.newLocalQuickFix(action, descriptor).key(key).registerFix()
          }
        }
      }
      builder.create()
    }
  }

  private class MyDisableInspectionFix(key: HighlightDisplayKey) : IntentionAction, Iconable {
    private val myDisableInspectionToolAction = DisableInspectionToolAction(key)

    override fun getText(): String {
      return "Disable inspection"
    }

    override fun getFamilyName(): String {
      return text
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
      return true
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
      myDisableInspectionToolAction.invoke(project, editor, file)
    }

    override fun startInWriteAction(): Boolean {
      return myDisableInspectionToolAction.startInWriteAction()
    }

    override fun getElementToMakeWritable(file: PsiFile): PsiElement? {
      return myDisableInspectionToolAction.getElementToMakeWritable(file)
    }

    override fun getIcon(@IconFlags flags: Int): Icon {
      return myDisableInspectionToolAction.getIcon(flags)
    }
  }

  class MyFixingIntention(
    @SafeFieldForPreview private val myQuickFix: LintIdeQuickFix,
    /** If non-null, the fix is targeted for a different file than the current one in the editor. */
    @SafeFieldForPreview private val myRange: SmartPsiFileRange
  ) : IntentionAction, HighPriorityAction {
    constructor(quickFix: LintIdeQuickFix, project: Project, file: PsiFile, range: TextRange)
      : this(quickFix, SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(file, range))

    override fun getText(): String {
      return myQuickFix.name
    }

    override fun getFamilyName(): String {
      return myQuickFix.familyName ?: LintBundle.message("android.lint.quickfixes.family")
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
      return true
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      file ?: return
      editor ?: return

      val context: AndroidQuickfixContexts.Context
      val targetFile: PsiFile

      if (file.isPhysical) {
        targetFile = myRange.containingFile ?: return
        context = AndroidQuickfixContexts.EditorContext.getInstance(editor, file)
      } else {
        if (file.name != myRange.containingFile?.name) {
          return
        }
        targetFile = file
        context = AndroidQuickfixContexts.EditorPreviewContext(editor, targetFile)
      }

      val textRange = myRange.range ?: return
      val start = targetFile.findElementAt(textRange.startOffset) ?: return
      val end = targetFile.findElementAt(textRange.endOffset - 1) ?: return
      myQuickFix.apply(start, end, context)
    }

    override fun startInWriteAction(): Boolean {
      return myQuickFix.startInWriteAction()
    }

    override fun toString(): String {
      return text
    }

    override fun getPriority(): PriorityAction.Priority {
      return myQuickFix.priority
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
      return myQuickFix.generatePreview(project, editor, file)
             ?: super.generatePreview(project, editor, file)
    }
  }

  private class MyEditInspectionToolsSettingsAction(key: HighlightDisplayKey, inspection: AndroidLintInspectionBase) :
    CustomEditInspectionToolsSettingsAction(key, Computable { "Edit '" + inspection.displayName + "' inspection settings" })
}
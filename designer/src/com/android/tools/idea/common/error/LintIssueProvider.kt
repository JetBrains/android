/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.error

import com.android.tools.idea.common.lint.LintAnnotationsModel
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.LintIdeQuickFix
import com.android.tools.idea.rendering.HtmlBuilderHelper
import com.android.tools.lint.detector.api.Option
import com.android.tools.lint.detector.api.TextFormat
import com.android.utils.HtmlBuilder
import com.google.common.collect.ImmutableCollection
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.BrowserHyperlinkListener
import java.util.Objects
import java.util.stream.Stream
import javax.swing.event.HyperlinkListener
import kotlin.properties.Delegates

class LintIssueProvider(_lintAnnotationsModel: LintAnnotationsModel) : IssueProvider() {

  var lintAnnotationsModel: LintAnnotationsModel by Delegates.observable(_lintAnnotationsModel) { _, _, _ -> notifyModified() }

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    for (error in lintAnnotationsModel.issues) {
      issueListBuilder.add(LintIssueWrapper(error))
    }
  }

  class LintIssueWrapper(private val issue: LintAnnotationsModel.IssueData) : Issue() {
    private fun createFullDescription(): String {
      val issue = issue.issue
      val headerFontColor = HtmlBuilderHelper.getHeaderFontColor()
      val builder = HtmlBuilder()

      builder.addHtml(TextFormat.RAW.convertTo(this.issue.message, TextFormat.HTML))
      builder.newline().newline()
      builder.addHtml(issue.getExplanation(TextFormat.HTML))
      builder.newline()
      val optionsHtml = Option.Companion.describe(issue.getOptions(), TextFormat.HTML)
      builder.addHtml(optionsHtml)
      builder.newline()
      builder.beginItalic()
      builder.addHtml("Issue id: ")
      builder.addHtml(issue.id)
      builder.endItalic()
      builder.newline()
      val moreInfo = issue.moreInfo
      val count = moreInfo.size
      if (count > 1) {
        builder.addHeading("More Info: ", headerFontColor)
        builder.beginList()
      }
      for (uri in moreInfo) {
        if (count > 1) {
          builder.listItem()
        }
        builder.addLink(uri, uri)
      }
      if (count > 1) {
        builder.endList()
      }
      builder.newline()

      val vendor = issue.vendor ?: issue.registry?.vendor
      vendor?.let {
        builder.addHtml(vendor.describe(TextFormat.HTML))
        builder.newline()
      }

      return builder.html
    }

    override val summary: String
      get() = issue.issue.getBriefDescription(TextFormat.RAW)

    override val description: String = createFullDescription()

    override val severity: HighlightSeverity = issue.level.severity

    override val source: IssueSource = issue.issueSource

    override val category: String = issue.issue.category.fullName

    override val hyperlinkListener: HyperlinkListener?
      get() = if (issue.issue.moreInfo.isEmpty()) super.hyperlinkListener else BrowserHyperlinkListener.INSTANCE

    override val fixes: Stream<Fix>
      get() {
        val inspection = issue.inspection
        val startElement = issue.startElementPointer.element ?: return emptyList<Fix>().stream()
        val endElement = issue.endElementPointer.element ?: return emptyList<Fix>().stream()
        val quickFixes = inspection.getQuickFixes(
          startElement, endElement,
          issue.message, issue.quickfixData
        )
        val intentions = inspection.getIntentions(startElement, endElement)
        return quickFixes.map { createQuickFixPair(it) }.plus(intentions.map { createQuickFixPair(it) }).stream()
      }

    override val suppresses: Stream<Suppress>
      get() {
        val suppressLint = issue.suppressLintQuickFix ?: return Stream.empty()
        val project = issue.component.model.project
        val suppress = Suppress("Suppress", suppressLint.name) {
          CommandProcessor.getInstance().executeCommand(project, {
            WriteAction.run<Throwable> {
              val startElement = issue.startElementPointer.element ?: return@run
              suppressLint.applyFix(startElement)
            }
          }, EXECUTE_SUPPRESSION + suppressLint.name, null
          )
        }
        return Stream.of(suppress)
      }

    private fun createQuickFixPair(fix: LintIdeQuickFix) = Fix("Fix", fix.name, createQuickFixRunnable(fix))

    private fun createQuickFixPair(fix: IntentionAction) = Fix("Fix", fix.text, createQuickFixRunnable(fix))

    override fun equals(other: Any?): Boolean {
      if (other !is LintIssueWrapper) {
        return false
      }
      return (super.equals(other)
              && other.issue.startElementPointer == this.issue.startElementPointer
              && other.issue.endElementPointer == this.issue.endElementPointer)
    }

    override fun hashCode(): Int {
      var res = super.hashCode()
      res += 31 * Objects.hash(issue.startElementPointer, issue.endElementPointer)
      return res
    }

    private fun createQuickFixRunnable(fix: LintIdeQuickFix): Runnable {
      return Runnable {
        val model = issue.component.model
        val file = issue.startElementPointer.containingFile ?: return@Runnable
        val editor = PsiEditorUtil.findEditor(file)
        if (editor != null) {
          val project = model.project
          CommandProcessor.getInstance().executeCommand(
            project,
            {
              WriteAction.run<Throwable> {
                val startElement = issue.startElementPointer.element ?: return@run
                val endElement = issue.endElementPointer.element ?: return@run
                fix.apply(startElement, endElement, AndroidQuickfixContexts.BatchContext.getInstance())
              }
            },
              EXECUTE_FIX + fix.name, null
          )
        }
      }
    }

    private fun createQuickFixRunnable(fix: IntentionAction): Runnable {
      return Runnable {
        val model = issue.component.model
        val file = issue.startElementPointer.containingFile ?: return@Runnable
        val editor = PsiEditorUtil.findEditor(file)
        if (editor != null) {
          val project = model.project
          CommandProcessor.getInstance().executeCommand(
              project,
              { fix.invoke(project, editor, model.file) },
              EXECUTE_FIX + fix.familyName, null
          )
        }
      }
    }
  }
}
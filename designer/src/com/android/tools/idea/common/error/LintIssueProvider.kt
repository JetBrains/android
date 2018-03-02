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
import com.android.tools.idea.rendering.HtmlBuilderHelper
import com.android.tools.lint.detector.api.TextFormat
import com.android.utils.HtmlBuilder
import com.google.common.collect.ImmutableCollection
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.util.PsiEditorUtil
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts
import java.util.*
import java.util.stream.Stream

class LintIssueProvider(var lintAnnotationsModel: LintAnnotationsModel) : IssueProvider {

  override fun collectIssues(issueListBuilder: ImmutableCollection.Builder<Issue>) {
    for (error in lintAnnotationsModel.issues) {
      issueListBuilder.add(LintIssueWrapper(error))
    }
  }

  class LintIssueWrapper(private val myIssue: LintAnnotationsModel.IssueData) : Issue() {

    private var myDescription: String = createFullDescription()

    private fun createFullDescription(): String {
      val issue = myIssue.issue
      val headerFontColor = HtmlBuilderHelper.getHeaderFontColor()
      val builder = HtmlBuilder()

      builder.addHtml(TextFormat.RAW.convertTo(myIssue.message, TextFormat.HTML))
      builder.newline().newline()
      builder.addHtml(issue.getExplanation(TextFormat.HTML))
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

      return builder.html
    }

    override fun getSummary(): String {
      return myIssue.issue.getBriefDescription(TextFormat.RAW)
    }

    override fun getDescription() = myDescription

    override fun getSeverity() = myIssue.level.severity

    override fun getSource() = myIssue.component

    override fun getCategory() = myIssue.issue.category.fullName

    override fun getFixes(): Stream<Fix> {
      val inspection = myIssue.inspection
      val quickFixes = inspection.getQuickFixes(
          myIssue.startElement, myIssue.endElement,
          myIssue.message, myIssue.quickfixData
      )
      val intentions = inspection.getIntentions(myIssue.startElement, myIssue.endElement)
      return quickFixes.map { createQuickFixPair(it) }.plus(intentions.map {createQuickFixPair(it)}).stream()
    }

    private fun createQuickFixPair(fix: AndroidLintQuickFix) = Fix(fix.name, createQuickFixRunnable(fix))

    private fun createQuickFixPair(fix: IntentionAction) = Fix(fix.text, createQuickFixRunnable(fix))

    override fun equals(other: Any?): Boolean {
      if (other !is LintIssueWrapper) {
        return false
      }
      return (super.equals(other)
          && other.myIssue.startElement == this.myIssue.startElement
          && other.myIssue.endElement == this.myIssue.endElement)
    }

    override fun hashCode(): Int {
      var res = super.hashCode()
      res += 31 * Objects.hash(myIssue.startElement, myIssue.endElement)
      return res
    }

    private fun createQuickFixRunnable(fix: AndroidLintQuickFix): Runnable {
      return Runnable {
        val model = myIssue.component.model
        val editor = PsiEditorUtil.Service.getInstance().findEditorByPsiElement(myIssue.startElement)
        if (editor != null) {
          val project = model.project
          CommandProcessor.getInstance().executeCommand(
              project,
              { fix.apply(myIssue.startElement, myIssue.endElement, AndroidQuickfixContexts.BatchContext.getInstance()) },
              EXECUTE_FIX + fix.name, null
          )
        }
      }
    }

    private fun createQuickFixRunnable(fix: IntentionAction): Runnable {
      return Runnable {
        val model = myIssue.component.model
        val editor = PsiEditorUtil.Service.getInstance().findEditorByPsiElement(myIssue.startElement)
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
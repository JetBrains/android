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
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassSpecificationBody
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8JavaRule
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8Rule
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8RuleWithClassSpecification
import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentFix
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import java.util.regex.Pattern

/**
 * Adds quick fixes e.g [SuppressByProguardR8CommentFix] for suppressing ProguardR8***Inspection.
 */
class ProguardR8InspectionSuppressor : InspectionSuppressor {
  private val suppressPattern = Pattern.compile("#${SuppressionUtil.COMMON_SUPPRESS_REGEXP}")

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
    if (element == null) return SuppressQuickFix.EMPTY_ARRAY
    return arrayOf(SuppressByProguardR8CommentFix(element, toolId))
  }

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    val container = SuppressByProguardR8CommentFix(element, toolId).getContainer(element) ?: return false
    return getCommentsBefore(container).any { isSuppressedInComment(it.text, toolId) }
  }

  private fun isSuppressedInComment(commentText: String, toolId: String): Boolean {
    val matcher = suppressPattern.matcher(commentText)
    return matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)
  }

  private fun getCommentsBefore(element: PsiElement) = generateSequence(
    PsiTreeUtil.getPrevSiblingOfType<PsiComment>(element, PsiComment::class.java)) {
    PsiTreeUtil.getPrevSiblingOfType<PsiComment>(it, PsiComment::class.java)
  }
}

/**
 * Suppress ProguardR8 inspections by comment.
 */
private class SuppressByProguardR8CommentFix(element: PsiElement, toolId: String) : SuppressByCommentFix(toolId, element::class.java) {
  override fun getContainer(context: PsiElement?): PsiElement? {
    if (context == null) return null
    //If we are inside class specification body, comment should be above ProguardR8JavaRule.
    if (context.parentOfType<ProguardR8ClassSpecificationBody>() != null) {
      return context.parentOfType<ProguardR8JavaRule>()
    }
    //If we are not inside class specification body, comment should be above ProguardR8Rule/ProguardR8RuleWithClassSpecification.
    return context.parentOfType(ProguardR8Rule::class, ProguardR8RuleWithClassSpecification::class)
  }

  override fun createSuppression(project: Project, element: PsiElement, container: PsiElement) {
    //To safe indent before container, hopefully we will get rid of this after http://b/145126304
    val oldIndent = if (container.prevSibling is PsiWhiteSpace) getIndent(container.prevSibling as PsiWhiteSpace) else ""
    //Add suppression comment before container.
    super.createSuppression(project, element, container)
    val parserFacade = PsiParserFacade.SERVICE.getInstance(project)
    //Add new line and old indent after suppression comment.
    val newLine = parserFacade.createWhiteSpaceFromText("\n" + oldIndent)
    container.parent.addAfter(newLine, container.prevSibling)
  }

  private fun getIndent(element: PsiWhiteSpace): String {
    val text = element.text
    val indexOfLastNewLine = text.indexOfLast { it == '\n' }
    return text.substring(indexOfLastNewLine + 1)
  }
}

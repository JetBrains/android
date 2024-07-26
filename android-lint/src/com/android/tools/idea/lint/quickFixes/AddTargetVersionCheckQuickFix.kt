/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder
import com.intellij.codeInspection.JavaSuppressionUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinIfSurrounder
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtWhenEntry

/** Fix which surrounds an API warning with a version check */
class AddTargetVersionCheckQuickFix(
  project: Project,
  element: PsiElement,
  private val api: Int,
  private val sdkId: Int,
  private val minSdk: ApiConstraint,
) : PsiBasedModCommandAction<PsiElement>(element) {

  private val name: String =
    if (sdkId == ANDROID_SDK_ID)
      "Surround with if (VERSION.SDK_INT >= ${
        getVersionField(api, false).let { if (it[0].isDigit()) it else "VERSION_CODES.$it" }
      }) { ... }"
    else
      "Surround with if (SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, false)})) >= $api) { ... }"

  override fun getFamilyName(): String = "AddTargetVersionCheckQuickFix"

  override fun getPresentation(context: ActionContext, element: PsiElement): Presentation? {
    // Don't offer this unless we're in an Android module
    if (AndroidFacet.getInstance(element) == null) {
      return null
    }

    return when (element.language) {
      JavaLanguage.INSTANCE -> {
        PsiTreeUtil.getParentOfType(element, PsiExpression::class.java, false)?.let {
          Presentation.of(name)
        }
      }
      KotlinLanguage.INSTANCE -> {
        getKotlinTargetExpression(element)?.let { Presentation.of(name) }
      }
      else -> null
    }
  }

  override fun perform(context: ActionContext, element: PsiElement): ModCommand =
    when (element.language) {
      JavaLanguage.INSTANCE -> handleJava(element, context)
      KotlinLanguage.INSTANCE -> handleKotlin(element, context)
      else -> ModCommand.nop()
    }

  private fun handleKotlin(element: PsiElement, context: ActionContext): ModCommand {
    val targetExpression = getKotlinTargetExpression(element) ?: return ModCommand.nop()
    val project = targetExpression.project

    val conditionText =
      if (sdkId == ANDROID_SDK_ID)
        "android.os.Build.VERSION.SDK_INT >= ${getVersionField(api, true)}"
      else {
        "${getExtensionCheckPrefix()}android.os.ext.SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, true)}) >= $api"
      }

    val todoText =
      if (sdkId == ANDROID_SDK_ID) "\"VERSION.SDK_INT < ${getVersionField(api, false)}\""
      else
        "\"SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, false)}) < $api\""

    return getKotlinSurrounder(targetExpression, conditionText, todoText)
      .surroundElements(context, arrayOf(targetExpression))
  }

  private fun getExtensionCheckPrefix(): String {
    return if (
      minSdk != ApiConstraint.UNKNOWN &&
        minSdk.isAtLeast(ApiConstraint.get(AndroidVersion.VersionCodes.R, ANDROID_SDK_ID))
    )
      ""
    else "android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && "
  }

  private fun handleJava(element: PsiElement, context: ActionContext): ModCommand {
    val project = element.project
    val expression =
      PsiTreeUtil.getParentOfType(element, PsiExpression::class.java, false)
        ?: return ModCommand.nop()
    val anchorStatement =
      PsiTreeUtil.getParentOfType(expression, PsiStatement::class.java) ?: return ModCommand.nop()

    var elements = arrayOf<PsiElement>(anchorStatement)
    val prev = PsiTreeUtil.skipWhitespacesBackward(anchorStatement)
    if (prev is PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = arrayOf(prev, anchorStatement)
    }

    val newText =
      if (sdkId == ANDROID_SDK_ID)
        "android.os.Build.VERSION.SDK_INT >= " + getVersionField(api, true)
      else
        "${getExtensionCheckPrefix()}android.os.ext.SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, true)}) >= $api"

    return ModCommand.psiUpdate(context) { updater ->
      val statement =
        JavaWithIfSurrounder()
          .surroundStatements(
            project,
            updater.getWritable(anchorStatement.parent),
            elements.map { updater.getWritable(it) }.toTypedArray(),
            newText,
          )

      JavaCodeStyleManager.getInstance(project).shortenClassReferences(statement)
      updater.moveCaretTo(statement.lParenth?.endOffset ?: statement.textOffset)
    }
  }

  private fun getKotlinTargetExpression(element: PsiElement): KtElement? {
    var current: KtElement? = PsiTreeUtil.getParentOfType(element, KtExpression::class.java)
    while (current != null) {
      val parent = current.parent ?: return current
      if (parent.isInvalidTargetExpression()) {
        break
      }
      current = PsiTreeUtil.getParentOfType(current, KtExpression::class.java, true)
    }
    return current
  }

  private fun getKotlinSurrounder(
    element: KtElement,
    conditionText: String,
    todoText: String,
  ): KotlinIfSurrounder {
    val used = analyze(element) { (element as? KtExpression)?.isUsedAsExpression ?: false }

    return object : KotlinIfSurrounder() {
      override fun getCodeTemplate(): String =
        if (used) "if (a) { \n} else {\nTODO($todoText)\n}" else super.getCodeTemplate()

      override fun surroundStatements(
        context: ActionContext,
        container: PsiElement,
        statements: Array<out PsiElement>,
        updater: ModPsiUpdater,
      ) {
        super.surroundStatements(context, container, statements, updater)

        // [KotlinIfSurrounderBase] removes the condition and leaves the caret in its place,
        // so we have to continue editing manually for now...
        val file = container.containingFile
        val document = file.fileDocument
        PsiDocumentManager.getInstance(container.project)
          .doPostponedOperationsAndUnblockDocument(document)
        document.insertString(updater.caretOffset, conditionText)
        PsiDocumentManager.getInstance(container.project).commitDocument(document)
        ShortenReferencesFacility.getInstance()
          .shorten(file as KtFile, TextRange.from(updater.caretOffset, conditionText.length))
      }
    }
  }

  private fun PsiElement.isInvalidTargetExpression(): Boolean {
    return this is KtBlockExpression ||
      this is KtContainerNode ||
      this is KtWhenEntry ||
      this is KtFunction ||
      this is KtPropertyAccessor ||
      this is KtProperty ||
      this is KtReturnExpression ||
      this is KtDestructuringDeclaration ||
      this is KtClassInitializer
  }

  companion object {
    fun getVersionField(api: Int, fullyQualified: Boolean): String =
      ExtensionSdk.getAndroidVersionField(api, fullyQualified)

    fun getSdkExtensionField(project: Project, sdkId: Int, fullyQualified: Boolean): String {
      val apiLookup = LintIdeClient.getApiLookup(project)
      if (apiLookup != null) {
        return apiLookup.getSdkExtensionField(sdkId, fullyQualified)
      }
      return ExtensionSdk.getSdkExtensionField(sdkId, fullyQualified)
    }
  }
}

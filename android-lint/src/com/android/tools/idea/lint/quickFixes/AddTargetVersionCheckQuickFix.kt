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
import com.android.tools.idea.lint.common.AndroidQuickfixContexts
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.lint.common.LintIdeClient
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils.prepareElementForWrite
import com.intellij.codeInspection.JavaSuppressionUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiStatement
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement.KotlinIfSurrounder
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.ShortenReferences
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
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/** Fix which surrounds an API warning with a version check  */
class AddTargetVersionCheckQuickFix(
  project: Project,
  private val api: Int,
  private val sdkId: Int,
  private val minSdk: ApiConstraint
) : DefaultLintQuickFix(
  if (sdkId == ANDROID_SDK_ID)
    "Surround with if (VERSION.SDK_INT >= ${getVersionField(api, false).let { if (it[0].isDigit()) it else "VERSION_CODES.$it" }}) { ... }"
  else
    "Surround with if (SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, false)})) >= $api) { ... }"
) {

  override fun isApplicable(startElement: PsiElement,
                            endElement: PsiElement,
                            contextType: AndroidQuickfixContexts.ContextType): Boolean {
    // Don't offer this unless we're in an Android module
    if (AndroidFacet.getInstance(endElement) == null) {
      return false
    }

    return when (startElement.language) {
      JavaLanguage.INSTANCE -> {
        val expression = PsiTreeUtil.getParentOfType(startElement, PsiExpression::class.java, false)
        expression != null
      }
      KotlinLanguage.INSTANCE -> {
        getKotlinTargetExpression(startElement) != null
      }
      else -> false
    }
  }

  override fun apply(startElement: PsiElement, endElement: PsiElement, context: AndroidQuickfixContexts.Context) {
    if (!prepareElementForWrite(startElement)) {
      return
    }
    when (startElement.language) {
      JavaLanguage.INSTANCE -> handleJava(startElement, context)
      KotlinLanguage.INSTANCE -> handleKotlin(startElement, context)
    }
  }

  private fun handleKotlin(element: PsiElement, context: AndroidQuickfixContexts.Context) {
    val targetExpression = getKotlinTargetExpression(element) ?: return
    val project = targetExpression.project
    targetExpression.findExistingEditor()
    val editor = context.getEditor(targetExpression.containingFile) ?: return
    val file = targetExpression.containingFile
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = editor.document
    if (!prepareElementForWrite(file)) {
      return
    }

    val surrounder =
      if (sdkId == ANDROID_SDK_ID) {
        getKotlinSurrounder(targetExpression, "\"VERSION.SDK_INT < ${getVersionField(api, false)}\"")
      } else {
        getKotlinSurrounder(targetExpression, "\"SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, false)}) < $api\"")
      }
    val conditionRange = surrounder.surroundElements(project, editor, arrayOf(targetExpression)) ?: return
    val conditionText = if (sdkId == ANDROID_SDK_ID)
      "android.os.Build.VERSION.SDK_INT >= ${getVersionField(api, true)}"
    else {
      "${getExtensionCheckPrefix()}android.os.ext.SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, true)}) >= $api"
    }

    document.replaceString(conditionRange.startOffset, conditionRange.endOffset, conditionText)
    documentManager.commitDocument(document)

    ShortenReferences.DEFAULT.process(documentManager.getPsiFile(document) as KtFile,
                                      conditionRange.startOffset,
                                      conditionRange.startOffset + conditionText.length)
  }

  private fun getExtensionCheckPrefix(): String {
    return if (minSdk != ApiConstraint.UNKNOWN && minSdk.isAtLeast(ApiConstraint.get(AndroidVersion.VersionCodes.R, ANDROID_SDK_ID))) ""
    else "android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && "
  }

  private fun handleJava(element: PsiElement, context: AndroidQuickfixContexts.Context) {
    val expression = PsiTreeUtil.getParentOfType(element, PsiExpression::class.java, false) ?: return
    val editor = context.getEditor(expression.containingFile) ?: return
    val anchorStatement = PsiTreeUtil.getParentOfType(expression, PsiStatement::class.java) ?: return
    val project = expression.project
    val document = editor.document

    val owner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner::class.java, false)
    var elements = arrayOf<PsiElement>(anchorStatement)
    val prev = PsiTreeUtil.skipWhitespacesBackward(anchorStatement)
    if (prev is PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = arrayOf(prev, anchorStatement)
    }
    try {
      val textRange = JavaWithIfSurrounder().surroundElements(project, editor, elements) ?: return
      val newText =
        if (sdkId == ANDROID_SDK_ID)
          "android.os.Build.VERSION.SDK_INT >= " + getVersionField(api, true)
      else
          "${getExtensionCheckPrefix()}android.os.ext.SdkExtensions.getExtensionVersion(${getSdkExtensionField(project, sdkId, true)}) >= $api"
      document.replaceString(textRange.startOffset, textRange.endOffset, newText)
      val documentManager = PsiDocumentManager.getInstance(project)
      documentManager.commitDocument(document)

      editor.caretModel.moveToOffset(textRange.endOffset + newText.length)
      editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)

      if (owner != null && owner.isValid &&
          // Unit tests: "JavaDummyHolder" doesn't work
          !ApplicationManager.getApplication().isUnitTestMode) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(owner)
      }
    }
    catch (e: IncorrectOperationException) {
      Logger.getInstance(AddTargetVersionCheckQuickFix::class.java).error(e)
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

  private fun getKotlinSurrounder(element: KtElement, todoText: String?): KotlinIfSurrounder {
    val used = element.analyze(BodyResolveMode.PARTIAL)[BindingContext.USED_AS_EXPRESSION, element] ?: false
    return if (used) {
      object : KotlinIfSurrounder() {
        override fun getCodeTemplate(): String = "if (a) { \n} else {\nTODO(${todoText ?: ""})\n}"
      }
    }
    else {
      KotlinIfSurrounder()
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
    fun getVersionField(api: Int, fullyQualified: Boolean): String = ExtensionSdk.getAndroidVersionField(api, fullyQualified)
    fun getSdkExtensionField(project: Project, sdkId: Int, fullyQualified: Boolean): String {
      val apiLookup = LintIdeClient.getApiLookup(project)
      if (apiLookup != null) {
        return apiLookup.getSdkExtensionField(sdkId, fullyQualified)
      }
      return ExtensionSdk.getSdkExtensionField(sdkId, fullyQualified)
    }
  }
}

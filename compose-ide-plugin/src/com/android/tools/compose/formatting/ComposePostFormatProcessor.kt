/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose.formatting

import com.android.tools.compose.isComposeEnabled
import com.android.tools.compose.isModifierChainLongerThanTwo
import com.android.tools.compose.settings.ComposeCustomCodeStyleSettings
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Runs after explicit code formatting invocation and for Modifier(androidx.compose.ui.Modifier)
 * chain that is two modifiers or longer, splits it in one modifier per line.
 */
class ComposePostFormatProcessor : PostFormatProcessor {

  private fun isAvailable(psiElement: PsiElement, settings: CodeStyleSettings): Boolean {
    return psiElement.containingFile is KtFile &&
      isComposeEnabled(psiElement) &&
      !DumbService.isDumb(psiElement.project) &&
      settings
        .getCustomSettings(ComposeCustomCodeStyleSettings::class.java)
        .USE_CUSTOM_FORMATTING_FOR_MODIFIERS
  }

  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    return if (isAvailable(source, settings)) ComposeModifierProcessor(settings).process(source)
    else source
  }

  override fun processText(
    source: PsiFile,
    rangeToReformat: TextRange,
    settings: CodeStyleSettings,
  ): TextRange {
    return if (isAvailable(source, settings))
      ComposeModifierProcessor(settings).processText(source, rangeToReformat)
    else rangeToReformat
  }
}

class ComposeModifierProcessor(private val settings: CodeStyleSettings) : KtTreeVisitorVoid() {
  private val myPostProcessor = PostFormatProcessorHelper(settings.kotlinCommonSettings)

  private fun updateResultRange(oldTextLength: Int, newTextLength: Int) {
    myPostProcessor.updateResultRange(oldTextLength, newTextLength)
  }

  override fun visitKtElement(element: KtElement) {
    super.visitElement(element)
    if (element.isPhysical && isModifierChainThatNeedToBeWrapped(element)) {
      val oldTextLength: Int = element.textLength
      wrapModifierChain(element as KtDotQualifiedExpression, settings)
      updateResultRange(oldTextLength, element.textLength)
    }
  }

  fun process(formatted: PsiElement): PsiElement {
    formatted.accept(this)
    return formatted
  }

  fun processText(source: PsiFile, rangeToReformat: TextRange): TextRange {
    myPostProcessor.resultTextRange = rangeToReformat
    source.accept(this)
    return myPostProcessor.resultTextRange
  }
}

/**
 * Returns true if it's Modifier(androidx.compose.ui.Modifier) chain that is two modifiers or
 * longer.
 *
 * Note that this function calls [isModifierChainLongerThanTwo] that potentially uses the analysis
 * API for K2. Since reformat can be used by template on write action, we need to wrap it with
 * [allowAnalysisFromWriteAction].
 *
 * TODO(310045274): We have to avoid analysis API use on write action eventually. Double check if we
 *   can avoid analysis here and drop [allowAnalysisFromWriteAction] (as explained above, reformat
 *   will run after final PSI is determined by the template, so it looks impossible to drop analysis
 *   here though).
 */
@OptIn(KaAllowAnalysisFromWriteAction::class)
private fun isModifierChainThatNeedToBeWrapped(element: KtElement): Boolean {
  // Take very top KtDotQualifiedExpression, e.g for `Modifier.adjust1().adjust2()` take whole
  // expression, not only `Modifier.adjust1()`.
  return element is KtDotQualifiedExpression &&
    element.parent !is KtDotQualifiedExpression &&
         @Suppress("OPT_IN_USAGE_ERROR") allowAnalysisFromWriteAction { isModifierChainLongerThanTwo(element) }
}

/** Splits KtDotQualifiedExpression it one call per line. */
internal fun wrapModifierChain(element: KtDotQualifiedExpression, settings: CodeStyleSettings) {
  CodeStyle.runWithLocalSettings(element.project, settings) { tempSettings: CodeStyleSettings ->
    tempSettings.kotlinCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    tempSettings.kotlinCommonSettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
    CodeFormatterFacade(tempSettings, element.language).processElement(element.node)
  }
}

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

import com.android.tools.compose.ComposeLibraryNamespace
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.js.translate.callTranslator.getReturnType
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * Runs after explicit code formatting invocation and for Modifier(androidx.compose.ui.Modifier) chain that is two modifiers or longer,
 * splits it in one modifier per line.
 */
class ComposePostFormatProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    if (source.containingFile !is KtFile) return source
    return ComposeModifierProcessor(settings).process(source)
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (source !is KtFile) return rangeToReformat
    return ComposeModifierProcessor(settings).processText(source, rangeToReformat)
  }
}

class ComposeModifierProcessor(private val settings: CodeStyleSettings) : KtTreeVisitorVoid() {
  private val myPostProcessor = PostFormatProcessorHelper(settings.kotlinCommonSettings)

  override fun visitKtElement(element: KtElement) {
    super.visitElement(element)
    if (isModifierChainThatNeedToBeWrapped(element)) {
      wrapModifierChain(element as KtDotQualifiedExpression, settings)
    }
  }

  fun process(formatted: PsiElement): PsiElement {
    formatted.accept(this)
    return formatted
  }

  fun processText(
    source: PsiFile,
    rangeToReformat: TextRange
  ): TextRange {
    myPostProcessor.resultTextRange = rangeToReformat
    source.accept(this)
    return myPostProcessor.resultTextRange
  }
}

/**
 * Returns true if it's Modifier(androidx.compose.ui.Modifier) chain that is two modifiers or longer.
 */
internal fun isModifierChainThatNeedToBeWrapped(element: KtElement): Boolean {
  // Take very top KtDotQualifiedExpression, e.g for `Modifier.adjust1().adjust2()` take whole expression, not only `Modifier.adjust1()`.
  if (element is KtDotQualifiedExpression && element.parent !is KtDotQualifiedExpression) {
    // Take only long chain (more than two expressions), e.g take `Modifier.adjust1().adjust2()`,don't take `Modifier.adjust1()`
    if (element.getChildrenOfType<KtDotQualifiedExpression>().isNotEmpty()) {
      val fqName = element.resolveToCall(BodyResolveMode.PARTIAL)?.getReturnType()?.fqName?.asString()
      if (fqName == ComposeLibraryNamespace.ANDROIDX_COMPOSE.composeModifierClassName) {
        return true
      }
    }
  }
  return false
}

/**
 * Splits KtDotQualifiedExpression it one call per line.
 */
internal fun wrapModifierChain(element: KtDotQualifiedExpression, settings: CodeStyleSettings) {
  CodeStyle.doWithTemporarySettings(
    element.project,
    settings
  ) { tempSettings: CodeStyleSettings ->
    tempSettings.kotlinCommonSettings.METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    tempSettings.kotlinCommonSettings.WRAP_FIRST_METHOD_IN_CALL_CHAIN = true
    CodeFormatterFacade(tempSettings, element.containingFile.language).processElement(element.node)
  }
}
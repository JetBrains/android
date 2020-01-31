/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl.findUsages

import com.android.tools.idea.lang.aidl.lexer.AidlLexer
import com.android.tools.idea.lang.aidl.lexer.AidlTokenTypeSets
import com.android.tools.idea.lang.aidl.psi.AidlDeclaration
import com.android.tools.idea.lang.aidl.psi.AidlDeclarationName
import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

/**
 * Enable find usage action on AIDL symbols.
 */
class AidlFindUsageProvider : FindUsagesProvider {
  override fun canFindUsagesFor(psiElement: PsiElement) = psiElement is AidlDeclaration

  override fun getHelpId(psiElement: PsiElement) = HelpID.FIND_OTHER_USAGES

  override fun getType(element: PsiElement)= "AIDL Component"

  override fun getDescriptiveName(element: PsiElement): String {
    return when (element) {
      is AidlDeclarationName -> element.nameIdentifier?.text ?: element.text
      else -> ""
    }
  }

  override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)

  override fun getWordsScanner(): WordsScanner? {
    return DefaultWordsScanner(AidlLexer(), AidlTokenTypeSets.IDENTIFIERS, AidlTokenTypeSets.COMMENTS, TokenSet.EMPTY)
  }
}

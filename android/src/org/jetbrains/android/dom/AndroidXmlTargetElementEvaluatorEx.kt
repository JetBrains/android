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
package org.jetbrains.android.dom

import com.android.tools.idea.util.androidFacet
import com.intellij.codeInsight.TargetElementEvaluatorEx
import com.intellij.codeInsight.XmlTargetElementEvaluator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Extends the functionality of [XmlTargetElementEvaluator].
 *
 * This is necessary because Android resource references in XML start with the '@' symbol, which is
 * not included in the default implementation for Java
 * identifiers, @see [Character.isJavaIdentifierPart], but remains part of the resource reference.
 */
class AndroidXmlTargetElementEvaluatorEx : XmlTargetElementEvaluator(), TargetElementEvaluatorEx {
  override fun isIdentifierPart(file: PsiFile, text: CharSequence, offset: Int): Boolean {
    val character = text[offset]
    return if (file.androidFacet != null) {
      Character.isJavaIdentifierPart(character) || character == '@'
    } else {
      Character.isJavaIdentifierPart(character)
    }
  }

  override fun includeSelfInGotoImplementation(element: PsiElement): Boolean {
    return false
  }
}

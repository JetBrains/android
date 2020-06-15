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

import com.android.tools.idea.lang.proguardR8.psi.ProguardR8ClassMemberName
import com.android.tools.idea.lang.proguardR8.psi.ProguardR8QualifiedName
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType

/**
 * Annotates java key words with ProguardR8TextAttributes.KEYWORD attributes.
 *
 * We can't highlight java keywords by ProguardR8SyntaxHighlighter because it's not enough to know just token type. ProguardR8Annotator
 * analyzes that java keyword token does't belong to class member/package/class name and only in such a case add attributes.
 */
class ProguardR8Annotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is LeafPsiElement &&
        (JAVA_KEY_WORDS.contains(element.elementType) || JAVA_PRIMITIVE.contains(element.elementType)) &&
        !isPartOfName(element)
    ) {
      val annotation = holder.createInfoAnnotation(element.textRange, null)
      annotation.enforcedTextAttributes = ProguardR8TextAttributes.KEYWORD.key.defaultAttributes
    }
  }

  private fun isPartOfName(element: PsiElement): Boolean {
    return element.parentOfType<ProguardR8QualifiedName>() != null ||
           element.parentOfType<ProguardR8ClassMemberName>() != null
  }
}
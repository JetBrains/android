/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder
import com.intellij.lang.CodeDocumentationAwareCommenterEx
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType


class DeclarativeCommenter : CodeDocumentationAwareCommenterEx {
  override fun getLineCommentPrefix(): String = "//"
  override fun getBlockCommentPrefix(): String = "/*"
  override fun getBlockCommentSuffix(): String = "*/"
  override fun getCommentedBlockCommentPrefix(): String? = null
  override fun getCommentedBlockCommentSuffix(): String? = null
  override fun getLineCommentTokenType(): IElementType = DeclarativeElementTypeHolder.LINE_COMMENT
  override fun getBlockCommentTokenType(): IElementType = DeclarativeElementTypeHolder.BLOCK_COMMENT
  override fun getDocumentationCommentTokenType(): IElementType? = null
  override fun getDocumentationCommentPrefix(): String? = null
  override fun getDocumentationCommentLinePrefix(): String? = null
  override fun getDocumentationCommentSuffix(): String? = null
  override fun isDocumentationComment(element: PsiComment): Boolean = false
  override fun isDocumentationCommentText(element: PsiElement): Boolean = false
}

/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Language;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/** Defines syntax for commenting code within C-like languages. */
public final class CLanguageCommenter implements CodeDocumentationAwareCommenter {

  /** Returns the IElementType representing the line comment token for C. */
  @Nullable
  @Override
  public IElementType getLineCommentTokenType() {
    return new IElementType("EOL_COMMENT", Language.ANY);
  }

  /** Returns the IElementType representing the block comment token for C. */
  @Nullable
  @Override
  public IElementType getBlockCommentTokenType() {
    return new IElementType("BLOCK_COMMENT", Language.ANY);
  }

  /** Returns the IElementType representing the documentation comment token for C. */
  @Nullable
  @Override
  public IElementType getDocumentationCommentTokenType() {
    return null;
  }

  /** Returns the String representing the documentation comment prefix token for C. */
  @Nullable
  @Override
  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  /** Returns the String representing the documentation comment line prefix token for C. */
  @Nullable
  @Override
  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  /** Returns the String representing the documentation comment suffix token for C. */
  @Nullable
  @Override
  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  /** Returns if the {@link PsiComment} is the block comment token type for C. */
  @Override
  public boolean isDocumentationComment(PsiComment psiComment) {
    return psiComment.getTokenType() == getBlockCommentTokenType();
  }

  /** Returns the String representing the comment line prefix token for C. */
  @Nullable
  @Override
  public String getLineCommentPrefix() {
    return "//";
  }

  /** Returns the String representing the comment block prefix token for C. */
  @Nullable
  @Override
  public String getBlockCommentPrefix() {
    return "/*";
  }

  /** Returns the String representing the comment block suffix token for C. */
  @Nullable
  @Override
  public String getBlockCommentSuffix() {
    return "*/";
  }

  /** Returns null for the commented block comment prefix token for C. */
  @Nullable
  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  /** Returns null for the commented block comment suffix token for C. */
  @Nullable
  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }
}

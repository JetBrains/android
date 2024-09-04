/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.idea.blaze.base.lang.buildfile.psi.Argument.Keyword;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.AttributeSpecificStringLiteralReferenceProvider;
import com.google.idea.blaze.base.lang.buildfile.references.ExternalWorkspaceReferenceFragment;
import com.google.idea.blaze.base.lang.buildfile.references.LabelReference;
import com.google.idea.blaze.base.lang.buildfile.references.LoadedSymbolReference;
import com.google.idea.blaze.base.lang.buildfile.references.PackageReferenceFragment;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import javax.annotation.Nullable;

/** PSI node for string literal expressions */
public class StringLiteral extends BuildElementImpl implements LiteralExpression {

  /**
   * Removes the leading and trailing quotes. Naive implementation intended for resolving references
   * (in which case escaped characters, raw strings, etc. are unlikely).
   */
  public static String stripQuotes(String string) {
    return textRangeInElement(string).substring(string);
  }

  /** The range of text characters, excluding leading and trailing quotes. */
  public static TextRange textRangeInElement(String string) {
    // TODO: Handle escaped characters, etc. here?
    // (extract logic from BuildLexerBase.addStringLiteral)
    if (string.startsWith("\"\"\"")) {
      return string.length() <= 3
          ? TextRange.EMPTY_RANGE
          : TextRange.create(3, endTrimIndex(string, '"', 3));
    }
    if (string.startsWith("'''")) {
      return string.length() <= 3
          ? TextRange.EMPTY_RANGE
          : TextRange.create(3, endTrimIndex(string, '\'', 3));
    }
    if (string.startsWith("\"")) {
      return TextRange.create(1, endTrimIndex(string, '"', 1));
    }
    if (string.startsWith("'")) {
      return TextRange.create(1, endTrimIndex(string, '\'', 1));
    }
    return TextRange.allOf(string);
  }

  private static int endTrimIndex(String string, char quoteChar, int numberQuoteChars) {
    int trimIndex = string.length();
    for (int i = 1;
        i <= Math.min(numberQuoteChars, string.length() - numberQuoteChars);
        i++, trimIndex--) {
      if (string.charAt(string.length() - i) != quoteChar) {
        break;
      }
    }
    return trimIndex;
  }

  public static QuoteType getQuoteType(@Nullable String rawText) {
    if (rawText == null) {
      return QuoteType.NoQuotes;
    }
    if (rawText.startsWith("\"\"\"")) {
      return QuoteType.TripleDouble;
    }
    if (rawText.startsWith("'''")) {
      return QuoteType.TripleSingle;
    }
    if (rawText.startsWith("'")) {
      return QuoteType.Single;
    }
    if (rawText.startsWith("\"")) {
      return QuoteType.Double;
    }
    return QuoteType.NoQuotes;
  }

  public StringLiteral(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptVisitor(BuildElementVisitor visitor) {
    visitor.visitStringLiteral(this);
  }

  /** Removes the leading and trailing quotes */
  public String getStringContents() {
    return stripQuotes(getText());
  }

  public QuoteType getQuoteType() {
    return getQuoteType(getText());
  }

  /**
   * Labels are taken to reference: - the actual target they reference - the BUILD package specified
   * before the colon (only if explicitly present)
   */
  @Override
  public PsiReference[] getReferences() {
    // first look for attribute-specific references
    String attributeName = getParentAttributeName();
    if (attributeName != null) {
      PsiReference[] refs =
          AttributeSpecificStringLiteralReferenceProvider.findReferences(attributeName, this);
      if (refs.length != 0) {
        return refs;
      }
    }
    PsiReference primaryReference = getReference();
    if (primaryReference instanceof LabelReference) {
      LabelReference labelReference = (LabelReference) primaryReference;
      return new PsiReference[] {
        primaryReference,
        new PackageReferenceFragment(labelReference),
        new ExternalWorkspaceReferenceFragment(labelReference)
      };
    }
    return primaryReference != null
        ? new PsiReference[] {primaryReference}
        : PsiReference.EMPTY_ARRAY;
  }

  /** The primary reference -- this is the target referenced by the full label */
  @Nullable
  @Override
  public PsiReference getReference() {
    LoadStatement load = getLoadStatementParent();
    if (load != null) {
      StringLiteral importNode = load.getImportPsiElement();
      if (importNode == null) {
        return null;
      }
      LabelReference importReference = new LabelReference(importNode, false);
      if (this.equals(importNode)) {
        return importReference;
      }
      return new LoadedSymbolReference(this, importReference);
    }
    return new LabelReference(this, true);
  }

  /** If this string is an attribute value within a BUILD rule, return the attribute type. */
  @Nullable
  private String getParentAttributeName() {
    Keyword parentKeyword = PsiUtils.getParentOfType(this, Keyword.class, true);
    return parentKeyword != null ? parentKeyword.getName() : null;
  }

  @Nullable
  public LoadStatement getLoadStatementParent() {
    PsiElement parent = getParent();
    if (parent instanceof LoadStatement) {
      // the skylark extension label
      return (LoadStatement) parent;
    }
    if (parent instanceof AssignmentStatement) {
      // could be part of an aliased symbol
      parent = parent.getParent();
    }
    return parent instanceof LoadedSymbol ? (LoadStatement) parent.getParent() : null;
  }

  @Override
  public String getPresentableText() {
    return getText();
  }
}

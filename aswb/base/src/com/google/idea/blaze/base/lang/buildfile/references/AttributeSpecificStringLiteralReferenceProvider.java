/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiReference;

/** Non-default reference provider for {@link StringLiteral} values for a given attribute type. */
public interface AttributeSpecificStringLiteralReferenceProvider {

  ExtensionPointName<AttributeSpecificStringLiteralReferenceProvider> EP_NAME =
      ExtensionPointName.create(
          "com.google.idea.blaze.AttributeSpecificStringLiteralReferenceProvider");

  /** Find a reference type specific to values of this attribute. */
  static PsiReference[] findReferences(String attributeName, StringLiteral literal) {
    for (AttributeSpecificStringLiteralReferenceProvider provider : EP_NAME.getExtensions()) {
      PsiReference[] refs = provider.getReferences(attributeName, literal);
      if (refs.length != 0) {
        return refs;
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  /** Find references specific to this attribute. */
  PsiReference[] getReferences(String attributeName, StringLiteral literal);
}

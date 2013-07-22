/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.converters;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiPackageReference;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPackageConverter extends Converter<String> implements CustomReferenceConverter<String> {

  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<String> value, PsiElement element, ConvertContext context) {
    final String s = value.getStringValue();
    return s != null
           ? new MyPackageReferenceSet(s, element).getPsiReferences()
           : PsiReference.EMPTY_ARRAY;
  }

  private static class MyPackageReferenceSet extends PackageReferenceSet {
    public MyPackageReferenceSet(String s, PsiElement element) {
      super(s, element, ElementManipulators.getOffsetInElement(element));
    }

    @NotNull
    @Override
    protected PsiPackageReference createReference(TextRange range, int index) {
      return new PsiPackageReference(this, range, index) {
        @NotNull
        @Override
        public ResolveResult[] multiResolve(boolean incompleteCode) {
          return PsiElementResolveResult.createResults(new PsiElement[] {myElement});
        }
      };
    }
  }
}

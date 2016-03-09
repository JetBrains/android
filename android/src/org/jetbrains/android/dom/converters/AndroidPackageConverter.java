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

import com.android.tools.idea.model.AndroidModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PackageReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiPackageReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.xml.*;
import org.jetbrains.android.AndroidApplicationPackageRenameProcessor;
import org.jetbrains.android.facet.AndroidFacet;
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
    final int myStartInElement;

    public MyPackageReferenceSet(String s, PsiElement element) {
      this(s, element, ElementManipulators.getOffsetInElement(element));
    }

    public MyPackageReferenceSet(String str, PsiElement element, int startInElement) {
      super(str, element, startInElement);
      myStartInElement = startInElement;
    }

    @NotNull
    @Override
    protected PsiPackageReference createReference(TextRange range, int index) {
      // If the Gradle model specifies an application id, which does not rely on
      // the package in any way, then the package attribute in the manifest should
      // be taken to be a normal package reference, and should participate in normal
      // package rename refactoring
      AndroidFacet facet = AndroidFacet.getInstance(getElement());
      if (facet != null) {
        AndroidModel androidModel = facet.getAndroidModel();
        if (androidModel != null && androidModel.overridesManifestPackage() || facet.isLibraryProject()) {
          return new PsiPackageReference(this, range, index);
        }
      }

      return new MyPsiPackageReference(this, range, index);
    }
  }

  private static class MyPsiPackageReference extends PsiPackageReference {
    private final MyPackageReferenceSet myReferenceSet;
    private final TextRange myTextRange;

    public MyPsiPackageReference(MyPackageReferenceSet referenceSet, TextRange textRange, int index) {
      super(referenceSet, textRange, index);
      myReferenceSet = referenceSet;
      myTextRange = textRange;
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return PsiElementResolveResult.createResults(myElement);
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      final ResolveResult[] results = doMultiResolve();
      for (ResolveResult result : results) {
        if (getElement().getManager().areElementsEquivalent(result.getElement(), element)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return myElement;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      // here we only detect package movement and update relative names of components in manifest, don't change the current element

      if (!(element instanceof PsiPackage) || !(myElement instanceof XmlAttributeValue)) {
        throw new IncorrectOperationException("Cannot bind to " + element);
      }
      final String newPackageName = ((PsiPackage)element).getQualifiedName();
      final String basePackage = ((XmlAttributeValue)myElement).getValue();
      final String oldPackageName = myElement.getText().substring(myReferenceSet.myStartInElement, myTextRange.getEndOffset());
      final PsiFile file = myElement.getContainingFile();

      if (basePackage.length() > 0 && file instanceof XmlFile) {
        AndroidApplicationPackageRenameProcessor.processAllAttributesToUpdate(
          (XmlFile)file, basePackage, oldPackageName, newPackageName, new Processor<Pair<GenericAttributeValue, String>>() {
          @Override
          public boolean process(Pair<GenericAttributeValue, String> pair) {
            pair.getFirst().setStringValue(pair.getSecond());
            return true;
          }
        });
      }
      return myElement;
    }
  }
}

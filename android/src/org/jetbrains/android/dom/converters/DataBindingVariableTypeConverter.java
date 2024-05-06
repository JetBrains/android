/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.dom.converters;

import com.android.tools.idea.databinding.util.DataBindingUtil;
import com.android.tools.idea.databinding.util.DataBindingUtil.ClassReferenceVisitor;
import com.android.tools.idea.databinding.index.BindingXmlIndex;
import com.android.utils.OffsetTrackingDecodedXmlValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The converter for "type" attribute of "variable" element in databinding layouts.
 */
public class DataBindingVariableTypeConverter extends DataBindingConverter {
  @Override
  public String getErrorMessage(@Nullable String type, @NotNull ConvertContext context) {
    PsiTypeElement typeElement = createTypeElement(type, context);
    if (typeElement != null) {
      PsiType unresolved = findUnresolvedType(typeElement.getType());
      if (unresolved == null) {
        return null;
      }
      type = unresolved.getPresentableText();
    }
    return super.getErrorMessage(type, context);
  }

  @Nullable
  @Override
  public PsiElement fromString(@Nullable String type, @NotNull ConvertContext context) {
    if (type == null) {
      return null;
    }
    PsiElement element = super.fromString(type, context);
    if (element != null) {
      return element;
    }
    PsiTypeElement typeElement = createTypeElement(type, context);
    if (typeElement != null && findUnresolvedType(typeElement.getType()) != null) {
      return null;
    }
    return typeElement;
  }

  @Nullable
  private static PsiTypeElement createTypeElement(@Nullable String type, @NotNull ConvertContext context) {
    if (type == null) {
      return null;
    }

    BindingXmlIndex.Entry indexEntry = getBindingIndexEntry(context);
    if (indexEntry == null) {
      return null;
    }

    Project project = context.getProject();
    type = DataBindingUtil.getQualifiedType(project, type, indexEntry.getData(), false);
    if (type == null) {
      return null;
    }

    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiJavaParserFacade parser = facade.getParserFacade();
    try {
      return parser.createTypeElementFromText(type, null);
    } catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Nullable
  private static PsiType findUnresolvedType(@NotNull PsiType psiType) {
    PsiType[] result = new PsiType[1];
    psiType.accept(new ClassReferenceVisitor() {
      @Override
      public void visitClassReference(@NotNull PsiClassReferenceType classType) {
        if (result[0] == null) {
          PsiClassType rawType = classType.rawType();
          if (rawType.resolve() == null) {
            result[0] = rawType;
          }
        }
      }
    });
    return result[0];
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<PsiElement> value, PsiElement element, ConvertContext context) {
    assert element instanceof XmlAttributeValue;
    XmlAttributeValue attrValue = (XmlAttributeValue)element;
    OffsetTrackingDecodedXmlValue decodedValue = new OffsetTrackingDecodedXmlValue(attrValue.getValue());
    String typeStr = decodedValue.getDecodedCharacters().toString();

    List<PsiReference> result = new ArrayList<>();

    int valueOffset = attrValue.getValueTextRange().getStartOffset() - attrValue.getTextRange().getStartOffset();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(element.getProject());
    PsiJavaParserFacade parser = psiFacade.getParserFacade();
    PsiType psiType;
    try {
      psiType = parser.createTypeFromText(typeStr, null);
    } catch (IncorrectOperationException e) {
      return PsiReference.EMPTY_ARRAY;
    }

    if (psiType instanceof PsiPrimitiveType) {
      result.add(new PsiReferenceBase.Immediate<>(element, true, element));
    } else {
      psiType.accept(new ClassReferenceVisitor() {
        @Override
        public void visitClassReference(@NotNull PsiClassReferenceType classReference) {
          PsiJavaCodeReferenceElement reference = classReference.getReference();
          int offset = reference.getTextRange().getStartOffset();
          offset = decodedValue.getEncodedOffset(offset);
          createReferences(element, getName(reference), true, valueOffset + offset, context, result);
        }
      });
    }

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  @Nullable
  private static String getName(PsiJavaCodeReferenceElement reference) {
    return reference.isQualified() ? reference.getQualifiedName() : reference.getReferenceName();
  }
}

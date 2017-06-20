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

import com.android.tools.idea.res.DataBindingInfo;
import com.android.utils.OffsetTrackingDecodedXmlValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The converter for "type" attribute of "variable" element in databinding layouts.
 */
public class DataBindingVariableTypeConverter extends DataBindingConverter {
  /**
   * Returns the fully qualified name of the class referenced by {@code nameOrAlias}.
   * <p>
   * It is not guaranteed that the class will exist. The name returned here uses '.' for inner classes (like import declarations) and
   * not '$' as used by JVM.
   *
   * @param type a fully qualified name, or an alias as declared in an {@code <import>}, or an inner class of an alias
   * @param dataBindingInfo for getting the list of {@code <import>} tags
   * @return the qualified name of the class or the unqualified name if the {@code nameOrAlias} doesn't match any imports
   */
  public static String getQualifiedType(@Nullable String type, @Nullable DataBindingInfo dataBindingInfo) {
    if (type == null || dataBindingInfo == null) {
      return type;
    }

    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(dataBindingInfo.getProject());
    PsiJavaParserFacade parser = psiFacade.getParserFacade();
    PsiType psiType;
    try {
      psiType = parser.createTypeFromText(type, null);
    } catch (IncorrectOperationException e) {
      return null;
    }

    if (psiType instanceof PsiPrimitiveType) {
      return type;
    }

    StringBuilder result = new StringBuilder();
    int[] offset = new int[1];
    psiType.accept(new ClassReferenceVisitor() {
      @Override
      public void visitClassReference(PsiClassReferenceType classReference) {
        PsiJavaCodeReferenceElement reference = classReference.getReference();
        int nameOffset = reference.getTextRange().getStartOffset();
        // Copy text preceding the class name.
        while (offset[0] < nameOffset) {
          result.append(type.charAt(offset[0]++));
        }
        String className = getName(reference);
        // Copy the resolved class name.
        result.append(resolveImport(className, dataBindingInfo));
        offset[0] += className.length();
      }
    });
    // Copy text after the last class name.
    while (offset[0] < type.length()) {
      result.append(type.charAt(offset[0]++));
    }
    return result.toString();
  }

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
  public PsiElement fromString(@Nullable @NonNls String type, @NotNull ConvertContext context) {
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
  private static PsiTypeElement createTypeElement(@Nullable @NonNls String type, @NotNull ConvertContext context) {
    if (type == null) {
      return null;
    }
    DataBindingInfo dataBindingInfo = getDataBindingInfo(context);
    type = getQualifiedType(type, dataBindingInfo);

    Project project = context.getProject();
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
      public void visitClassReference(PsiClassReferenceType classType) {
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
        public void visitClassReference(PsiClassReferenceType classReference) {
          PsiJavaCodeReferenceElement reference = classReference.getReference();
          int offset = reference.getTextRange().getStartOffset();
          offset = decodedValue.getEncodedOffset(offset);
          createReferences(element, getName(reference), true, valueOffset + offset, context, result);
        }
      });
    }

    return result.toArray(new PsiReference[result.size()]);
  }

  private static String getName(PsiJavaCodeReferenceElement reference) {
    return reference.isQualified() ? reference.getQualifiedName() : reference.getReferenceName();
  }

  /**
   * Visits all class type references contained in a type.
   */
  private abstract static class ClassReferenceVisitor extends PsiTypeVisitor<Void> {
    @Nullable
    @Override
    public final Void visitClassType(PsiClassType classType) {
      if (classType instanceof PsiClassReferenceType) {
        visitClassReference((PsiClassReferenceType)classType);
      }

      PsiType[] parameters = classType.getParameters();
      for (PsiType parameter : parameters) {
        parameter.accept(this);
      }
      return null;
    }

    /** Visits a class reference. The referenced class may or may not exist. */
    public abstract void visitClassReference(PsiClassReferenceType classReference);
  }
}

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
package com.android.tools.idea.smali;

import com.android.tools.idea.smali.psi.SmaliAccessModifier;
import com.android.tools.idea.smali.psi.SmaliFieldName;
import com.android.tools.idea.smali.psi.SmaliFieldSpec;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.smali.SmaliHighlighterColors.*;
import static com.android.tools.idea.smali.psi.SmaliTypes.IDENTIFIER;
import static com.intellij.psi.util.PsiTreeUtil.findFirstParent;

public class SmaliAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    //System.out.println(element);
    if (element instanceof LeafPsiElement) {
      ASTNode node = element.getNode();
      if (node.getElementType().equals(IDENTIFIER)) {
        PsiElement fieldNameElement = findFirstParent(element, element1 -> element1 instanceof SmaliFieldName);
        if (fieldNameElement != null) {
          // This identifier is the name of the field.
          annotateFieldName(element, holder);
        }
      }
    }
  }

  private static void annotateFieldName(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    PsiElement fieldSpecElement = findFirstParent(element, element1 -> element1 instanceof SmaliFieldSpec);
    if (fieldSpecElement instanceof SmaliFieldSpec) {
      Annotation annotation = holder.createInfoAnnotation(element, null);

      List<SmaliAccessModifier> accessModifiers = ((SmaliFieldSpec)fieldSpecElement).getAccessModifierList();
      Set<String> accessModifierNames = getAccessModifierNames(accessModifiers);
      boolean isStatic = accessModifierNames.contains("static");
      boolean isConstant = isStatic && accessModifierNames.contains("final");

      if (isConstant) {
        annotation.setTextAttributes(CONSTANT_ATTR_KEY);
      }
      else if (isStatic) {
        annotation.setTextAttributes(STATIC_FIELD_ATTR_KEY);
      }
      else {
        annotation.setTextAttributes(INSTANCE_FIELD_ATTR_KEY);
      }
    }
  }

  @NotNull
  private static Set<String> getAccessModifierNames(@NotNull List<SmaliAccessModifier> accessModifiers) {
    if (accessModifiers.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> names = new HashSet<>();
    for (SmaliAccessModifier accessModifier : accessModifiers) {
      names.add(accessModifier.getText());
    }
    return names;
  }
}

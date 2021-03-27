/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.codeanalysis.utils;

import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import java.util.logging.Logger;

public class PsiCFGDebugUtil {
  public static final Logger LOG = Logger.getLogger(PsiCFGDebugUtil.class.getSimpleName());

  public static void debugOutputPsiElement(PsiElement e) {
    StringBuilder sb = new StringBuilder();
    sb.append("PsiElement Debug>> Type: " + e.getClass().getSimpleName() + " HashCode " + e.hashCode());
    sb.append("\n");
    if (e instanceof PsiAnonymousClass) {
      debugOutputPsiAnonymousClass(sb, (PsiAnonymousClass)e);
    }
    else if (e instanceof PsiClass) {
      debugOutputPsiClass(sb, (PsiClass)e);
    }
    else if (e instanceof PsiMethod) {
      debugOutputPsiMethod(sb, (PsiMethod)e);
    }
    else if (e instanceof PsiParameter) {
      debugOutputPsiParameter(sb, (PsiParameter)e);
    }
    else if (e instanceof PsiJavaFile) {
      debugOutputPsiJavaFile(sb, (PsiJavaFile)e);
    }
    else if (e instanceof PsiModifier) {

    }
    else if (e instanceof PsiModifierList) {

    }
    else if (e instanceof PsiField) {
      debugOutputPsiField(sb, (PsiField)e);
    }
    else if (e instanceof PsiReferenceExpression) {
      debugOutputPsiReferenceExpression(sb, (PsiReferenceExpression)e);
    }
    else if (e instanceof PsiThisExpression) {
      debugOutputPsiThisExpression(sb, (PsiThisExpression)e);
    }
    else {

    }
    LOG.info(sb.toString());
  }

  private static void debugOutputPsiParameter(StringBuilder sb, PsiParameter e) {
    sb.append("PsiParameter: " + e.getName() + "\n");
    sb.append("Type: " + e.getType().getCanonicalText() + "\n");
    sb.append("\n");
  }

  private static void debugOutputPsiJavaFile(StringBuilder sb, PsiJavaFile e) {
    sb.append("JavaFile: " + e.getName() + "\n");
    PsiClass[] allClasses = e.getClasses();
    for (PsiClass mClass : allClasses) {
      sb.append("Class in file: " + mClass.getQualifiedName() + "\n");
    }
    sb.append("\n");
  }

  private static void debugOutputPsiAnonymousClass(StringBuilder sb, PsiAnonymousClass psiClass) {
    sb.append("AnonymousClass: ");
    sb.append(psiClass.getName() == null ? "null" : psiClass.getName());
    sb.append("\n");
    sb.append("Identifier: ");
    PsiIdentifier psiIdentifier = psiClass.getNameIdentifier();
    sb.append(psiIdentifier == null ? "null" : psiIdentifier.getText());
    sb.append("\n");

    String qualifiedName = psiClass.getQualifiedName();
    sb.append("Qualified Name: " + (qualifiedName == null ? "null" : qualifiedName) + "\n");
    sb.append("Inner Classes Count: " + psiClass.getInnerClasses().length + "\n");
    sb.append("All Inner Classes Count: " + psiClass.getAllInnerClasses().length + "\n");

    PsiClassInitializer[] allInitalizers = psiClass.getInitializers();
    sb.append("Initializer count: " + allInitalizers.length);
    PsiMethod[] methods = psiClass.getMethods();
    sb.append("Methods count: " + methods.length + "\n");
  }

  private static void debugOutputPsiMethod(StringBuilder sb, PsiMethod method) {
    sb.append("Method: " + method.getName() + "\n");
    sb.append("Declaring Class: " + method.getContainingClass().getName() + "\n");
  }

  private static void debugOutputPsiField(StringBuilder sb, PsiField psiField) {
    sb.append("Field: " + psiField.getName() + "\n");
    sb.append("Class: " + psiField.getContainingClass().getQualifiedName() + "\n");
    sb.append("Type: " + psiField.getType().getCanonicalText() + "\n");
  }

  private static void debugOutputPsiClass(StringBuilder sb, PsiClass psiClass) {
    sb.append("Class: ");
    sb.append(psiClass.getName());
    sb.append("\n");
    sb.append("Identifier: ");
    sb.append(psiClass.getNameIdentifier().getText());
    sb.append("\n");

    sb.append("Qualified Name: " + psiClass.getQualifiedName() + "\n");
    sb.append("Inner Classes Count: " + psiClass.getInnerClasses().length + "\n");
    sb.append("All Inner Classes Count: " + psiClass.getAllInnerClasses().length + "\n");

    PsiClassInitializer[] allInitalizers = psiClass.getInitializers();
    sb.append("Initializer count: " + allInitalizers.length);
  }

  private static void debugOutputPsiReferenceExpression(StringBuilder sb, PsiReferenceExpression psiElement) {
    sb.append("PsiReferenceExpression: " + psiElement.hashCode() + "\n");
    PsiElement e = psiElement.resolve();
    sb.append("ResolveType: " + e.getClass().getSimpleName() + " " + e.hashCode() + "\n");
    PsiExpression expr = psiElement.getQualifierExpression();
    if (expr == null) {
      sb.append("Qualifier Expression: " + "null \n");
    }
    else {
      sb.append("Qualifier Expression: " + expr.getClass().getSimpleName() + "\n");
      sb.append("Qualifier Expression Text: " + expr.getText() + "\n");
    }

    JavaResolveResult[] results = psiElement.multiResolve(false);

    sb.append("MultiResolveResults: " + results.length + "\n");
    for (JavaResolveResult curRes : results) {
      sb.append("Result: " + curRes.getElement().getClass().getSimpleName() + "\t" + curRes.getElement().getText() + "\n");
    }
    sb.append("\n");

    if (psiElement instanceof PsiReferenceExpressionImpl) {
      PsiReferenceExpressionImpl refImpl = (PsiReferenceExpressionImpl)psiElement;
    }
  }

  private static void debugOutputPsiThisExpression(StringBuilder sb, PsiThisExpression thisExpr) {
    sb.append("PsiThisExpression: " + thisExpr.hashCode() + "\n");
    PsiType type = thisExpr.getType();
    sb.append("Type: " + type.getCanonicalText() + "\n");
  }

}

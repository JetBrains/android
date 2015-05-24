/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.SdkConstants;
import com.android.tools.lint.checks.SupportAnnotationDetector;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.inspections.ResourceTypeInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

import static com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE;
import static com.android.SdkConstants.TYPE_DEF_VALUE_ATTRIBUTE;

public class AndroidResolveHelper {
  public static class ResolveResult {
    @NotNull public final String label;
    @Nullable public final Icon icon;

    public ResolveResult(@NotNull String label, @Nullable Icon icon) {
      this.label = label;
      this.icon = icon;
    }
  }

  @Nullable
  public static PsiAnnotation getAnnotationForLocal(@NotNull PsiElement context, @NotNull String name) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    return getAnnotation(facade.getResolveHelper().resolveReferencedVariable(name, context));
  }

  @Nullable
  public static PsiAnnotation getAnnotationForField(@NotNull PsiElement context, @NotNull String className, @NotNull String fieldName) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiClass psiClass = JavaPsiFacade.getInstance(context.getProject()).findClass(className, getSearchScope(context));
    if (psiClass == null) {
      return null;
    }

    return getAnnotation(psiClass.findFieldByName(fieldName, true));
  }

  @NotNull
  private static GlobalSearchScope getSearchScope(@NotNull PsiElement context) {
    Module module = ModuleUtilCore.findModuleForPsiElement(context);
    if (module != null) {
      return module.getModuleWithDependenciesAndLibrariesScope(false);
    }
    else {
      return GlobalSearchScope.projectScope(context.getProject());
    }
  }

  @Nullable
  private static PsiAnnotation getAnnotation(@Nullable PsiModifierListOwner owner) {
    if (owner == null) {
      return null;
    }

    Project project = owner.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope searchScope = getSearchScope(owner);

    for (PsiAnnotation a : ResourceTypeInspection.getAllAnnotations(owner)) {
      String qualifiedName = a.getQualifiedName();
      if (qualifiedName == null || qualifiedName.startsWith("java")) {
        continue;
      }

      if (qualifiedName.endsWith(SupportAnnotationDetector.RES_SUFFIX)
        || qualifiedName.equals(SupportAnnotationDetector.COLOR_INT_ANNOTATION)
        || qualifiedName.equals(SdkConstants.INT_DEF_ANNOTATION)) {
        return a;
      }

      // typedef annotations involve a level of indirection
      // i.e. we need to from "@Visibility int visibility" to "@IntDef public @interface Visibility"
      PsiClass annotationClass = psiFacade.findClass(qualifiedName, searchScope);
      PsiAnnotation annotation = getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
    }

    return null;
  }

  public static class IntDefResolution {
    @Nullable public final Map<Integer,String> valuesMap;
    public final boolean canBeOred;

    public IntDefResolution(boolean canBeOred, @Nullable Map<Integer,String> valuesMap) {
      this.canBeOred = canBeOred;
      this.valuesMap = valuesMap;
    }

    public static IntDefResolution createError() {
      return new IntDefResolution(false, null);
    }
  }

  public static IntDefResolution resolveIntDef(@NotNull PsiAnnotation annotation) {
    // TODO: cache int def resolutions

    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiAnnotationMemberValue intValues = annotation.findAttributeValue(TYPE_DEF_VALUE_ATTRIBUTE);
    PsiAnnotationMemberValue[] allowedValues = intValues instanceof PsiArrayInitializerMemberValue
                                               ? ((PsiArrayInitializerMemberValue)intValues).getInitializers()
                                               : PsiAnnotationMemberValue.EMPTY_ARRAY;

    Map<Integer,String> valuesMap = Maps.newHashMap();

    for (PsiAnnotationMemberValue value : allowedValues) {
      if (!(value instanceof PsiReference)) {
        return IntDefResolution.createError();
      }

      PsiElement resolved = ((PsiReference)value).resolve();
      if (!(resolved instanceof PsiNamedElement)) {
        return IntDefResolution.createError();
      }

      // For each name, we need to identify the integer value corresponding to it. We first attempt to check if we can quickly
      // extract the value set by ConstantExpressionVisitor.VALUE.
      Key<?> key = Key.findKeyByName("VALUE");
      Integer constantValue = null;
      if (key != null) {
        Object v = value.getUserData(key);
        if (v instanceof Integer) {
          constantValue = (Integer)v;
        }
      }

      // If that didn't work, we invoke it directly
      if (constantValue == null && (resolved instanceof PsiField)) {
        Object v = JavaConstantExpressionEvaluator.computeConstantExpression(((PsiField)resolved).getInitializer(), null, false);
        if (v instanceof Integer) {
          constantValue = (Integer)v;
        }
      }

      if (constantValue == null) {
        return IntDefResolution.createError();
      }

      valuesMap.put(constantValue, ((PsiNamedElement)resolved).getName());
    }

    PsiAnnotationMemberValue orValue = annotation.findAttributeValue(TYPE_DEF_FLAG_ATTRIBUTE);
    boolean canBeOred = orValue instanceof PsiLiteral && Boolean.TRUE.equals(((PsiLiteral)orValue).getValue());

    return new IntDefResolution(canBeOred, valuesMap);
  }
}

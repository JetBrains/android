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
package com.android.tools.idea.debug;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.inspections.ResourceTypeInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
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
    PsiVariable variable = facade.getResolveHelper().resolveReferencedVariable(name, context);
    if (variable != null) {
      return getAnnotationForVariable(variable, 0);
    }

    return null;
  }

  @Nullable
  private static PsiAnnotation getAnnotationForVariable(@NotNull PsiVariable variable, int depth) {
    // Prevent deep recursion, and cycles if we try to chase assignment values from mutually assigned
    // variables in code like this:
    //    int var1 = 0, var2 = 0;
    //    var1 = var2;
    //    var2 = var1;
    if (depth > 10) {
      return null;
    }

    // Parameter? Those can carry annotations directly. So can local variable declarations.
    if (variable instanceof PsiParameter || variable instanceof PsiLocalVariable) {
      PsiAnnotation annotation = getAnnotation(variable);
      if (annotation != null) {
        return annotation;
      }
    }

    // We're stepping through the code, and we can be far from the variable declaration
    // location; the variable can be assigned in multiple places, so to correctly determine
    // its current value semantics we'd need to know exactly where the debugger is (the
    // current program counter), map that back to the AST, and then flow backwards to the most
    // recent assignment, and then chase the value chain from there.
    //
    // This is necessary if we want to correctly pick @Type1 versus @Type2 based on the
    // program location here:
    //    void test(int var, @Type1 int type1, @Type2 int type2, @ColorInt int rgba) {
    //       var = type1;
    //       ...
    //       var = type2;
    //       ...
    //       var = rgba;
    //       ...
    // However, this scenario is unlikely in real code.
    //
    // It's much more likely that a value will be assigned at most one typedef bound
    // value. Therefore, by searching through the known set of assignments and picking the
    // first match, we're likely to find a typedef which is helpful to the user.
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      if (initializer instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression callExpression = (PsiMethodCallExpression)initializer;
        PsiElement resolved = callExpression.getMethodExpression().resolve();
        if (resolved instanceof PsiMethod) {
          PsiAnnotation annotation = getAnnotation((PsiMethod)resolved);
          if (annotation != null) {
            return annotation;
          }
        }
      } else if (initializer instanceof PsiReferenceExpression) {
        PsiReferenceExpression reference = (PsiReferenceExpression) initializer;
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiField) {
          PsiAnnotation annotation = getAnnotationForField((PsiField)resolved);
          if (annotation != null) {
            return annotation;
          }
        } else if (resolved instanceof PsiVariable) {
          PsiAnnotation annotation = getAnnotationForVariable((PsiVariable)resolved, depth + 1);
          if (annotation != null) {
            return annotation;
          }
        }
      }
    }

    PsiMethod method = PsiTreeUtil.getParentOfType(variable, PsiMethod.class, true);
    if (method == null) {
      return null;
    }

    // Variable not initialized; might be assigned to. Look for assignments.
    Collection<PsiAssignmentExpression> assignments = PsiTreeUtil.findChildrenOfType(method, PsiAssignmentExpression.class);
    for (PsiAssignmentExpression assignment : assignments) {
      PsiExpression lhs = assignment.getLExpression();
      if (lhs instanceof PsiReferenceExpression) {
        PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
        PsiElement resolved = reference.resolve();
        if (resolved == variable) {
          // Yes, assigning to our target variable
          // Look at the rhs to see if we can figure out the type
          PsiExpression rhs = assignment.getRExpression();
          if (rhs instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression callExpression = (PsiMethodCallExpression)rhs;
            PsiElement r = callExpression.getMethodExpression().resolve();
            if (r instanceof PsiMethod) {
              PsiAnnotation annotation = getAnnotation((PsiMethod)r);
              if (annotation != null) {
                return annotation;
              }
            }
          } else if (rhs instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref = (PsiReferenceExpression) rhs;
            PsiElement r = ref.resolve();
            if (r instanceof PsiField) {
              PsiAnnotation annotation = getAnnotationForField((PsiField)r);
              if (annotation != null) {
                return annotation;
              }
            } else if (r instanceof PsiVariable) {
              PsiAnnotation annotation = getAnnotationForVariable((PsiVariable)r, depth + 1);
              if (annotation != null) {
                return annotation;
              }
            }
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiAnnotation getAnnotationForField(@NotNull PsiElement context, @NotNull String className, @NotNull String fieldName) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    PsiClass psiClass = JavaPsiFacade.getInstance(context.getProject()).findClass(className, getSearchScope(context));
    if (psiClass == null) {
      return null;
    }

    PsiField field = psiClass.findFieldByName(fieldName, true);
    if (field != null) {
      return getAnnotationForField(field);
    }

    return null;
  }

  @Nullable
  private static PsiAnnotation getAnnotationForField(PsiField field) {
    PsiAnnotation annotation = getAnnotation(field);
    if (annotation == null) {
      // Fields are usually not annotated (because they are private, not part of the API).
      // However, if a field has a getter, there is a good chance that the getter has been
      // annotated!
      PsiMethod getter = PropertyUtil.findGetterForField(field);
      if (getter != null) {
        return getAnnotation(getter);
      }
    }
    return annotation;
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

      if (qualifiedName.endsWith(ResourceEvaluator.RES_SUFFIX)
        || qualifiedName.equals(ResourceEvaluator.COLOR_INT_ANNOTATION)
        || qualifiedName.equals(ResourceEvaluator.PX_ANNOTATION)
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

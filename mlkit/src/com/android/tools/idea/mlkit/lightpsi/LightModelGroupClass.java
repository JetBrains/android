/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.lightpsi;

import com.android.tools.idea.psi.light.DeprecatableLightMethodBuilder;
import com.android.tools.mlkit.MlNames;
import com.android.tools.mlkit.TensorGroupInfo;
import com.android.tools.mlkit.TensorInfo;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableSet;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a light class auto-generated for getting results from model tensor group. It will generate class with following format:
 * <pre>
 * public class TensorGroupName {
 *   public Type getTensor1AsType();
 *   // More getters for other tensors if exist.
 * }
 * </>
 *
 * @see LightModelClass
 */
public class LightModelGroupClass extends AndroidLightClassBase {
  @NotNull
  private final PsiClass containingClass;
  @NotNull
  private final String qualifiedName;
  @NotNull
  private final CachedValue<PsiMethod[]> myMethodCache;
  @NotNull
  private final String myClassName;

  public LightModelGroupClass(@NotNull Module module,
                              @NotNull List<TensorInfo> tensorInfos,
                              @NotNull TensorGroupInfo tensorGroupInfo,
                              @NotNull PsiClass containingClass) {
    super(PsiManager.getInstance(module.getProject()), ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    this.myClassName = StringHelper.usLocaleCapitalize(tensorGroupInfo.getIdentifierName());
    this.qualifiedName = String.join(".", containingClass.getQualifiedName(), myClassName);
    this.containingClass = containingClass;

    setModuleInfo(module, false);

    // Caches getter methods for output class.
    myMethodCache = CachedValuesManager.getManager(getProject()).createCachedValue(
      () -> {
        List<PsiMethod> methods = new ArrayList<>();
        for (TensorInfo tensorInfo : tensorInfos) {
          if (tensorGroupInfo.getTensorNames().contains(tensorInfo.getName())) {
            methods.add(buildGetterMethod(tensorInfo));
          }
        }

        return CachedValueProvider.Result.create(methods.toArray(PsiMethod.EMPTY_ARRAY), ModificationTracker.NEVER_CHANGED);
      }, false);
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return qualifiedName;
  }

  @NotNull
  @Override
  public String getName() {
    return myClassName;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethodCache.getValue();
  }

  @NotNull
  private PsiMethod buildGetterMethod(@NotNull TensorInfo tensorInfo) {
    GlobalSearchScope scope = getResolveScope();
    PsiType returnType = getPsiClassType(tensorInfo, getProject(), scope);
    String methodName = MlNames.formatGetterName(tensorInfo.getIdentifierName(), CodeUtils.getTypeName(returnType));
    DeprecatableLightMethodBuilder method = new DeprecatableLightMethodBuilder(myManager, JavaLanguage.INSTANCE, methodName);
    method
      .setMethodReturnType(returnType, true)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL)
      .setContainingClass(this)
      .setNavigationElement(this);
    return method;
  }

  @NotNull
  @Override
  public PsiClass getContainingClass() {
    return containingClass;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return containingClass.getNavigationElement();
  }

  @NotNull
  private static PsiType getPsiClassType(@NotNull TensorInfo tensorInfo, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    if (tensorInfo.getContentType() == TensorInfo.ContentType.BOUNDING_BOX) {
      return PsiType.getTypeByName(ClassNames.RECT_F, project, scope);
    }
    else if (tensorInfo.getFileType() == TensorInfo.FileType.TENSOR_VALUE_LABELS) {
      return PsiType.getTypeByName(CommonClassNames.JAVA_LANG_STRING, project, scope);
    }
    else if (tensorInfo.getDataType() == TensorInfo.DataType.FLOAT32) {
      return PsiTypes.floatType();
    }
    else if (tensorInfo.getDataType() == TensorInfo.DataType.UINT8) {
      return PsiTypes.intType();
    }
    else {
      Logger.getInstance(LightModelGroupClass.class).warn("Can't find desired data type, fallback to int.");
      return PsiTypes.intType();
    }
  }
}

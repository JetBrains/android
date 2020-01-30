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

import com.android.tools.idea.mlkit.MlkitModuleService;
import com.android.tools.idea.mlkit.MlkitUtils;
import com.android.tools.mlkit.Param;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PropertyUtilBase;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Output class for output tensors. For each tensor it has a private field to store data and a getter method.
 */
public class MlkitOutputLightClass extends AndroidLightClassBase {
  public static final String NAME = "Output";

  private final PsiClass containingClass;
  private final String qualifiedName;
  private final List<Param> params;
  private final Module myModule;
  private final CachedValue<PsiMethod[]> myMethodCache;

  public MlkitOutputLightClass(@NotNull Module module, List<Param> params, PsiClass containingClass) {
    super(PsiManager.getInstance(module.getProject()),
          ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    this.params = params;
    this.qualifiedName = String.join(".", MlkitUtils.computeModelPackageName(module), containingClass.getName(), NAME);
    this.myModule = module;
    this.containingClass = containingClass;

    setModuleInfo(module, false);

    // Cache getter methods for output class
    ModificationTracker modificationTracker = new MlkitModuleService.ModelFileModificationTracker(module);
    myMethodCache = CachedValuesManager.getManager(getProject()).createCachedValue(
      () -> {
        PsiMethod[] methods = new PsiMethod[params.size()];
        for (int i = 0; i < methods.length; i++) {
          methods[i] = buildGetterMethod(params.get(i));
        }

        return CachedValueProvider.Result.create(methods, modificationTracker);
      }
      , false);
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return qualifiedName;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethodCache.getValue();
  }

  private PsiMethod buildGetterMethod(Param param) {
    PsiType returnType = PsiType.getTypeByName(CodeUtils.getTypeQualifiedName(param), myModule.getProject(), getResolveScope());

    return new LightMethodBuilder(myManager, PropertyUtilBase.suggestGetterName(param.getName(), returnType))
      .setMethodReturnType(returnType)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL)
      .setContainingClass(this);
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return containingClass;
  }
}

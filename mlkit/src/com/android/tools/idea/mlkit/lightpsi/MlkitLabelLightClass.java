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

import com.android.tools.idea.mlkit.MlkitUtils;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.Param;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Label light class if the output tensor is {@code TENSOR_AXIS_LABELS}. It contains label string
 * and probability of its likelihood.
 */
//TODO(b/148677238): consider remove it or use it
public class MlkitLabelLightClass extends AndroidLightClassBase {
  private final PsiClass containingClass;
  private final String qualifiedName;
  private final Param param;
  private final PsiMethod[] myMethods;


  public MlkitLabelLightClass(@NotNull Module module, Param param, PsiClass containingClass) {
    super(PsiManager.getInstance(module.getProject()),
          ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    this.param = param;
    this.qualifiedName = String.join(".", MlkitUtils.computeModelPackageName(module), containingClass.getName(), MlkitNames.LABEL);
    this.containingClass = containingClass;

    setModuleInfo(module, false);

    myMethods = new PsiMethod[2];

    myMethods[0] = new LightMethodBuilder(myManager, "getName")
      .addModifier(PsiModifier.PUBLIC)
      .setContainingClass(this)
      .setMethodReturnType("java.lang.String");
    myMethods[1] = new LightMethodBuilder(myManager, "getProbability")
      .addModifier(PsiModifier.PUBLIC)
      .setContainingClass(this)
      .setMethodReturnType(PsiType.FLOAT);
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return qualifiedName;
  }

  @Override
  public String getName() {
    return MlkitNames.LABEL;
  }


  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethods;
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return containingClass;
  }
}

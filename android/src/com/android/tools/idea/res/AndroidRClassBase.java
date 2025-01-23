/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import org.jetbrains.android.augment.InnerRClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for implementations of light R classes (top-level, which only contain inner classes for every resource type).
 *
 * @see InnerRClassBase
 */
public abstract class AndroidRClassBase extends AndroidClassWithOnlyInnerClassesBase {
  protected AndroidRClassBase(@NotNull PsiManager psiManager,
                           @Nullable String packageName,
                           @NotNull AndroidLightClassModuleInfo moduleInfo) {
    super(SdkConstants.R_CLASS,
          packageName,
          psiManager,
          ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL),
          moduleInfo);
  }
}

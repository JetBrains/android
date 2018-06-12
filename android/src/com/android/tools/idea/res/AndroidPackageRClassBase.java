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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for implementations of light R classes (top-level, which only contain inner classes for every resource type).
 *
 * @see org.jetbrains.android.augment.ResourceTypeClassBase
 */
public abstract class AndroidPackageRClassBase extends AndroidLightClassBase {
  @NotNull protected final PsiFile myFile;
  @NotNull protected final String myFullyQualifiedName;
  private CachedValue<PsiClass[]> myClassCache;

  public AndroidPackageRClassBase(@NotNull PsiManager psiManager, @NotNull String packageName) {
    super(psiManager, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));
    myFile =
        PsiFileFactory.getInstance(myManager.getProject())
                      .createFileFromText("R.java", JavaFileType.INSTANCE, "package " + packageName + ";");
    myFullyQualifiedName = packageName + ".R";
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(getQualifiedName()).toString();
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myFullyQualifiedName;
  }

  @Override
  public String getName() {
    return "R";
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myFile;
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    if (myClassCache == null) {
      myClassCache =
          CachedValuesManager.getManager(getProject())
                             .createCachedValue(
                  () ->
                      CachedValueProvider.Result.create(
                        doGetInnerClasses(),
                        PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
    }
    return myClassCache.getValue();
  }

  protected abstract PsiClass[] doGetInnerClasses();

  @Override
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    for (PsiClass aClass : getInnerClasses()) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }
}

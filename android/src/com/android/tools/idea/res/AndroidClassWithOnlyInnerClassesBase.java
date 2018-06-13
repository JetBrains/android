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
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Base class for light classes that only contain inner classes, like {@code R} or {@code Manifest}.
 */
public abstract class AndroidClassWithOnlyInnerClassesBase extends AndroidLightClassBase {
  @NotNull protected final CachedValue<PsiClass[]> myClassCache;
  @NotNull protected final String myFullyQualifiedName;
  @NotNull protected final String myShortName;
  @NotNull protected final PsiFile myFile;

  public AndroidClassWithOnlyInnerClassesBase(@NotNull String shortName,
                                              @NotNull String packageName,
                                              @NotNull PsiManager psiManager,
                                              @NotNull Collection<String> modifiers) {
    super(psiManager, modifiers);
    myShortName = shortName;
    myFullyQualifiedName = packageName + "." + shortName;

    myClassCache =
      CachedValuesManager.getManager(getProject()).createCachedValue(
        () ->
          CachedValueProvider.Result.create(
            doGetInnerClasses(),
            PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));

    myFile = PsiFileFactory.getInstance(myManager.getProject())
                           .createFileFromText(shortName + ".java", JavaFileType.INSTANCE, "package " + packageName + ";");
  }

  @Nullable
  @Override
  public final PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public final PsiClass[] getInnerClasses() {
    return myClassCache.getValue();
  }

  @Nullable
  @Override
  public final PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    for (PsiClass aClass : getInnerClasses()) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  protected abstract PsiClass[] doGetInnerClasses();

  @Override
  @NotNull
  public final String getName() {
    return myShortName;
  }

  @NotNull
  @Override
  public final String getQualifiedName() {
    return myFullyQualifiedName;
  }

  @NotNull
  @Override
  public final PsiFile getContainingFile() {
    return myFile;
  }

  @Override
  @NotNull
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(getQualifiedName()).toString();
  }
}

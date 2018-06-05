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

import com.android.resources.ResourceType;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.augment.ResourceTypeClass;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Represents a dynamic "class R" for resources in an Android module. */
public class AndroidPackageRClass extends AndroidLightClassBase {
  private static final Logger LOG = Logger.getInstance(AndroidPackageRClass.class);

  @NotNull private final PsiFile myFile;
  @NotNull private final String myFullyQualifiedName;
  @NotNull private final Module myModule;

  private CachedValue<PsiClass[]> myClassCache;

  public AndroidPackageRClass(
      @NotNull PsiManager psiManager, @NotNull String packageName, @NotNull Module module) {
    super(psiManager, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));

    myModule = module;
    myFullyQualifiedName = packageName + ".R";
    myFile =
        PsiFileFactory.getInstance(myManager.getProject())
            .createFileFromText("R.java", JavaFileType.INSTANCE, "package " + packageName + ";");

    this.putUserData(ModuleUtilCore.KEY_MODULE, module);
    // Some scenarios move up to the file level and then attempt to get the module from the file.
    myFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
  }

  @NotNull
  public Module getModule() {
    return myModule;
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
                      Result.create(
                          doGetInnerClasses(),
                          PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
    }
    return myClassCache.getValue();
  }

  private PsiClass[] doGetInnerClasses() {
    if (DumbService.isDumb(getProject())) {
      LOG.debug("R_CLASS_AUGMENT: empty because of dumb mode");
      return PsiClass.EMPTY_ARRAY;
    }

    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    if (facet == null) {
      LOG.debug("R_CLASS_AUGMENT: empty because no facet");
      return PsiClass.EMPTY_ARRAY;
    }

    Set<ResourceType> types =
        ResourceReferenceConverter.getResourceTypesInCurrentModule(facet);
    List<PsiClass> result = new ArrayList<>();

    for (ResourceType type : types) {
      result.add(new ResourceTypeClass(facet, type.getName(), this));
    }
    LOG.debug("R_CLASS_AUGMENT: " + result.size() + " classes added");
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }

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

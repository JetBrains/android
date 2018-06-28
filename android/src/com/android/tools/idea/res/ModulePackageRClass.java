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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.augment.ModuleResourceTypeClass;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Represents a dynamic "class R" for resources in an Android module. */
public class ModulePackageRClass extends AndroidPackageRClassBase {
  private static final Logger LOG = Logger.getInstance(ModulePackageRClass.class);

  @NotNull private final Module myModule;

  public ModulePackageRClass(
      @NotNull PsiManager psiManager, @NotNull String packageName, @NotNull Module module) {
    super(psiManager, packageName);
    myModule = module;
    this.putUserData(ModuleUtilCore.KEY_MODULE, module);
    // Some scenarios move up to the file level and then attempt to get the module from the file.
    myFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @Override
  protected PsiClass[] doGetInnerClasses() {
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
      if (type.getHasInnerClass()) {
        result.add(new ModuleResourceTypeClass(facet, type, this));
      }
    }
    LOG.debug("R_CLASS_AUGMENT: " + result.size() + " classes added");
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }
}

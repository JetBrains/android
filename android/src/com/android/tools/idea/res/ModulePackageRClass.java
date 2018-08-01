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
import com.android.builder.model.AaptOptions;
import com.android.resources.ResourceType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.augment.ModuleResourceTypeClass;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Represents a dynamic "class R" for resources in an Android module. */
public class ModulePackageRClass extends AndroidPackageRClassBase {
  private static final Logger LOG = Logger.getInstance(ModulePackageRClass.class);

  @NotNull private final Module myModule;
  @NotNull private final AaptOptions.Namespacing myNamespacing;

  public ModulePackageRClass(@NotNull PsiManager psiManager, @NotNull Module module, @NotNull AaptOptions.Namespacing namespacing) {
    // TODO(b/110188226): Update the file package name when the module's package name changes.
    super(psiManager, getPackageName(module));
    myModule = module;
    myNamespacing = namespacing;
    this.putUserData(ModuleUtilCore.KEY_MODULE, module);
    // Some scenarios move up to the file level and then attempt to get the module from the file.
    myFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
  }

  /** Helper static method that can be called to compute the value to be passed to the super constructor. */
  @Nullable
  private static String getPackageName(@NotNull Module module) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      return null;
    }

    return AndroidManifestUtils.getPackageName(androidFacet);
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
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
        result.add(new ModuleResourceTypeClass(facet, myNamespacing, type, this));
      }
    }
    LOG.debug("R_CLASS_AUGMENT: " + result.size() + " classes added");
    return result.toArray(PsiClass.EMPTY_ARRAY);
  }

  /**
   * {@inheritDoc}
   *
   * For {@link ModulePackageRClass} this is the package name specified in the module's manifest.
   */
  @Nullable
  @Override
  public String getQualifiedName() {
    AndroidFacet androidFacet = AndroidFacet.getInstance(myModule);
    if (androidFacet == null) {
      return null;
    }

    String packageName = AndroidManifestUtils.getPackageName(androidFacet);
    if (packageName == null) {
      return null;
    }

    return packageName + "." + SdkConstants.R_CLASS;
  }
}

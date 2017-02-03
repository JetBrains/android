/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.apk;

import com.intellij.facet.*;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Facet applied to modules in a project created by importing an APK file.
 */
public class AndroidApkFacet extends Facet<AndroidApkFacetConfiguration> {
  @NotNull public static final FacetTypeId<AndroidApkFacet> TYPE_ID = new FacetTypeId<>("android-apk");

  @NonNls public static final String ID = "android-apk";
  @NonNls public static final String NAME = "Android-APK";

  public AndroidApkFacet(@NotNull Module module,
                         @NotNull String name,
                         @NotNull AndroidApkFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static AndroidApkFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType(ID);
    assert facetType instanceof AndroidApkFacetType;
    return (AndroidApkFacetType)facetType;
  }

  @Nullable
  public static AndroidApkFacet getInstance(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    AndroidApkFacet facet = getInstance(module);
    if (facet == null) {
      // facet may be present, but not visible if ModifiableFacetModel has not been committed yet (e.g. in the case of a new project.)
      ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
      facet = facetModel.getFacetByType(TYPE_ID);
    }
    return facet;
  }

  @Nullable
  public static AndroidApkFacet getInstance(@NotNull Module module) {
    if (module.isDisposed()) {
      return null;
    }
    return FacetManager.getInstance(module).getFacetByType(TYPE_ID);
  }
}
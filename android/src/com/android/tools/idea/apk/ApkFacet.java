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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Facet applied to modules in a project created by importing an APK file.
 */
public class ApkFacet extends Facet<ApkFacetConfiguration> {
  @NotNull private static final FacetTypeId<ApkFacet> TYPE_ID = new FacetTypeId<>("android-apk");

  @Nullable
  public static ApkFacet getInstance(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    ApkFacet facet = getInstance(module);
    if (facet == null) {
      // facet may be present, but not visible if ModifiableFacetModel has not been committed yet (e.g. in the case of a new project.)
      ModifiableFacetModel facetModel = modelsProvider.getModifiableFacetModel(module);
      facet = facetModel.getFacetByType(TYPE_ID);
    }
    return facet;
  }

  @Nullable
  public static ApkFacet getInstance(@NotNull Module module) {
    if (module.isDisposed()) {
      return null;
    }
    return FacetManager.getInstance(module).getFacetByType(TYPE_ID);
  }

  @NotNull
  public static FacetTypeId<ApkFacet> getFacetTypeId() {
    return TYPE_ID;
  }

  public ApkFacet(@NotNull Module module,
                  @NotNull String name,
                  @NotNull ApkFacetConfiguration configuration) {
    super(getFacetType(), module, name, configuration, null);
  }

  @NotNull
  public static ApkFacetType getFacetType() {
    FacetType facetType = FacetTypeRegistry.getInstance().findFacetType(getFacetId());
    assert facetType instanceof ApkFacetType;
    return (ApkFacetType)facetType;
  }

  @NotNull
  public static String getFacetId() {
    return "apk";
  }

  @NotNull
  public static String getFacetName() {
    return "APK";
  }
}
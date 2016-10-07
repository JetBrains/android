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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android-APK facet.
 *
 * </p>This facet is set to IDEA modules that have been imported from an APK project so that the IDE could identify them.
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
  public static AndroidApkFacet getInstance(@NotNull Module module) {
    return FacetManager.getInstance(module).getFacetByType(TYPE_ID);
  }

  @Nullable
  public static AndroidApkFacet getInstance(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    // Only 1 module existed for Apk project
    Module[] modules = moduleManager.getModules();
    if (modules.length == 1) {
      return getInstance(modules[0]);
    }
    return null;
  }
}
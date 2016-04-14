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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AndroidApkFacetType extends FacetType<AndroidApkFacet, AndroidApkFacetConfiguration> {
  public AndroidApkFacetType() {
    super(AndroidApkFacet.TYPE_ID, AndroidApkFacet.ID, AndroidApkFacet.NAME, null);
  }

  @Override
  public AndroidApkFacetConfiguration createDefaultConfiguration() {
    return new AndroidApkFacetConfiguration();
  }

  @Override
  public AndroidApkFacet createFacet(@NotNull Module module,
                                     @NotNull String name,
                                     @NotNull AndroidApkFacetConfiguration configuration,
                                     @Nullable Facet underlyingFacet) {
    return new AndroidApkFacet(module, name, configuration);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AndroidIcons.Android;
  }
}
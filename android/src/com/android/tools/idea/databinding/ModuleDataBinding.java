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
package com.android.tools.idea.databinding;

import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleDataBinding {
  @Nullable private LightBrClass myLightBrClass;
  private boolean myEnabled;

  @NotNull
  public static ModuleDataBinding getInstance(@NotNull AndroidFacet facet) {
    ModuleDataBinding dataBinding = ModuleServiceManager.getService(facet.getModule(), ModuleDataBinding.class);
    assert dataBinding != null; // service registered in android plugin
    return dataBinding;
  }

  private ModuleDataBinding() {
    setEnabled(false);
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  /**
   * Set by {@linkplain DataBindingUtil} the first time we need it.
   *
   * @param lightBrClass
   * @see DataBindingUtil#getOrCreateBrClassFor(AndroidFacet)
   */
  void setLightBrClass(@NotNull LightBrClass lightBrClass) {
    myLightBrClass = lightBrClass;
  }

  /**
   * Returns the light BR class for this facet if it is aready set.
   *
   * @return The BR class for this facet, if exists
   * @see DataBindingUtil#getOrCreateBrClassFor(AndroidFacet)
   */
  @Nullable
  LightBrClass getLightBrClass() {
    return myLightBrClass;
  }
}

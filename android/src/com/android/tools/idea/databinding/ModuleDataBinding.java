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

import com.intellij.openapi.util.Key;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleDataBinding extends AndroidFacetScopedService {
  private static final Key<ModuleDataBinding> KEY = Key.create("com.android.tools.idea.databinding.ModuleDataBinding");

  @Nullable private LightBrClass myLightBrClass;

  public static void enable(@NotNull AndroidFacet facet) {
    setEnabled(facet, true);
  }

  public static void disable(@NotNull AndroidFacet facet) {
    setEnabled(facet, false);
  }

  private static void setEnabled(@NotNull AndroidFacet facet, boolean enabled) {
    if (isEnabled(facet) != enabled) {
      ModuleDataBinding dataBinding = enabled ? new ModuleDataBinding(facet) : null;
      facet.putUserData(KEY, dataBinding);
    }
  }

  public static boolean isEnabled(@NotNull AndroidFacet facet) {
    return getInstance(facet) != null;
  }

  @Nullable
  public static ModuleDataBinding getInstance(@NotNull AndroidFacet facet) {
    return facet.getUserData(KEY);
  }

  private ModuleDataBinding(@NotNull AndroidFacet facet) {
    super(facet);
  }

  @Override
  protected void onServiceDisposal(@NotNull AndroidFacet facet) {
    facet.putUserData(KEY, null);
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

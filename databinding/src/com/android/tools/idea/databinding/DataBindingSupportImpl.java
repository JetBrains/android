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
package com.android.tools.idea.databinding;

import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class DataBindingSupportImpl implements DataBindingSupport {

  /**
   * Returns data binding mode for the facet.
   */
  @Override
  @NotNull
  public DataBindingMode getDataBindingMode(@NotNull AndroidFacet facet) {
    return ModuleDataBinding.getInstance(facet).getDataBindingMode();
  }

  /**
   * Returns tracker that changes when a facet's data binding enabled value changes.
   */
  @Override
  @NotNull
  public ModificationTracker getDataBindingEnabledTracker() {
    return InternalDataBindingUtil.DATA_BINDING_ENABLED_TRACKER;
  }
}
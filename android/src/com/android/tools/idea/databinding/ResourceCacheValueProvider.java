/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.rendering.LocalResourceRepository;
import com.intellij.openapi.util.CompositeModificationTracker;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

abstract public class ResourceCacheValueProvider<T> implements CachedValueProvider<T> {
  ModificationTracker myTracker = new ModificationTracker() {
    @Override
    public long getModificationCount() {
      LocalResourceRepository moduleResources = myFacet.getModuleResources(false);
      return moduleResources == null ? Long.MIN_VALUE : moduleResources.getModificationCount();
    }
  };
  final AndroidFacet myFacet;

  public ResourceCacheValueProvider(AndroidFacet facet) {
    myFacet = facet;
  }

  @Nullable
  @Override
  public final Result<T> compute() {
    if (myFacet.getModuleResources(false) == null) {
      return Result.create(defaultValue(), myTracker);
    }
    Object additionalTracker = getAdditionalTracker();
    if (additionalTracker == null) {
      return Result.create(doCompute(), myTracker);
    } else {
      return Result.create(doCompute(), myTracker, additionalTracker);
    }
  }

  abstract T doCompute();

  abstract T defaultValue();

  protected Object getAdditionalTracker() {
    return null;
  }
}


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

import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

abstract public class ResourceCacheValueProvider<T> implements CachedValueProvider<T>, ModificationTracker {
  ModificationTracker[] myAdditionalTrackers;
  private ModificationTracker myTracker = new ModificationTracker() {
    private long myLastVersion = -1;
    private long myVersion = 0;
    @Override
    public long getModificationCount() {
      LocalResourceRepository moduleResources = ModuleResourceRepository.findExistingInstance(myFacet);
      // make sure it changes if facet's module resource availability changes
      long version = moduleResources == null ? Integer.MIN_VALUE : moduleResources.getModificationCount();
      if (version != myLastVersion) {
        myLastVersion = version;
        myVersion ++;
      }
      return myVersion;
    }
  };
  private final AndroidFacet myFacet;

  public ResourceCacheValueProvider(AndroidFacet facet, ModificationTracker... additionalTrackers) {
    myFacet = facet;
    myAdditionalTrackers = additionalTrackers;
  }

  public AndroidFacet getFacet() {
    return myFacet;
  }

  @Override
  public long getModificationCount() {
    return myTracker.getModificationCount();
  }

  @NotNull
  @Override
  public final Result<T> compute() {
    if (ModuleResourceRepository.findExistingInstance(myFacet) == null) {
      return Result.create(defaultValue(), myTracker, myAdditionalTrackers);
    }
    return Result.create(doCompute(), myTracker, myAdditionalTrackers);
  }

  abstract T doCompute();

  abstract T defaultValue();
}


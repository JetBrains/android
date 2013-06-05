/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.google.common.collect.Lists;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Resource repository for a module along with all its library dependencies */
final class ModuleSetResourceRepository extends MultiResourceRepository {
  private ModuleSetResourceRepository(@NotNull List<ProjectResources> delegates) {
    super(delegates);
    assert delegates.size() >= 2; // factory should delegate to a plain ModuleResourceRepository if not
  }

  @NotNull
  public static ProjectResources create(@NotNull AndroidFacet facet) {
    List<AndroidFacet> libraries = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    boolean includeLibraries = false;

    ProjectResources main = get(facet.getModule(), includeLibraries);
    if (libraries.isEmpty()) {
      return main;
    }
    List<ProjectResources> resources = Lists.newArrayListWithExpectedSize(libraries.size());
    for (AndroidFacet f : libraries) {
      ProjectResources r = get(f.getModule(), includeLibraries);
      resources.add(r);
    }

    resources.add(main);

    // TODO: How do we update the module set if the module roots (dependencies) have changed?
    // See ModuleResourceRepository#updateRoots for similar logic we should apply here (but for dependencies, not resource dirs obviously)

    return new ModuleSetResourceRepository(resources);
  }
}

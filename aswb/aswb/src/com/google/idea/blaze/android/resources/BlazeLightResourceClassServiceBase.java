/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.resources;

import com.android.tools.idea.projectsystem.LightResourceClassService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Implementation of {@link LightResourceClassService} set up at Blaze sync time. */
public abstract class BlazeLightResourceClassServiceBase implements LightResourceClassService {

  @VisibleForTesting
  public static final FeatureRolloutExperiment workspaceResourcesFeature =
      new FeatureRolloutExperiment("aswb.workspace.light.class.enabled");

  Map<String, BlazeRClass> rClasses = Maps.newHashMap();
  Map<Module, BlazeRClass> rClassesByModule = Maps.newHashMap();
  final Set<BlazeRClass> allRClasses = Sets.newHashSet();

  @Override
  public Collection<? extends PsiClass> getLightRClassesAccessibleFromModule(Module module) {
    if (workspaceResourcesFeature.isEnabled()
        && module.getName().equals(BlazeDataStorage.WORKSPACE_MODULE_NAME)) {
      // Returns all the packages in resource modules, and all the workspace packages that
      // have previously been asked for. All `res/` directories in our project should belong to a
      // resource module. For java sources, IntelliJ will ask for explicit resource package by
      // calling `getLightRClasses` at which point we can create the package. This is not completely
      // correct and the autocomplete will be slightly off when initial `R` is typed in the editor,
      // but this workaround is being used to mitigate issues (b/136685602) while resources
      // are re-worked.
      return allRClasses;
    } else {
      return rClasses.values();
    }
  }

  @Override
  public Collection<? extends PsiClass> getLightRClassesDefinedByModule(Module module) {
    BlazeRClass rClass = rClassesByModule.get(module);
    return rClass == null ? ImmutableSet.of() : ImmutableSet.of(rClass);
  }
}

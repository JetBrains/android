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
package com.google.idea.blaze.android.sync.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Keeps track of which resource modules correspond to which resource target.
 *
 * <p>Each {@link AndroidResourceModule} has one corresponding {@link Module} and contains all
 * resources of one unique resource package name.
 *
 * <p>Each {@link AndroidResourceModule} (and therefore {@link Module}) may contain resources of
 * multiple {@link TargetKey}. When multiple blaze targets share the same R package name, their
 * resources may be merged into one {@link AndroidResourceModule}. See {@link
 * BlazeAndroidWorkspaceImporter} for more details.
 */
public class AndroidResourceModuleRegistry {
  /**
   * Map between canonical targets and their corresponding {@link Module}.
   *
   * <p>This map is for accounting purposes between {@link Module} and the blaze targets they are
   * named after. Although one {@link Module} can contain resources contributed from multiple
   * targets, each resource containing {@link Module} is identified using one blaze target for
   * naming purposes.
   */
  private final BiMap<Module, TargetKey> moduleToTarget = HashBiMap.create();

  /**
   * Maps blaze targets to the {@link AndroidResourceModule} that it contributed resources to.
   *
   * <p>This maps tracks which blaze targets contributed to which {@link AndroidResourceModule}; the
   * resources of which are contained in the {@link Module} identified by {@link
   * AndroidResourceModule#targetKey}.
   */
  private final Map<TargetKey, AndroidResourceModule> targetToResourceModule = new HashMap<>();

  public static AndroidResourceModuleRegistry getInstance(Project project) {
    return project.getService(AndroidResourceModuleRegistry.class);
  }

  /**
   * Returns the given {@link Module}'s canonical blaze target's {@link TargetKey}. For a list of
   * targets that contributed to the given {@link Module}'s resources, see {@link
   * AndroidResourceModule#sourceTargetKeys} after obtaining it using {@link #get(Module)}. Returns
   * null if there's no target registered to the given {@link Module}, which also means the module
   * is not a resource containing module (e.g. workspace module).
   *
   * <p>The canonical target is the blaze target chosen to represent all other targets that generate
   * resource under the same resource package name. It is the main target of the given {@link
   * Module}'s corresponding {@link AndroidResourceModule}.
   */
  @Nullable
  public TargetKey getTargetKey(Module module) {
    return moduleToTarget.get(module);
  }

  /**
   * Returns the {@link AndroidResourceModule} registered for the given {@link Module}. Returns null
   * if there's no target registered to the given {@link Module}, which also means the module is not
   * a resource containing module (e.g. workspace module).
   */
  @Nullable
  public AndroidResourceModule get(Module module) {
    TargetKey target = moduleToTarget.get(module);
    return target == null ? null : targetToResourceModule.get(target);
  }

  /**
   * Returns the {@link Module} that holds resources of the given {@link TargetKey}. Returns null if
   * there is no such {@link Module}.
   *
   * <p>The returned {@link Module} may contain resources from all targets that share the same
   * resource package name as the target with the given {@link TargetKey}.
   */
  @Nullable
  public Module getModuleContainingResourcesOf(TargetKey target) {
    AndroidResourceModule resourceModule = targetToResourceModule.get(target);
    if (resourceModule == null) {
      return null;
    }
    return moduleToTarget.inverse().get(resourceModule.targetKey);
  }

  public void put(Module module, AndroidResourceModule resourceModule) {
    moduleToTarget.put(module, resourceModule.targetKey);

    // One resource module may contain resources from many targets with the same
    // resource package. Any one of those contributing targets should resolve to
    // the same AndroidResourceModule.
    for (TargetKey target : resourceModule.sourceTargetKeys) {
      targetToResourceModule.put(target, resourceModule);
    }
  }

  public void clear() {
    moduleToTarget.clear();
    targetToResourceModule.clear();
  }
}

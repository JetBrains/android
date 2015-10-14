/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.sync;

import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Encapsulates functionality of comparing {@link DataNode DataNodes} disregarding their state or
 * {@link DataNode#getChildren() children}/{@link DataNode#getParent() parent} differences.
 * <p/>
 * Example: a library dependency with <code>'exported'</code> set to <code>'true'</code> should match to a dependency of the same module
 * which points to the same library but has its <code>'exported'</code> flag set to <code>'false'</code>.
 *
 */
public class SyncEntityDataComparisonStrategy {

  private interface EqualityStrategy<T> {
    boolean isSameData(@NotNull T data1, @NotNull T data2, @NotNull Project project);
  }

  @NotNull private final Map<Key<?>, EqualityStrategy<?>> myStrategies = Maps.newHashMap();

  public SyncEntityDataComparisonStrategy() {
    myStrategies.put(ProjectKeys.PROJECT, new ProjectStrategy());
    myStrategies.put(ProjectKeys.MODULE, new ModuleStrategy());
    myStrategies.put(ProjectKeys.MODULE_DEPENDENCY, new ModuleDependencyStrategy());
  }

  @SuppressWarnings("unchecked")
  public boolean isSameNode(@NotNull DataNode<?> node1, @NotNull DataNode<?> node2, @NotNull Project project) {
    Key<?> key = node1.getKey();
    if (!key.equals(node2.getKey())) {
      return false;
    }
    EqualityStrategy strategy = myStrategies.get(key);
    if (strategy == null) {
      return node1.getData().equals(node2.getData());
    }
    return strategy.isSameData(node1.getData(), node2.getData(), project);
  }

  private static class ProjectStrategy implements EqualityStrategy<ProjectData> {
    @Override
    public boolean isSameData(@NotNull ProjectData data1, @NotNull ProjectData data2, @NotNull Project project) {
      return true;
    }
  }

  private static class ModuleStrategy implements EqualityStrategy<ModuleData> {
    @Override
    public boolean isSameData(@NotNull ModuleData data1, @NotNull ModuleData data2, @NotNull Project project) {
      IdeModelsProvider modelsProvider = new IdeModelsProviderImpl(project);
      Module ideModule = modelsProvider.findIdeModule(data1);
      if (ideModule == null) {
        return false;
      }
      return data2.getInternalName().equals(ideModule.getName());
    }
  }

  private class ModuleDependencyStrategy implements EqualityStrategy<ModuleDependencyData> {
    @SuppressWarnings("unchecked")
    @Override
    public boolean isSameData(@NotNull ModuleDependencyData data1, @NotNull ModuleDependencyData data2, @NotNull Project project) {
      EqualityStrategy<ModuleData> strategy = (EqualityStrategy<ModuleData>)myStrategies.get(ProjectKeys.MODULE);
      if (strategy == null) {
        assert false;
        return false;
      }
      if (strategy.isSameData(data1.getOwnerModule(), data2.getOwnerModule(), project)) {
        return false;
      }
      return strategy.isSameData(data1.getTarget(), data2.getTarget(), project);
    }
  }
}

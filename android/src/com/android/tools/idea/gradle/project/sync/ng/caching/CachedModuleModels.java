/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.caching;

import com.android.tools.idea.gradle.project.sync.ng.GradleModuleModels;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class CachedModuleModels implements GradleModuleModels {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 3L;

  @NotNull private final String myModuleName;
  @NotNull private final Map<Class<?>, List<Serializable>> myGradleModelsByType = new HashMap<>();

  CachedModuleModels(@NotNull Module module) {
    myModuleName = module.getName();
  }

  public void addModel(@NotNull Serializable model) {
    List<Serializable> models = myGradleModelsByType.computeIfAbsent(model.getClass(), k -> new ArrayList<>());
    models.add(model);
  }

  @Override
  @Nullable
  public <T> T findModel(@NotNull Class<T> modelType) {
    List<Serializable> models = myGradleModelsByType.get(modelType);
    if (models == null || models.isEmpty()) {
      return null;
    }
    assert models.size() == 1 : "More than one models available, please use findModels() instead.";
    Serializable model = models.get(0);
    if (modelType.isInstance(model)) {
      return modelType.cast(model);
    }
    return null;
  }

  @Nullable
  @Override
  public <T> List<T> findModels(@NotNull Class<T> modelType) {
    List<Serializable> models = myGradleModelsByType.get(modelType);
    if (models == null || models.isEmpty()) {
      return null;
    }
    return models.stream().filter(modelType::isInstance).map(modelType::cast).collect(Collectors.toList());
  }

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CachedModuleModels)) {
      return false;
    }
    CachedModuleModels that = (CachedModuleModels)o;
    return Objects.equals(myModuleName, that.myModuleName) &&
           Objects.equals(myGradleModelsByType, that.myGradleModelsByType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModuleName, myGradleModelsByType);
  }

  @Override
  public String toString() {
    return "GradleModuleModelsCache{" +
           "myModuleName='" + myModuleName + '\'' +
           ", myGradleModelsByType=" + myGradleModelsByType +
           '}';
  }
}
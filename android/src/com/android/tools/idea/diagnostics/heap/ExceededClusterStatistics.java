/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import com.google.common.collect.Maps;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class ExceededClusterStatistics {
  @NotNull
  final Map<String, ObjectsStatistics> nominatedClassesTotalStatistics = Maps.newHashMap();
  @NotNull
  final Object2IntMap<String> nominatedClassesEnumeration = new Object2IntOpenHashMap<>();
  @NotNull
  final Set<ClassLoader> nominatedClassLoaders = ContainerUtil.createWeakSet();

  final int exceededClusterIndex;
  public ExceededClusterStatistics(int exceededClusterIndex) {
    this.exceededClusterIndex = exceededClusterIndex;
  }

  public void addNominatedClass(@NotNull final String className, @NotNull final ObjectsStatistics objectsStatistics) {
    nominatedClassesEnumeration.putIfAbsent(className, nominatedClassesEnumeration.size());
    nominatedClassesTotalStatistics.putIfAbsent(className, objectsStatistics);
  }

  public void addNominatedClassLoader(@NotNull final ClassLoader classLoader) {
    nominatedClassLoaders.add(classLoader);
  }

  public boolean isClassLoaderNominated(@NotNull final ClassLoader loader) {
    return nominatedClassLoaders.contains(loader);
  }

  public boolean isClassNominated(@NotNull final String className) {
    return nominatedClassesTotalStatistics.containsKey(className);
  }
}

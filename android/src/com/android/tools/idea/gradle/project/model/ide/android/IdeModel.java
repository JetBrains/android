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
package com.android.tools.idea.gradle.project.model.ide.android;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

public abstract class IdeModel implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull
  protected <T> List<T> copy(@NotNull Collection<?> original, @NotNull ModelCache modelCache, @NotNull Function<T, T> mapper) {
    if (original.isEmpty()) {
      return Collections.emptyList();
    }
    List<T> copies = new ArrayList<>(original.size());
    for (Object item : original) {
      T copy = modelCache.computeIfAbsent(item, mapper);
      copies.add(copy);
    }
    return copies;
  }

  @NotNull
  protected <K, V> Map<K, V> copy(@NotNull Map<K, V> original, @NotNull ModelCache modelCache, @NotNull Function<V, V> mapper) {
    if (original.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<K, V> copies = new HashMap<>(original.size());
    original.forEach((k, v) -> {
      V copy = modelCache.computeIfAbsent(v, mapper);
      copies.put(k, copy);
    });
    return copies;
  }

  @Contract("!null -> !null")
  @Nullable
  protected Set<String> copy(@Nullable Set<String> original) {
    return original != null ? new HashSet<>(original) : null;
  }
}

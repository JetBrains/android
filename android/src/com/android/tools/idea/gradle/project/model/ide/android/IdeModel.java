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

  protected IdeModel(@NotNull Object original, @NotNull ModelCache modelCache) {
    Object copy = modelCache.computeIfAbsent(original, (Function<Object, Object>)recursiveModel1 -> this);
    if (copy != this) {
      throw new IllegalStateException("An existing copy was found in the cache");
    }
  }

  @NotNull
  protected static <K, V> List<K> copy(@NotNull Collection<K> original, @NotNull ModelCache modelCache, @NotNull Function<K, V> mapper) {
    if (original.isEmpty()) {
      return Collections.emptyList();
    }
    List<K> copies = new ArrayList<>(original.size());
    for (K item : original) {
      V copy = modelCache.computeIfAbsent(item, mapper);
      //noinspection unchecked
      copies.add((K)copy);
    }
    return copies;
  }

  @NotNull
  protected static <K, V> Map<K, V> copy(@NotNull Map<K, V> original, @NotNull ModelCache modelCache, @NotNull Function<V, V> mapper) {
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
  protected static Set<String> copy(@Nullable Set<String> original) {
    return original != null ? new HashSet<>(original) : null;
  }
}

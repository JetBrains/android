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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.Computable;
import org.gradle.tooling.model.UnsupportedMethodException;
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

  @Nullable
  protected static <K, V> V copyNewProperty(@NotNull ModelCache modelCache,
                                            @NotNull Computable<K> keyCreator,
                                            @NotNull Function<K, V> mapper,
                                            @Nullable V defaultValue) {
    try {
      K key = keyCreator.compute();
      return key != null ? modelCache.computeIfAbsent(key, mapper) : defaultValue;
    }
    catch (UnsupportedMethodException ignored) {
      return defaultValue;
    }
  }

  @Contract("_, !null -> !null")
  @Nullable
  protected static <T> T copyNewProperty(@NotNull Computable<T> propertyInvoker, @Nullable T defaultValue) {
    try {
      return propertyInvoker.compute();
    }
    catch (UnsupportedMethodException ignored) {
      return defaultValue;
    }
  }

  @NotNull
  protected static <K, V> List<K> copy(@NotNull Collection<K> original, @NotNull ModelCache modelCache, @NotNull Function<K, V> mapper) {
    if (original.isEmpty()) {
      return Collections.emptyList();
    }
    ImmutableList.Builder<K> copies = ImmutableList.builder();
    for (K item : original) {
      V copy = modelCache.computeIfAbsent(item, mapper);
      //noinspection unchecked
      copies.add((K)copy);
    }
    return copies.build();
  }

  @NotNull
  protected static <K, V> Map<K, V> copy(@NotNull Map<K, V> original, @NotNull ModelCache modelCache, @NotNull Function<V, V> mapper) {
    if (original.isEmpty()) {
      return Collections.emptyMap();
    }
    ImmutableMap.Builder<K, V> copies = ImmutableMap.builder();
    original.forEach((k, v) -> {
      V copy = modelCache.computeIfAbsent(v, mapper);
      copies.put(k, copy);
    });
    return copies.build();
  }

  @Contract("!null -> !null")
  @Nullable
  protected static Set<String> copy(@Nullable Set<String> original) {
    return original != null ? ImmutableSet.copyOf(original) : null;
  }
}

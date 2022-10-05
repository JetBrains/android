/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.isPrimitive;
import static com.google.wireless.android.sdk.stats.MemoryUsageReportEvent.MemoryUsageCollectionMetadata.StatusCode;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_FIELD_ARRAY;

import com.google.common.collect.Sets;
import com.intellij.util.Function;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FieldCache {

  private static final int MAX_ALLOWED_CACHE_SIZE = 1_000_000;

  @NotNull
  private final Map<Class<?>, Field[]> instanceFieldsCache;
  @NotNull
  private final HeapSnapshotStatistics statistics;
  private int cacheSize = 0;

  public FieldCache(@NotNull final HeapSnapshotStatistics statistics) {
    instanceFieldsCache = new Object2ObjectOpenCustomHashMap<>(CanonicalObjectStrategy.INSTANCE);
    this.statistics = statistics;
  }

  static final class CanonicalObjectStrategy<T> implements Hash.Strategy<T> {
    static final Hash.Strategy<Object> INSTANCE = new CanonicalObjectStrategy<>();

    @Override
    public int hashCode(@Nullable T o) {
      return Objects.hashCode(o);
    }

    @Override
    public boolean equals(@Nullable T a, @Nullable T b) {
      return Objects.equals(a, b);
    }
  }

  private Field[] getFieldsFromCacheOrUpdateCaches(@NotNull Class<?> aClass,
                                                   @NotNull final Map<Class<?>, Field[]> cache,
                                                   Function<Class<?>, List<Field>> getFields)
    throws HeapSnapshotTraverseException {
    Field[] cached = cache.get(aClass);

    if (cached != null) {
      return cached;
    }

    try {
      List<Field> declaredFields = getFields.fun(aClass);
      Set<Field> fields = Sets.newHashSet();

      for (Field declaredField : declaredFields) {
        declaredField.setAccessible(true);
        Class<?> type = declaredField.getType();
        if (isPrimitive(type)) continue; // unable to hold references, skip
        fields.add(declaredField);
      }

      Class<?> superclass = aClass.getSuperclass();
      if (superclass != null) {
        fields.addAll(
          Arrays.asList(getFieldsFromCacheOrUpdateCaches(superclass, cache, getFields)));
      }

      cache.put(aClass, fields.isEmpty()
                        ? EMPTY_FIELD_ARRAY
                        : fields.toArray(new Field[0]));
      cacheSize += fields.size();
      statistics.updateMaxFieldsCacheSize(cacheSize);
      if (cacheSize > MAX_ALLOWED_CACHE_SIZE) {
        throw new HeapSnapshotTraverseException(StatusCode.CLASS_FIELDS_CACHE_IS_TOO_BIG);
      }
    }
    catch (IncompatibleClassChangeError | NoClassDefFoundError | SecurityException |
           InaccessibleObjectException e) {
      // this exception may be thrown because there are two different versions of
      // org.objectweb.asm.tree.ClassNode from different plugins.
      cache.put(aClass, EMPTY_FIELD_ARRAY);
    }
    return cache.get(aClass);
  }

  public Field[] getInstanceFields(@NotNull Class<?> aClass) throws HeapSnapshotTraverseException {
    return getFieldsFromCacheOrUpdateCaches(aClass, instanceFieldsCache, c ->
      Arrays.stream(c.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).collect(Collectors.toList())
    );
  }

  public Object[] getStaticFields(@NotNull Class<?> aClass) {
    return HeapSnapshotTraverse.getClassStaticFieldsValues(aClass);
  }
}

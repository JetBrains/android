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
import static org.apache.commons.lang3.ArrayUtils.EMPTY_FIELD_ARRAY;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FieldCache {
  @NotNull
  private final Map<Class<?>, Field[]> myStaticFieldsCache;
  @NotNull
  private final Map<Class<?>, Field[]> myInstanceFieldsCache;

  public FieldCache() {
    myStaticFieldsCache = new Object2ObjectOpenCustomHashMap<>(CanonicalObjectStrategy.INSTANCE);
    myInstanceFieldsCache = new Object2ObjectOpenCustomHashMap<>(CanonicalObjectStrategy.INSTANCE);
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

  private Field[] getFieldsFromCacheOrUpdateCaches(@NotNull Class<?> aClass, @NotNull final Map<Class<?>, Field[]> cache) {
    Field[] cached = cache.get(aClass);

    if (cached != null) {
      return cached;
    }

    try {
      Field[] declaredFields = aClass.getDeclaredFields();
      Set<Field> instanceFields = new HashSet<>();
      Set<Field> staticFields = new HashSet<>();

      for (Field declaredField : declaredFields) {
        declaredField.setAccessible(true);
        Class<?> type = declaredField.getType();
        if (isPrimitive(type)) continue; // unable to hold references, skip
        if ((declaredField.getModifiers() & Modifier.STATIC) != 0) {
          staticFields.add(declaredField);
        } else {
          instanceFields.add(declaredField);
        }
      }

      Class<?> superclass = aClass.getSuperclass();
      if (superclass != null) {
        instanceFields.addAll(Arrays.asList(getFieldsFromCacheOrUpdateCaches(superclass, myInstanceFieldsCache)));
        staticFields.addAll(Arrays.asList(getFieldsFromCacheOrUpdateCaches(superclass, myStaticFieldsCache)));
      }

      myInstanceFieldsCache.put(aClass, instanceFields.isEmpty() ? EMPTY_FIELD_ARRAY : instanceFields.toArray(new Field[0]));
      myStaticFieldsCache.put(aClass, staticFields.isEmpty() ? EMPTY_FIELD_ARRAY : staticFields.toArray(new Field[0]));
    }
    catch (IncompatibleClassChangeError | NoClassDefFoundError | SecurityException | InaccessibleObjectException e) {
      // this exception may be thrown because there are two different versions of org.objectweb.asm.tree.ClassNode from different plugins
      myInstanceFieldsCache.put(aClass, EMPTY_FIELD_ARRAY);
      myStaticFieldsCache.put(aClass, EMPTY_FIELD_ARRAY);
    }
    return cache.get(aClass);
  }

  public Field[] getInstanceFields(@NotNull Class<?> aClass) {
    return getFieldsFromCacheOrUpdateCaches(aClass, myInstanceFieldsCache);
  }

  public Field[] getStaticFields(@NotNull Class<?> aClass) {
    return getFieldsFromCacheOrUpdateCaches(aClass, myStaticFieldsCache);
  }
}

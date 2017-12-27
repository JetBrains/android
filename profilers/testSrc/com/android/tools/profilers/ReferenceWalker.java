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
package com.android.tools.profilers;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

/**
 * An utility class that can used to test reachability of objects from an object by following hard links.
 */
public class ReferenceWalker {
  @NotNull private final Set<Object> myReachable;
  @NotNull private final Map<Object, Object> myParentObject;
  @NotNull private final Object myRoot;

  public ReferenceWalker(@NotNull Object object) throws IllegalAccessException {
    myRoot = object;
    myReachable = Sets.newIdentityHashSet();
    myParentObject = Maps.newIdentityHashMap();
    collectReachable(myRoot, null);
  }

  /**
   * Ensured that no object which satisfied the {@code predicate} callback is reachable from {@link #myRoot}.
   */
  private void assertNotReachable(@NotNull Predicate<Object> predicate) {
    Object object = findReachable(predicate);
    if (object == null) {
      return;
    }

    StringBuilder error = new StringBuilder("Found invalid object:\n");
    error.append(" > \"").append(formatObject(object)).append("\n");
    error.append(" Reference path:\n");

    Object previous = myParentObject.get(object);
    while (previous != null) {
      error.append(" > \"").append(formatObject(previous)).append("\n");
      previous = myParentObject.get(previous);
    }
    Assert.fail(error.toString());
  }

  /**
   * Ensured that {@code objects} are not reachable from {@link #myRoot}.
   */
  public void assertNotReachable(@NotNull Object... objects) throws IllegalAccessException {
    Set<Object> invalid = Sets.newIdentityHashSet();
    invalid.addAll(Arrays.asList(objects));
    assertNotReachable(invalid::contains);
  }

  /**
   * Ensured that classes {@code classes} are not reachable from {@link #myRoot}.
   */
  public void assertNotReachable(@NotNull Class... classes) throws IllegalAccessException {
    Set<Class> invalidClasses = new HashSet<>(Arrays.asList(classes));
    assertNotReachable(o -> invalidClasses.contains(o.getClass()));
  }

  /**
   * Ensured that classes {@code classes } are reachable from {@link #myRoot}.
   */
  public void assertReachable(@NotNull Class... classes) throws IllegalAccessException {
    for (Class clazz: classes) {
      if (findReachable(o -> o.getClass().equals(clazz)) == null) {
        String error = String.format("The class %s is not reachable from %s", clazz.getCanonicalName(), formatObject(myRoot));
        Assert.fail(error);
      }
    }
  }

  @Nullable
  private Object findReachable(@NotNull Predicate<Object> predicate) {
    for (Object object : myReachable) {
      if (predicate.test(object)) {
        return object;
      }
    }
    return null;
  }

  /**
   * Collects all the objects reachable from "object" by following hard links. This method doesn't dive in if it finds
   * objects within java.lang or io.grpc.
   */
  private void collectReachable(@Nullable Object object, @Nullable Object parent)
    throws IllegalAccessException {
    if (object == null || object.getClass().equals(WeakReference.class) || object.getClass().equals(WeakHashMap.class)) {
      return;
    }
    String name = object.getClass().getCanonicalName();
    name = name == null ? "" : name;

    if (!object.getClass().isArray() && (name.startsWith("java.lang") || name.startsWith("io.grpc"))) {
      return;
    }

    if (!myReachable.add(object)) {
      return;
    }
    myParentObject.put(object, parent);

    if (object.getClass().isArray()) {
      for (int i = 0; i < Array.getLength(object); i++) {
        Object value = Array.get(object, i);
        collectReachable(value, object);
      }
    }
    else {
      ArrayList<Field> fields = new ArrayList<>();
      collectInheritedDeclaredFields(object.getClass(), fields);
      for (Field field : fields) {
        if (!field.getType().isPrimitive()) {
          field.setAccessible(true);
          Object value = field.get(object);
          collectReachable(value, object);
        }
      }
    }
  }

  private static void collectInheritedDeclaredFields(Class<?> clazz, ArrayList<Field> fields) {
    Collections.addAll(fields, clazz.getDeclaredFields());
    if (clazz.getSuperclass() != null) {
      collectInheritedDeclaredFields(clazz.getSuperclass(), fields);
    }
  }

  private static String formatObject(Object object) {
    return object + "\" :: " + object.getClass().getName();
  }
}

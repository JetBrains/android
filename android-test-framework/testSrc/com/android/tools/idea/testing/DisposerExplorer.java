/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testing;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Collection of methods for accessing {@link Disposer}'s tree.
 */
@TestOnly
public class DisposerExplorer {
  private static final Map<Class<?>, Map<String, Field>> fieldCache = new HashMap<>();
  /** For simplicity supports only non-overloaded methods. */
  private static final Map<Class<?>, Map<String, Method>> methodCache = new HashMap<>();
  @SuppressWarnings("UnstableApiUsage")
  private static final Object tree = Disposer.getTree();
  private static final Map<Disposable, Object> object2ParentNode = getFieldValue(tree, "myObject2ParentNode");
  private static final Object rootNode = getFieldValue(tree, "myRootNode");
  private static final Object treeLock = rootNode;

  /**
   * Checks if the given disposable is referenced by the Disposer's tree.
   */
  public static boolean isContainedInTree(@NotNull Disposable disposable) {
    boolean[] found = new boolean[1];
    visitTree(d -> {
      if (d == disposable) {
        found[0] = true;
      }
      return found[0] ? VisitResult.ABORT : VisitResult.CONTINUE;
    });
    return found[0];
  }

  /**
   * Returns the roots of the Disposer's tree. The order of the returned roots is not guaranteed.
   */
  public static @NotNull Collection<Disposable> getTreeRoots() {
    synchronized (treeLock) {
      return getTreeRootsInternal();
    }
  }

  /**
   * The same as {@link #getTreeRoots()} but without locking. Must be called while holding the disposer tree lock.
   */
  private static @NotNull Collection<Disposable> getTreeRootsInternal() {
    return getObjectNodeDisposableChildren(rootNode);
  }

  /**
   * Returns immediate children of the given disposable.
   */
  public static @NotNull List<Disposable> getChildren(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      if (objectNode == null) {
        return ImmutableList.of();
      }
      return getObjectNodeDisposableChildren(objectNode);
    }
  }

  private static @NotNull ImmutableList<Disposable> getObjectNodeDisposableChildren(@NotNull Object objectNode) {
    Collection<?> childNodes = getObjectNodeChildren(objectNode);
    if (childNodes.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Disposable> builder = ImmutableList.builderWithExpectedSize(childNodes.size());
    for (Object node : childNodes) {
      builder.add(getObjectNodeDisposable(node));
    }
    return builder.build();
  }

  /**
   * Returns the parent of the given disposable, or null if the disposable doesn't have parent.
   */
  public static @Nullable Disposable getParent(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      if (objectNode != null) {
        Object parentNode = getObjectNodeParent(objectNode);
        if (parentNode != null) {
          return getObjectNodeDisposable(parentNode);
        }
      }
      return null;
    }
  }

  /**
   * Checks if the given disposable has children.
   */
  static boolean hasChildren(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      return objectNode != null && !getObjectNodeChildren(objectNode).isEmpty();
    }
  }

  /**
   * Returns the stack trace from when the disposable was first registered, if Disposer.isDebugMode()
   * is true and the given disposable is a root. Otherwise returns null.
   */
  public static @Nullable Throwable getTrace(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      return objectNode != null ? getObjectNodeTrace(objectNode) : null;
    }
  }

  /**
   * Returns all objects in the tree that satisfy the given filter.
   *
   * @param filter the predicate determining what objects are returned
   * @return the objects, for which the {@code filter.test} method returned true
   */
  public static @NotNull List<Disposable> findAll(@NotNull Predicate<Disposable> filter) {
    List<Disposable> result = new ArrayList<>();
    visitTree(disposable -> {
      if (filter.test(disposable)) {
        result.add(disposable);
      }
      return VisitResult.CONTINUE;
    });
    return result;
  }

  /**
   * Returns the first encountered object in the tree that satisfies the given filter.
   *
   * @param filter the predicate determining whether an object should be returned or not
   * @return the first object, for which the {@code filter.test} method returned true, or null if there are no such objects
   */
  public static @Nullable Disposable findFirst(@NotNull Predicate<Disposable> filter) {
    Disposable[] result = new Disposable[1];
    visitTree(disposable -> {
      if (filter.test(disposable)) {
        result[0] = disposable;
        return VisitResult.ABORT;
      }
      return VisitResult.CONTINUE;
    });
    return result[0];
  }

  /**
   * Calls the {@link Visitor#visit(Disposable)} method for every disposable in the tree unless
   * some parts of the tree are skipped due to the {@link Visitor#visit(Disposable)} method
   * returning something other than {@link VisitResult#CONTINUE}.
   *
   * @param visitor the visitor object
   * @return VisitResult.ABORT if the visiting was terminated the {@link Visitor#visit(Disposable)} method
   *     returning VisitResult.ABORT, otherwise VisitResult.CONTINUE
   */
  public static @NotNull VisitResult visitTree(@NotNull Visitor visitor) {
    synchronized (treeLock) {
      return visitDescendantsOfNode(getFieldValue(tree, "myRootNode"), visitor);
    }
  }

  /**
   * Calls the {@link Visitor#visit(Disposable)} method for every disposable that is a direct or
   * indirect descendant of the given disposable. Some parts of the descendants' tree of can be
   * skipped if the {@link Visitor#visit(Disposable)} method returns something other than
   * {@link VisitResult#CONTINUE}.
   *
   * @param disposable the disposable to vising the descendants of
   * @param visitor the visitor object
   * @return VisitResult.ABORT if the visiting was terminated the {@link Visitor#visit(Disposable)} method
   *     returning VisitResult.ABORT, otherwise VisitResult.CONTINUE
   */
  public static @NotNull VisitResult visitDescendants(@NotNull Disposable disposable, @NotNull Visitor visitor) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      if (objectNode != null) {
        return visitDescendantsOfNode(objectNode, visitor);
      }
      return VisitResult.CONTINUE;
    }
  }

  private static @NotNull VisitResult visitSubtree(@NotNull Object objectNode, @NotNull Visitor visitor) {
    VisitResult result = visitor.visit(getObjectNodeDisposable(objectNode));
    if (result == VisitResult.CONTINUE) {
      result = visitDescendantsOfNode(objectNode, visitor);
      if (result == VisitResult.ABORT) {
        return result;
      }
    }
    return result == VisitResult.SKIP_CHILDREN ? VisitResult.CONTINUE : result;
  }

  private static @NotNull VisitResult visitDescendantsOfNode(@NotNull Object objectNode, @NotNull Visitor visitor) {
    for (Object child : getObjectNodeChildren(objectNode)) {
      VisitResult result = visitSubtree(child, visitor);
      if (result == VisitResult.ABORT) {
        return result;
      }
    }
    return VisitResult.CONTINUE;
  }

  private static @Nullable Object getObjectNode(@NotNull Disposable disposable) {
    Object parent = object2ParentNode.get(disposable);
    if (parent == null) {
      parent = rootNode;
    }
    return findChildNode(parent, disposable);
  }

  private static @Nullable Object findChildNode(Object objectNode, Disposable disposable) {
    Object childrenObject = getFieldValue(objectNode, "myChildren");
    if (childrenObject == null) {
      return null;
    }
    Method method = getMethod(childrenObject.getClass(), "findChildNode", Disposable.class);
    return invokeMethod(childrenObject, method, disposable);
  }

  private static @Nullable Object getObjectNodeParent(@NotNull Object objectNode) {
    return object2ParentNode.get(getObjectNodeDisposable(objectNode));
  }

  private static @NotNull Collection<?> getObjectNodeChildren(@NotNull Object objectNode) {
    Object childrenObject = getFieldValue(objectNode, "myChildren");
    if (childrenObject == null) {
      return ImmutableList.of();
    }
    Method method = getMethod(childrenObject.getClass(), "getAllNodes");
    return invokeMethod(childrenObject, method);
  }

  private static @NotNull Disposable getObjectNodeDisposable(@NotNull Object objectNode) {
    return getFieldValue(objectNode, "myObject");
  }

  private static @Nullable Throwable getObjectNodeTrace(@NotNull Object objectNode) {
    return getFieldValue(objectNode, "myTrace");
  }

  @SuppressWarnings("unchecked")
  private static <T> T getFieldValue(@NotNull Object object, @NotNull String fieldName) {
    Field field = getField(object.getClass(), fieldName);
    try {
      return (T)field.get(object);
    }
    catch (IllegalAccessException e) {
      throw new Error(e);
    }
  }

  private static @NotNull Field getField(Class<?> clazz, @NotNull String fieldName) {
    Map<String, Field> fieldsByName = fieldCache.computeIfAbsent(clazz, cls -> new HashMap<>());
    return fieldsByName.computeIfAbsent(fieldName, name -> {
      try {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
      }
      catch (NoSuchFieldException e) {
        throw new Error(e);
      }
    });
  }

  @SuppressWarnings("unchecked")
  private static <T> T invokeMethod(@NotNull Object object, @NotNull Method method, @Nullable Object... args) {
    try {
      return (T)method.invoke(object, args);
    }
    catch (ReflectiveOperationException e) {
      throw new Error(e);
    }
  }

  /**
   * For simplicity supports only non-overloaded methods.
   */
  private static @NotNull Method getMethod(Class<?> clazz, @NotNull String methodName, @NotNull Class<?>... parameterTypes) {
    Map<String, Method> methodsByName = methodCache.computeIfAbsent(clazz, cls -> new HashMap<>());
    return methodsByName.computeIfAbsent(methodName, name -> {
      try {
        Method method= clazz.getMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
      }
      catch (NoSuchMethodException e) {
        throw new Error(e);
      }
    });
  }

  /** Do not instantiate. All methods are static. */
  private DisposerExplorer() {}

  public enum VisitResult {
    CONTINUE,
    SKIP_CHILDREN,
    ABORT
  }

  public interface Visitor {
    /**
     * Called for every visited disposable.
     *
     * @param disposable the disposable being visited
     * @return VisitResult.CONTINUE to continue visiting other disposables, VisitResult.SKIP_CHILDREN
     *     to continue visit from the next sibling of the currently visited disposable, or
     *     VisitResult.ABORT to not visit any more disposables.
     */
    @NotNull VisitResult visit(@NotNull Disposable disposable);
  }
}

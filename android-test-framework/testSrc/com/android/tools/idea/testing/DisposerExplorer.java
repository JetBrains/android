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
import java.util.ArrayList;
import java.util.Collection;
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
  private static final Object treeLock = getTreeLock();

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
  @NotNull
  public static Collection<Disposable> getTreeRoots() {
    synchronized (treeLock) {
      return getTreeRootsInternal();
    }
  }

  /**
   * The same as {@link #getTreeRoots()} but without locking. Must be called while holding the disposer tree lock.
   */
  @NotNull
  private static Collection<Disposable> getTreeRootsInternal() {
    return getObjectNodeDisposableChildren(getRootNode());
  }

  @NotNull
  private static Object getRootNode() {
    return getFieldValue(Disposer.getTree(), "myRootNode");
  }

  @NotNull
  private static Object getTreeLock() {
    return getFieldValue(Disposer.getTree(), "treeLock");
  }

  /**
   * Returns immediate children of the given disposable.
   */
  @NotNull
  public static List<Disposable> getChildren(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      if (objectNode == null) {
        return ImmutableList.of();
      }
      return getObjectNodeDisposableChildren(objectNode);
    }
  }

  @NotNull
  private static ImmutableList<Disposable> getObjectNodeDisposableChildren(@NotNull Object objectNode) {
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
  @Nullable
  public static Disposable getParent(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      Object parentNode = getParentNode(disposable);
      if (parentNode == null) {
        return null;
      }
      return getObjectNodeDisposable(parentNode);
    }
  }

  /**
   * Checks if the given disposable has children.
   */
  public static boolean hasChildren(@NotNull Disposable disposable) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      return objectNode != null && !getObjectNodeChildren(objectNode).isEmpty();
    }
  }

  /**
   * If Disposer.isDebugMode() is true and the given disposable is a root, then this returns
   * the trace from when the disposable was first registered. Otherwise it returns null.
   */
  @Nullable
  public static Throwable getTrace(@NotNull Disposable disposable) {
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
  @NotNull
  public static List<Disposable> findAll(@NotNull Predicate<Disposable> filter) {
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
  @Nullable
  public static Disposable findFirst(@NotNull Predicate<Disposable> filter) {
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
  @NotNull
  public static VisitResult visitTree(@NotNull Visitor visitor) {
    synchronized (treeLock) {
      return visitNodeDescendants(getRootNode(), visitor);
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
  @NotNull
  public static VisitResult visitDescendants(@NotNull Disposable disposable, @NotNull Visitor visitor) {
    synchronized (treeLock) {
      Object objectNode = getObjectNode(disposable);
      if (objectNode != null) {
        return visitNodeDescendants(objectNode, visitor);
      }
      return VisitResult.CONTINUE;
    }
  }

  @NotNull
  private static VisitResult visitSubtree(@NotNull Object objectNode, @NotNull Visitor visitor) {
    VisitResult result = visitor.visit(getObjectNodeDisposable(objectNode));
    if (result == VisitResult.CONTINUE) {
      result = visitNodeDescendants(objectNode, visitor);
      if (result == VisitResult.ABORT) {
        return result;
      }
    }
    return result == VisitResult.SKIP_CHILDREN ? VisitResult.CONTINUE : result;
  }

  @NotNull
  private static VisitResult visitNodeDescendants(@NotNull Object objectNode, @NotNull Visitor visitor) {
    for (Object child : getObjectNodeChildren(objectNode)) {
      VisitResult result = visitSubtree(child, visitor);
      if (result == VisitResult.ABORT) {
        return result;
      }
    }
    return VisitResult.CONTINUE;
  }

  @NotNull
  private static Map<Disposable, ?> getObject2ParentNodeMap() {
    return getFieldValue(Disposer.getTree(), "myObject2ParentNode");
  }

  @Nullable
  private static Object getObjectNode(@NotNull Disposable disposable) {
    Object parentNode = getParentNode(disposable);
    for (Object node : getObjectNodeChildren(parentNode)) {
      if (getObjectNodeDisposable(node) == disposable) {
        return node;
      }
    }
    return null;
  }

  @NotNull
  private static Object getParentNode(@NotNull Disposable disposable) {
    Object parentNode = getObject2ParentNodeMap().get(disposable);
    return parentNode == null ? getRootNode() : parentNode;
  }


  @NotNull
  private static List<?> getObjectNodeChildren(@NotNull Object objectNode) {
    Object childNodes = getFieldValue(objectNode, "myChildren");
    return childNodes == null ? ImmutableList.of() : (List<?>)childNodes;
  }

  @Nullable
  private static Disposable getObjectNodeDisposable(@NotNull Object objectNode) {
    if (objectNode == getRootNode()) {
      return null;
    }
    return getFieldValue(objectNode, "myObject");
  }

  @Nullable
  private static Throwable getObjectNodeTrace(@NotNull Object objectNode) {
    return getFieldValue(objectNode, "myTrace");
  }

  // TODO: Replace reflection by a test-only class in the same package as Disposer.
  @SuppressWarnings("unchecked")
  @Nullable
  private static <T> T getFieldValue(@NotNull Object object, @NotNull String fieldName) {
    // If performance becomes an issue, we can cache Field objects.
    try {
      Field field = object.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return (T)field.get(object);
    }
    catch (ReflectiveOperationException e) {
      throw new Error(e); // Should not happen unless there is a bug in this class.
    }
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
    @NotNull
    VisitResult visit(@NotNull Disposable disposable);
  }
}

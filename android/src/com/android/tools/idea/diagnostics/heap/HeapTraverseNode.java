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

import com.android.annotations.Nullable;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.NotNull;

class HeapTraverseNode {

  @NotNull
  private final WeakReference<?> weakReference;
  public RefWeight ownershipWeight;
  public long ownedByComponentMask;
  public long retainedMask;
  // Retained mask that works in a component categories plane (for comparison: retainedMask works in
  // a sub-category plane)
  public int retainedMaskForCategories;
  public final long tag;

  HeapTraverseNode(@Nullable final Object obj,
                   @NotNull RefWeight ownershipWeight,
                   long ownedByComponentMask,
                   long retainedMask,
                   int retainedMaskForCategories,
                   long tag) {
    weakReference = new WeakReference<>(obj);
    this.ownershipWeight = ownershipWeight;
    this.ownedByComponentMask = ownedByComponentMask;
    this.retainedMask = retainedMask;
    this.retainedMaskForCategories = retainedMaskForCategories;
    this.tag = tag;
  }

  HeapTraverseNode(@Nullable final Object obj,
                   int ownershipWeightIntValue,
                   long ownedByComponentMask,
                   long retainedMask,
                   int retainedMaskForCategories,
                   long tag) {
    this(obj, refWeightFromInt(ownershipWeightIntValue), ownedByComponentMask, retainedMask, retainedMaskForCategories, tag);
  }

  @Nullable
  Object getObject() {
    return weakReference.get();
  }

  public enum RefWeight {
    DEFAULT(0),
    // Weight that is assigned to reference from objects not owned by any components to child object
    NON_COMPONENT(1),
    // Weight that is assigned to reference from synthetic objects to child objects
    SYNTHETIC(2),
    // Weight that is assigned to reference from array object to elements
    ARRAY_ELEMENT(3),
    // Weight that is assigned to reference from object to instance fields objects
    INSTANCE_FIELD(4),
    // Weight that is assigned to reference from class object to static fields objects
    STATIC_FIELD(5),
    // Weight that is assigned to reference from Disposable object to it's DisposerTree child
    // Disposables
    DISPOSER_TREE_REFERENCE(6);

    private final int value;
    private RefWeight(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  private static RefWeight refWeightFromInt(int refWeightInt) {
    switch(refWeightInt) {
      case 1:
        return RefWeight.NON_COMPONENT;
      case 2:
        return RefWeight.SYNTHETIC;
      case 3:
        return RefWeight.ARRAY_ELEMENT;
      case 4:
        return RefWeight.INSTANCE_FIELD;
      case 5:
        return RefWeight.STATIC_FIELD;
      case 6:
        return RefWeight.DISPOSER_TREE_REFERENCE;
      default:
        return RefWeight.DEFAULT;
    }
  }

  /**
   *  This method caches the <a href="https://docs.oracle.com/javase/7/docs/platform/jvmti/jvmti.html#jmethodID">MethodId</a> of the
   *  {@link HeapTraverseNode} constructor for future use. This caching allows to avoid the repeated method resolution and JVM method table
   *  requests.
   */
  static native void cacheHeapSnapshotTraverseNodeConstructorId(Class<?> heapTraverseNodeClass);

  /**
   * Clears the object id to {@link HeapTraverseNode} native map.
   */
  static native void clearObjectIdToTraverseNodeMap();

  /**
   * Adds a new node to the native map initialized with the passed Object, reference weight, masks and tag if the passed id was not yet
   * added to the native map. Otherwise, updates the existing element.
   */
  static native void putOrUpdateObjectIdToTraverseNodeMap(int id,
                                                          @NotNull final Object obj,
                                                          int refWeight,
                                                          long ownedByComponentMask,
                                                          long retainedMask,
                                                          int retainedMaskForCategories,
                                                          long tag);

  /**
   * @return the size of the native id to {@link HeapTraverseNode} map.
   */
  static native int getObjectIdToTraverseNodeMapSize();

  /**
   * Removes the element from the native object id to {@link HeapTraverseNode} map.
   */
  static native void removeElementFromObjectIdToTraverseNodeMap(int id);

  /**
   * Return element from the native {@link HeapTraverseNode} map.
   */
  static native HeapTraverseNode getObjectIdToTraverseNodeMapElement(int id, Class<?> heapTraverseNodeClass);
}

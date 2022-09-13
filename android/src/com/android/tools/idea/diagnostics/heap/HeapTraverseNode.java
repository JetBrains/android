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
  public RefWeight ownershipWeight = RefWeight.DEFAULT;
  public long ownedByComponentMask = 0;
  public long retainedMask = 0;
  // Retained mask that works in a component categories plane (for comparison: retainedMask works in
  // a sub-category plane)
  public int retainedMaskForCategories = 0;

  HeapTraverseNode(@NotNull final Object obj) {
    weakReference = new WeakReference<>(obj);
  }

  @Nullable
  Object getObject() {
    return weakReference.get();
  }

  public enum RefWeight {
    DEFAULT,
    // Weight that is assigned to reference from objects not owned by any components to child object
    NON_COMPONENT,
    // Weight that is assigned to reference from synthetic objects to child objects
    SYNTHETIC,
    // Weight that is assigned to reference from array object to elements
    ARRAY_ELEMENT,
    // Weight that is assigned to reference from object to instance fields objects
    INSTANCE_FIELD,
    // Weight that is assigned to reference from class object to static fields objects
    STATIC_FIELD,
    // Weight that is assigned to reference from Disposable object to it's DisposerTree child
    // Disposables
    DISPOSER_TREE_REFERENCE
  }
}

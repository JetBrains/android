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
  public RefWeight myOwnershipWeight = RefWeight.DEFAULT;
  public int myOwnedByComponentMask = 0;
  public int myRetainedMask = 0;
  @NotNull
  private final WeakReference<?> myWeakReference;

  HeapTraverseNode(@NotNull final Object obj) {
    myWeakReference = new WeakReference<>(obj);
  }

  @Nullable
  Object getObject() {
    return myWeakReference.get();
  }

  public enum RefWeight {
    DEFAULT,
    // Weight that is assigned to reference from objects not owned by any components to child objects
    NON_COMPONENT,
    // Weight that is assigned to reference from synthetic objects to child objects
    SYNTHETIC,
    // Weight that is assigned to reference from array object to elements
    ARRAY_ELEMENT,
    // Weight that is assigned to reference from object to instance fields objects
    INSTANCE_FIELD,
    // Weight that is assigned to reference from class object to static fields objects
    STATIC_FIELD,
    // Weight that is assigned to reference from Disposable object to it's DisposerTree child Disposables
    DISPOSER_TREE_REFERENCE
  }
}
